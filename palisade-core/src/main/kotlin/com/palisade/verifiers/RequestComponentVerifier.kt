package com.palisade.verifiers

import com.palisade.config.ConfigManager
import com.palisade.plugins.AppMetricsRegistry
import io.ktor.http.HttpStatusCode
import io.micrometer.core.instrument.Counter

class ForbiddenPathSegmentsVerifier(
    private val forbiddenSegmentsProvider: () -> Array<String> = { ConfigManager.current.verifierParams.forbiddenPathSegments },
) : RequestComponentVerifier {
    private val forbiddenSegmentCounter: Counter =
        Counter.builder("gateway_blocked_requests_total")
            .description("The total number of malicious requests dropped by the API gateway guards")
            .tag("reason", "malicious_request_component")
            .register(AppMetricsRegistry)

    override suspend fun verify(
        uri: String,
        path: String,
    ): VerificationResult {
        val forbiddenSegments = forbiddenSegmentsProvider()

        if (forbiddenSegments.isEmpty()) {
            return VerificationResult.Allowed
        }

        // We iterate through the raw array indices to bypass iterator object allocation
        for (i in forbiddenSegments.indices) {
            val segment = forbiddenSegments[i]

            // Fast, primitive character-array scan. Returns -1 if not found.
            if (path.indexOf(segment) != -1) {
                forbiddenSegmentCounter.increment()
                return VerificationResult.Denied(
                    status = HttpStatusCode.BadRequest,
                    message = "Malicious request path structure detected",
                )
            }
        }

        return VerificationResult.Allowed
    }
}

class AllowedPathPrefixVerifier(
    private val allowedPrefixesProvider: () -> Array<String> = { ConfigManager.current.verifierParams.allowedPathPrefixes },
) : RequestComponentVerifier {
    private val blockedPrefixesCounter: Counter =
        Counter.builder("gateway_blocked_requests_total")
            .description("The total number of malicious requests dropped by the API gateway guards")
            .tag("reason", "unauthorized_route_prefix")
            .register(AppMetricsRegistry)

    override suspend fun verify(
        uri: String,
        path: String,
    ): VerificationResult {
        val currentPrefixes = allowedPrefixesProvider()

        // Operational rule: If the list is empty, default to allowing all paths
        if (currentPrefixes.isEmpty()) {
            return VerificationResult.Allowed
        }

        val queryIndex = uri.indexOf('?')
        val sanitizedPath = if (queryIndex != -1) uri.substring(0, queryIndex) else uri

        // Fast index-based loop over the array configuration to avoid object allocation
        for (i in currentPrefixes.indices) {
            val prefix = currentPrefixes[i]
            if (sanitizedPath.startsWith(prefix) || sanitizedPath == prefix.dropLast(1)) {
                return VerificationResult.Allowed // Match found, let the call pass
            }
        }

        // None of the prefixes matched the path
        blockedPrefixesCounter.increment()
        return VerificationResult.Denied(
            // Returning 404 masks internal existence
            status = HttpStatusCode.NotFound,
            message = "The requested resource does not exist or is protected by Palisade perimeter shield.",
        )
    }
}

class MaxQueryParamCountVerifier(
    private val maxQueryParamCountProvider: () -> Int = { ConfigManager.current.verifierParams.maxQueryParamCount },
) : RequestComponentVerifier {
    private val blockedQueriesCounter: Counter =
        Counter.builder("gateway_blocked_requests_total")
            .description("The total number of malicious requests dropped by the API gateway guards")
            .tag("reason", "too_many_query_params")
            .register(AppMetricsRegistry)

    override suspend fun verify(
        uri: String,
        path: String,
    ): VerificationResult {
        val maxAllowed = maxQueryParamCountProvider()

        // Operational rule: 0 or negative means the check is disabled
        if (maxAllowed <= 0) {
            return VerificationResult.Allowed
        }

        // Fast, primitive scan of the raw URI string to find where the query starts.
        // This lets us avoid invoking Ktor's query parsing logic entirely if there are no parameters!
        val queryStart = uri.indexOf('?')
        if (queryStart == -1) {
            return VerificationResult.Allowed
        }

        // Fast check: Count the frequency of '&' in the query substring.
        // Number of params is roughly equal to number of ampersands + 1.
        var ampersandCount = 0
        for (i in (queryStart + 1) until uri.length) {
            if (uri[i] == '&') {
                ampersandCount++
            }
        }

        // If the number of parameters is safely below the maximum, skip heavy parsing
        if ((ampersandCount + 1) <= maxAllowed) {
            return VerificationResult.Allowed
        }

        // Guard triggered: Block the call immediately
        blockedQueriesCounter.increment()
        return VerificationResult.Denied(
            status = HttpStatusCode.BadRequest,
            message = "The number of query parameters exceeds the allowable limit configured by Palisade.",
        )
    }
}

class QueryParameterLengthVerifier(
    private val maxParamLengthProvider: () -> Int = { ConfigManager.current.verifierParams.maxQueryParamValueLength },
) : RequestComponentVerifier {
    private val blockedQueryLengthsCounter: Counter =
        Counter.builder("gateway_blocked_requests_total")
            .description("The total number of malicious requests dropped by the API gateway guards")
            .tag("reason", "query_param_too_long")
            .register(AppMetricsRegistry)

    override suspend fun verify(
        uri: String,
        path: String,
    ): VerificationResult {
        val maxLength = maxParamLengthProvider()

        if (maxLength <= 0) {
            return VerificationResult.Allowed
        }

        val queryStart = uri.indexOf('?')
        if (queryStart == -1 || queryStart == uri.length - 1) {
            return VerificationResult.Allowed
        }

        var currentSegmentLength = 0

        // Scan from right after '?' to the end of the URI
        for (i in (queryStart + 1) until uri.length) {
            val char = uri[i]

            if (char == '=' || char == '&') {
                // We reached a boundary (end of key or end of value)
                if (currentSegmentLength > maxLength) {
                    blockedQueryLengthsCounter.increment()
                    return VerificationResult.Denied(
                        status = HttpStatusCode.BadRequest,
                        message = "An individual query key or value exceeds the allowable limit configured by Palisade.",
                    )
                }
                currentSegmentLength = 0 // Reset for the next segment
            } else {
                currentSegmentLength++
            }
        }

        // Final check for the very last value segment in the URI string
        if (currentSegmentLength > maxLength) {
            blockedQueryLengthsCounter.increment()
            return VerificationResult.Denied(
                status = HttpStatusCode.BadRequest,
                message = "An individual query key or value exceeds the allowable limit configured by Palisade.",
            )
        }

        return VerificationResult.Allowed
    }
}
