package com.palisade.plugins

import com.palisade.config.ConfigManager
import com.palisade.config.RateLimitConfig
import com.palisade.ratelimit.RateLimitStoreFactory
import com.palisade.utils.EndApplicationCall
import com.palisade.utils.ForwardedAddressParser
import com.palisade.verifiers.HeaderVerifier
import com.palisade.verifiers.MethodVerifier
import com.palisade.verifiers.RequestComponentVerifier
import com.palisade.verifiers.VerificationResult
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import io.micrometer.core.instrument.Counter

class TrafficGatekeeperConfig {
    var methodVerifiers: Array<MethodVerifier> = emptyArray()
    var componentVerifiers: Array<RequestComponentVerifier> = emptyArray()
    var headerVerifiers: Array<HeaderVerifier> = emptyArray()

    var maxHeaderCountProvider: () -> Int = { ConfigManager.current.verifierParams.maxHeaderCount }
    var rateLimitConfigProvider: () -> RateLimitConfig = { ConfigManager.current.rateLimit }
}

@Suppress("NOTHING_TO_INLINE")
suspend inline fun handleVerificationResult(
    pipelineContext: PipelineContext<Unit, ApplicationCall>,
    call: ApplicationCall,
    result: VerificationResult,
) {
    if (result is VerificationResult.Denied) {
        EndApplicationCall.dropApplicationCall(pipelineContext, call, result.status, result.message)
        pipelineContext.finish()
    }
}

val tooManyHeadersCounter =
    Counter.builder("gateway_blocked_requests_total")
        .description("The total number of malicious requests dropped by the API gateway guards")
        .tag("reason", "too_many_headers")
        .register(AppMetricsRegistry)

val rateLimitCounter =
    Counter.builder("gateway_blocked_requests_total")
        .description("The total number of requests dropped by the API gateway guards")
        .tag("reason", "rate_limit_exceeded")
        .register(AppMetricsRegistry)

val TrafficGatekeeper =
    createApplicationPlugin(name = "TrafficGatekeeper", ::TrafficGatekeeperConfig) {

        val methodVerifiers = pluginConfig.methodVerifiers
        val componentVerifiers = pluginConfig.componentVerifiers
        val headerVerifiers = pluginConfig.headerVerifiers
        val maxHeaderCountProvider = pluginConfig.maxHeaderCountProvider
        val rateLimitConfigProvider = pluginConfig.rateLimitConfigProvider

        application.intercept(ApplicationCallPipeline.Setup) {
            @Suppress("UNCHECKED_CAST")
            val pipelineContext = this as PipelineContext<Unit, ApplicationCall>
            val rawUri = call.request.uri

            // Administrative paths are excluded
            if (rawUri.startsWith("/_palisade")) {
                return@intercept
            }

            val rateLimitEnabled = rateLimitConfigProvider().enabled
            if (rateLimitEnabled) {
                val clientIp = ForwardedAddressParser.extractClientIp(call)

                val currentRateLimitConfig = ConfigManager.current.rateLimit
                val store = RateLimitStoreFactory.getStore(currentRateLimitConfig)

                val burst = rateLimitConfigProvider().maxBurstTokens
                val refill = rateLimitConfigProvider().refillRatePerSecond

                if (!store.tryConsume(clientIp, burst, refill)) {
                    rateLimitCounter.increment()
                    EndApplicationCall.dropApplicationCall(
                        pipelineContext,
                        call,
                        HttpStatusCode.TooManyRequests,
                        "Too many requests. Rate limit exceeded.",
                    )
                    pipelineContext.finish()
                    return@intercept
                }
            }

            val headerNamesSet = call.request.headers.names()
            val headerCount = headerNamesSet.size
            val maxHeadersAllowed = maxHeaderCountProvider()

            if (maxHeadersAllowed > 0 && headerCount > maxHeadersAllowed) {
                tooManyHeadersCounter.increment()
                EndApplicationCall.dropApplicationCall(
                    pipelineContext,
                    call,
                    HttpStatusCode.BadRequest,
                    "Number of '$headerCount' headers exceed the limit of $maxHeadersAllowed allowable headers",
                )
                pipelineContext.finish()
                return@intercept
            }

            val httpMethodName = call.request.httpMethod.value.uppercase()
            for (i in 0 until methodVerifiers.size) {
                handleVerificationResult(
                    pipelineContext,
                    call,
                    methodVerifiers[i].verify(httpMethodName),
                )
            }

            val queryStart = rawUri.indexOf('?')
            val routePath = if (queryStart == -1) rawUri else rawUri.substring(0, queryStart)
            for (i in 0 until componentVerifiers.size) {
                handleVerificationResult(
                    pipelineContext,
                    call,
                    componentVerifiers[i].verify(rawUri, routePath),
                )
            }

            val headerIterator = headerNamesSet.iterator()

            while (headerIterator.hasNext()) {
                val headerName = headerIterator.next()
                val normalizedName = headerName.lowercase()
                val values = call.request.headers.getAll(normalizedName) ?: emptyList()
                for (j in 0 until headerVerifiers.size) {
                    handleVerificationResult(
                        pipelineContext,
                        call,
                        headerVerifiers[j].verify(normalizedName, values),
                    )
                }
            }
        }
    }
