# ADR 0003: Distributed Rate Limiting via Redis Lua Scripting

## Context
As a highly available reverse proxy, Palisade needs to scale horizontally across multiple instances behind a network load balancer. An in-memory `ConcurrentHashMap` rate limiter works perfectly for a single node but cannot share state between different proxy servers. 

If Client A hits Proxy Instance 1, and their next request hits Proxy Instance 2, a localized backend allows double the configured burst traffic. To enforce strict security perimeters, traffic tracking must be centralized.

## Considered Options
1. **In-Memory Lock-Free Storage (`ConcurrentHashMap`)**: Retained as a fallback option for single-node deployments or local developer environments.
2. **Distributed Java-Side Locking via Redis (Redisson / MULTI-EXEC)**: Rejected. Fetching tokens, calculating the elapsed math in Kotlin, and sending the results back requires distributed locks or transaction retry loops. Under heavy DDoS stress, this introduces thread contention, connection pool starvation, and network latency bottlenecks.
3. **In-Database Atomic Evaluation via Redis Lua Scripts**: **Selected.**

## Decision
We implemented a conditional Factory pattern that initializes a Redis-backed rate limiter if a `redisUri` is specified in the configuration. The actual Token Bucket allocation, math calculation, and expiration logic are wrapped entirely inside an inline Lua script.

## Consequences & Benefits (Why Lua?)
* **Guaranteed Atomicity**: Redis executes Lua scripts sequentially inside its single-threaded execution core. The transaction block acts as a single primitive operation. No concurrent proxy instance can read intermediate, stale states between evaluating and decrementing tokens.
* **Network Efficiency (Reduced Round-Trips)**: Instead of generating multiple network round-trips per request (e.g., Lock -> Read -> Compute -> Write -> Unlock), the proxy sends exactly one network command string via Lettuce. The entire state evaluation happens locally inside the Redis memory process.
* **Non-Blocking Coroutines**: By leveraging Lettuce async futures and bridging them via Kotlin's `await()`, the proxy never blocks underlying Netty worker threads while awaiting the Redis network round-trip response.
* **Graceful Degradation (Fail-Open)**: If the Redis cluster goes completely down under catastrophic conditions, the implementation catches the network exception and fails open, ensuring the core reverse proxy functionality remains up.
