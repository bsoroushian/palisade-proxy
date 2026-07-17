package com.palisade.config

import io.ktor.http.*
import io.ktor.server.config.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("PalisadeConfigLoader")

private val VALID_STANDARD_METHODS: Set<String> =
    setOf(
        "GET",
        "POST",
        "PUT",
        "DELETE",
        "PATCH",
        "HEAD",
        "OPTIONS",
        "TRACE",
    )
private val DEFAULT_SUPER_ADMIN_PASSWORD_HASH = "\$2a\$12\$E6tjJh3qbsgF4AmvGm3rIudgNXFLB2hkKPVtnrgWWBAkZjOrMw7aS"

// Root configuration object
@Serializable
data class PalisadeConfig(
    val targetHost: String,
    val allowedMethods: Set<String> = setOf("GET", "HEAD"),
    // BCrypt hash of pass123
    val superAdminPasswordHash: String = DEFAULT_SUPER_ADMIN_PASSWORD_HASH,
    val verifierParams: VerifierParams = VerifierParams(),
    val rateLimit: RateLimitConfig = RateLimitConfig(),
) {
    companion object {
        fun load(config: ApplicationConfig): PalisadeConfig {
            val root = config.config("palisade")

            return PalisadeConfig(
                targetHost = root.parseTargetHost(),
                allowedMethods = root.parseAllowedMethods(),
                superAdminPasswordHash = root.propertyOrNull("superAdminPasswordHash")?.getString() ?: DEFAULT_SUPER_ADMIN_PASSWORD_HASH,
                // Delegates guard parsing to a subsystem
                verifierParams = root.parseGuardConfig(),
            )
        }

        private fun ApplicationConfig.parseTargetHost(): String {
            val host = propertyOrNull("targetHost")?.getString()
            if (host.isNullOrBlank()) {
                throw IllegalArgumentException("Palisade initialization failed: 'targetHost' cannot be blank.")
            }
            return host
        }

        private fun ApplicationConfig.parseAllowedMethods(): Set<String> {
            val defaultMethods = "GET, HEAD"
            val rawMethods = propertyOrNull("allowedMethods")?.getString() ?: defaultMethods

            val validated =
                rawMethods
                    .split(",")
                    .asSequence()
                    .map { it.trim().uppercase() }
                    .filter { it.isNotBlank() }
                    .filter { method ->
                        val isValid = method in VALID_STANDARD_METHODS
                        if (!isValid) logger.warn("Ignored invalid HTTP method typo: '$method'.")
                        isValid
                    }
                    .toSet()

            return validated.ifEmpty { setOf("GET", "HEAD") }
        }

        // Dedicated pipeline orchestrator for the nested guard section
        private fun ApplicationConfig.parseGuardConfig(): VerifierParams {
            // Check if the 'guards' block even exists in the yaml. If not, load default safe parameters.
            val guardBlock = runCatching { config("verifierParams") }.getOrNull()

            return VerifierParams(
                maxHeaderSize =
                    (guardBlock?.propertyOrNull("maxHeaderSize")?.getString()?.toIntOrNull() ?: 4096)
                        .also { size ->
                            if (size in 1..4095) {
                                logger.warn("maxHeaderSize ($size) is below the recommended minimum baseline of 4096 bytes.")
                            }
                        },
                maxHeaderCount = guardBlock?.propertyOrNull("maxHeaderCount")?.getString()?.toIntOrNull() ?: 0,
                strictSingleValueHeaders = guardBlock?.parseHeaderSet("strictSingleValueHeaders") ?: emptySet(),
                forbiddenHeaders = guardBlock?.parseHeaderSet("forbiddenHeaders") ?: emptySet(),
                maxQueryParamCount = guardBlock?.propertyOrNull("maxQueryParamCount")?.getString()?.toIntOrNull() ?: 50,
                maxQueryParamValueLength = guardBlock?.propertyOrNull("maxQueryParamValueLength")?.getString()?.toIntOrNull() ?: 250,
                allowedPathPrefixes =
                    (
                        guardBlock?.parseCommaSeparatedValues("allowedPathPrefixes") { prefix ->
                            if (prefix.endsWith("/")) prefix else "$prefix/"
                        } ?: emptyArray()
                    )
                        .also { prefixes ->
                            if (prefixes.isNotEmpty()) {
                                logger.info("Palisade whitelisting enabled with ${prefixes.size} sanitized path prefixes.")
                            }
                        },
                forbiddenPathSegments = guardBlock?.parseCommaSeparatedValues("forbiddenPathSegments") ?: emptyArray(),
            )
        }

        private fun ApplicationConfig.parseHeaderSet(propertyKey: String): Set<String> {
            return propertyOrNull(propertyKey)?.getString()
                ?.split(",")
                ?.map { it.trim().lowercase() }
                ?.filter { it.isNotBlank() }
                ?.toSet() ?: emptySet()
        }

        private fun ApplicationConfig.parseCommaSeparatedValues(
            property: String,
            transform: (String) -> String = { it },
        ): Array<String> {
            return this.propertyOrNull(property)
                ?.getString()
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.map { transform(it) }
                ?.toTypedArray()
                ?: emptyArray()
        }

        private fun ApplicationConfig.parseRateLimitConfig(): RateLimitConfig {
            val rateLimitBlock = runCatching { config("rateLimit") }.getOrNull()

            return RateLimitConfig(
                enabled = rateLimitBlock?.propertyOrNull("enabled")?.getString()?.toBooleanStrictOrNull() ?: true,
                maxBurstTokens = rateLimitBlock?.propertyOrNull("maxBurstTokens")?.getString()?.toLongOrNull() ?: 100L,
                refillRatePerSecond = rateLimitBlock?.propertyOrNull("refillRatePerSecond")?.getString()?.toLongOrNull() ?: 10L,
                redisUri = rateLimitBlock?.propertyOrNull("redisUri")?.getString(),
            )
        }
    }
}

// Child configuration object exclusively for guard configurations
@Serializable
data class VerifierParams(
    val maxHeaderSize: Int = 4096,
    val maxHeaderCount: Int = 100,
    val strictSingleValueHeaders: Set<String> = emptySet(),
    val forbiddenHeaders: Set<String> = emptySet(),
    val maxQueryParamCount: Int = 50,
    val maxQueryParamValueLength: Int = 256,
    val allowedPathPrefixes: Array<String> = emptyArray(),
    val forbiddenPathSegments: Array<String> = arrayOf("/../", "//"),
)

@Serializable
data class RateLimitConfig(
    val enabled: Boolean = true,
    val maxBurstTokens: Long = 100,
    val refillRatePerSecond: Long = 10,
    val redisUri: String? = null,
)
