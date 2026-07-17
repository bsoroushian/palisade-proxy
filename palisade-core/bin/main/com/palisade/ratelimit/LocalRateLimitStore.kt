package com.palisade.ratelimit

import com.palisade.config.ConfigManager
import java.util.concurrent.ConcurrentHashMap

class LocalRateLimitStore(
    // Keep these as clean defaults for standard class instantiation
    private val maxTokensProvider: () -> Long = { ConfigManager.current.rateLimit.maxBurstTokens },
    private val refillRatePerSecondProvider: () -> Long = { ConfigManager.current.rateLimit.refillRatePerSecond },
) : RateLimitStore {
    private val registry = ConcurrentHashMap<String, TokenBucket>()

    // Overriding the exact 3-parameter signature mandated by your RateLimitStore interface
    override suspend fun tryConsume(
        clientId: String,
        maxTokens: Long,
        refillRatePerSecond: Long,
    ): Boolean {
        // Use the providers to get the live, dynamic configuration values
        val currentMaxTokens = maxTokensProvider()
        val currentRefillRate = refillRatePerSecondProvider()

        // Fetch or create the bucket lock-free
        val bucket = registry.computeIfAbsent(clientId) { _ -> TokenBucket() }

        // Pass the resolved configuration values into TokenBucket
        return bucket.tryConsume(currentMaxTokens, currentRefillRate)
    }
}
