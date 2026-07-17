package com.palisade.ratelimit

interface RateLimitStore {
    suspend fun tryConsume(
        clientId: String,
        maxTokens: Long,
        refillRatePerSecond: Long,
    ): Boolean
}
