package com.palisade.ratelimit

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class TokenBucket {
    private val tokens = AtomicLong(0)
    private val lastRefillTime = AtomicLong(System.currentTimeMillis())
    private val initialized = AtomicLong(0)

    fun tryConsume(
        maxTokens: Long,
        refillRatePerSecond: Long,
    ): Boolean {
        if (initialized.compareAndSet(0, 1)) {
            tokens.set(maxTokens)
            lastRefillTime.set(System.currentTimeMillis())
        }

        while (true) {
            val now = System.currentTimeMillis()
            val lastRefill = lastRefillTime.get()
            val currentTokens = tokens.get()

            // Calculate replenishment based on time elapsed
            val elapsedMs = now - lastRefill
            val tokensToAdd = (elapsedMs * refillRatePerSecond) / 1000

            var newTokens = currentTokens
            var newRefillTime = lastRefill

            if (tokensToAdd > 0) {
                newTokens = min(maxTokens, currentTokens + tokensToAdd)
                newRefillTime = now
            }

            if (newTokens < 1) {
                return false // Rate limit exceeded
            }

            // Attempt lock-free state update
            if (tokens.compareAndSet(currentTokens, newTokens - 1)) {
                if (tokensToAdd > 0) {
                    lastRefillTime.compareAndSet(lastRefill, newRefillTime)
                }
                return true
            }
        }
    }
}
