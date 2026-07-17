package com.palisade.verifiers

import com.palisade.config.ConfigManager
import com.palisade.plugins.AppMetricsRegistry
import io.ktor.http.HttpStatusCode
import io.micrometer.core.instrument.Counter

class AllowedMethodsVerifier(
    private val allowedMethodsProvider: () -> Set<String> = { ConfigManager.current.allowedMethods },
) : MethodVerifier {
    private val blockedMethodsCounter: Counter =
        Counter.builder("gateway_blocked_requests_total")
            .description("The total number of malicious requests dropped by the API gateway guards")
            .tag("reason", "blacklisted_method")
            .register(AppMetricsRegistry)

    override suspend fun verify(method: String): VerificationResult {
        val currentAllowedMethods = allowedMethodsProvider()

        if (method !in currentAllowedMethods) {
            blockedMethodsCounter.increment()
            return VerificationResult.Denied(
                HttpStatusCode.MethodNotAllowed,
                "Method Not Allowed by Palisade perimeter shield.",
            )
        }

        return VerificationResult.Allowed
    }
}
