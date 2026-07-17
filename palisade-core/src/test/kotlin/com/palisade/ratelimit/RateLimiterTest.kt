package com.palisade.ratelimit

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RateLimiterTest {
    @Test
    fun `TokenBucket should enforce burst limits and deny traffic when exhausted`() {
        val bucket = TokenBucket()
        val maxBurst = 5L
        val refillRate = 1L // 1 token per second

        // 1. Consume up to the exact maximum burst limit
        for (i in 1..maxBurst) {
            assertTrue(bucket.tryConsume(maxBurst, refillRate), "Request $i should be allowed")
        }

        // 2. The 6th concurrent request must be short-circuited and denied
        assertFalse(bucket.tryConsume(maxBurst, refillRate), "Request 6 should be rate-limited")
    }

    @Test
    fun `LocalRateLimitStore should isolate tracking per client IP address`() =
        runTest {
            val store =
                LocalRateLimitStore(
                    maxTokensProvider = { 2L },
                    refillRatePerSecondProvider = { 1L },
                )

            val clientA = "192.168.1.50"
            val clientB = "10.0.0.99"

            // Exhaust Client A entirely
            assertTrue(store.tryConsume(clientA, 2, 1))
            assertTrue(store.tryConsume(clientA, 2, 1))
            assertFalse(store.tryConsume(clientA, 2, 1), "Client A should now be blocked")

            // Client B should remain completely unaffected because tracking is isolated
            assertTrue(store.tryConsume(clientB, 2, 1), "Client B should be allowed regardless of Client A")
            assertTrue(store.tryConsume(clientB, 2, 1))
            assertFalse(store.tryConsume(clientB, 2, 1), "Client B should now be blocked")
        }
}
