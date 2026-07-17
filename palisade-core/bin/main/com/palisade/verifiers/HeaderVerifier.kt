package com.palisade.verifiers

import com.palisade.config.ConfigManager
import com.palisade.plugins.AppMetricsRegistry
import io.ktor.http.HttpStatusCode
import io.micrometer.core.instrument.Counter

private fun createCounter(reason: String): Counter {
    return Counter.builder("gateway_blocked_requests_total")
        .description("The total number of malicious requests dropped by the API gateway guards")
        .tag("reason", reason)
        .register(AppMetricsRegistry)
}

class ProhibitedHeaderVerifier(
    private val forbiddenHeadersProvider: () -> Set<String> = { ConfigManager.current.verifierParams.forbiddenHeaders },
) : HeaderVerifier {
    private val headerNotAllowedCounter = createCounter("blacklisted_header")

    override suspend fun verify(
        name: String,
        values: List<String>,
    ): VerificationResult {
        val currentForbiddenHeaders = forbiddenHeadersProvider()

        if (currentForbiddenHeaders.isEmpty()) {
            return VerificationResult.Allowed
        }

        if (name in currentForbiddenHeaders) {
            headerNotAllowedCounter.increment()
            return VerificationResult.Denied(
                HttpStatusCode.BadRequest,
                "Prohibited header detected: '$name'",
            )
        }

        return VerificationResult.Allowed
    }
}

class SingleValueHeaderVerifier(
    private val singleValueHeadersProvider: () -> Set<String> = { ConfigManager.current.verifierParams.strictSingleValueHeaders },
) : HeaderVerifier {
    private val restrictedHeaderWithMultipleValuesCounter = createCounter("header_has_multiple_values")

    override suspend fun verify(
        name: String,
        values: List<String>,
    ): VerificationResult {
        val currentSingleValueHeaders = singleValueHeadersProvider()

        if (currentSingleValueHeaders.isEmpty()) {
            return VerificationResult.Allowed
        }

        if (name in currentSingleValueHeaders) {
            if (values.size > 1) {
                restrictedHeaderWithMultipleValuesCounter.increment()
                return VerificationResult.Denied(
                    HttpStatusCode.BadRequest,
                    "Duplicate header injection detected for strict single-value header: '$name'",
                )
            }

            if (values.isEmpty()) {
                return VerificationResult.Allowed
            }

            val singleStringValue = values[0]
            for (i in 0 until singleStringValue.length) {
                if (singleStringValue[i] == ',') {
                    restrictedHeaderWithMultipleValuesCounter.increment()
                    return VerificationResult.Denied(
                        HttpStatusCode.BadRequest,
                        "Multi-valued comma separation detected in strict single-value header: '$name'",
                    )
                }
            }
        }

        return VerificationResult.Allowed
    }
}

class MaxHeaderSizeVerifier(
    private val maxHeaderSizeProvider: () -> Int = { ConfigManager.current.verifierParams.maxHeaderSize },
) : HeaderVerifier {
    private val headerTooLongCounter = createCounter("header_too_long")

    override suspend fun verify(
        name: String,
        values: List<String>,
    ): VerificationResult {
        val currentMaxHeaderSize = maxHeaderSizeProvider()

        if (currentMaxHeaderSize <= 0) {
            return VerificationResult.Allowed
        }

        var totalSize = name.length
        if (totalSize > currentMaxHeaderSize) {
            headerTooLongCounter.increment()
            return VerificationResult.Denied(
                status = HttpStatusCode.PayloadTooLarge,
                message =
                    "Individual header '$name' total size ($totalSize bytes) " +
                        "exceeds the allowed limit of $currentMaxHeaderSize bytes",
            )
        }

        for (i in 0 until values.size) {
            totalSize += values[i].length
            if (totalSize > currentMaxHeaderSize) {
                headerTooLongCounter.increment()
                return VerificationResult.Denied(
                    status = HttpStatusCode.PayloadTooLarge,
                    message =
                        "Individual header '$name' total size ($totalSize bytes) " +
                            "exceeds the allowed limit of $currentMaxHeaderSize bytes",
                )
            }
        }

        return VerificationResult.Allowed
    }
}
