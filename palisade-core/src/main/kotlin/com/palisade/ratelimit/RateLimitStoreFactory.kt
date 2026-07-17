package com.palisade.ratelimit

import com.palisade.config.RateLimitConfig
import java.util.concurrent.atomic.AtomicReference

object RateLimitStoreFactory {
    private val cachedStore = AtomicReference<RateLimitStore?>()
    private val cachedUri = AtomicReference<String?>()

    fun getStore(config: RateLimitConfig): RateLimitStore {
        val currentUri = config.redisUri
        val existingStore = cachedStore.get()
        val existingUri = cachedUri.get()

        // Fast path: if the store exists and configuration hasn't changed, return it
        if (existingStore != null && existingUri == currentUri) {
            return existingStore
        }

        // Slow path: Configuration changed or first-time initialization
        synchronized(this) {
            // Double-checked locking pattern for safe initialization under load
            if (cachedUri.get() == currentUri && cachedStore.get() != null) {
                return cachedStore.get()!!
            }

            val newStore =
                if (currentUri.isNullOrBlank()) {
                    LocalRateLimitStore()
                } else {
                    RedisRateLimitStore(currentUri)
                }

            cachedStore.set(newStore)
            cachedUri.set(currentUri)
            return newStore
        }
    }
}
