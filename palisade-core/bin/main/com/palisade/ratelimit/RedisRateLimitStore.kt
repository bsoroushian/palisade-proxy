package com.palisade.ratelimit

import io.lettuce.core.RedisClient
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await

class RedisRateLimitStore(redisUri: String) : RateLimitStore {
    private val client = RedisClient.create(redisUri)
    private val connection: StatefulRedisConnection<String, String> = client.connect()
    private val asyncCommands = connection.async()

    // Pure, explicit Token Bucket Lua script executed inside the Redis engine
    private val luaScript =
        """
        local key = KEYS[1]
        local max_tokens = tonumber(ARGV[1])
        local refill_rate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        
        -- Retrieve existing state values from a Redis Hash map
        local bucket = redis.call('hmget', key, 'tokens', 'last_update')
        
        local current_tokens = tonumber(bucket[1])
        local last_update = tonumber(bucket[2])
        
        if current_tokens == nil then
            -- First-time visitor initialization
            current_tokens = max_tokens
            last_update = now
        else
            -- Replenish tokens based on milliseconds elapsed since last call
            local elapsed_ms = now - last_update
            local tokens_to_add = (elapsed_ms * refill_rate) / 1000
            current_tokens = math.min(max_tokens, current_tokens + tokens_to_add)
            last_update = now
        end
        
        -- Evaluate consumption boundary check
        if current_tokens < 1 then
            return 0 -- Denied / Rate limit tripped
        else
            -- Update state and set an auto-expiry time to clean up inactive IPs
            redis.call('hmset', key, 'tokens', current_tokens - 1, 'last_update', last_update)
            
            local ttl_seconds = math.ceil(max_tokens / refill_rate) + 1
            redis.call('expire', key, ttl_seconds)
            return 1 -- Allowed
        end
        """.trimIndent()

    override suspend fun tryConsume(
        clientId: String,
        maxTokens: Long,
        refillRatePerSecond: Long,
    ): Boolean {
        val key = "ratelimit:$clientId"
        val now = System.currentTimeMillis().toString()

        return try {
            // Evaluates atomically inside Redis. Yields the thread back to Netty/Ktor while waiting.
            val result =
                asyncCommands.eval<Long>(
                    luaScript,
                    ScriptOutputType.INTEGER,
                    arrayOf(key),
                    maxTokens.toString(),
                    refillRatePerSecond.toString(),
                    now,
                ).await()

            result == 1L
        } catch (e: Exception) {
            // Fail-open architecture choice to prevent proxy failure if Redis goes down
            true
        }
    }
}
