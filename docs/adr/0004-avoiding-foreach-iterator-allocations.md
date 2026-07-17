# ADR 0004: Avoiding Kotlin forEach Loops in Guard Pipelines

## Context
When Palisade is subjected to a distributed brute-force or high-concurrency layer-7 attack (such as a DDoS targeting route validation strings), the `TrafficGatekeeper` interceptor processes thousands of requests per second. 

Every microsecond spent on garbage collection (GC) pauses directly degrades proxy throughput and risks dropping legitimate TCP connections.

## Considered Options
1. **Kotlin Idiomatic Functional Loops (`forEach`)**: Highly readable and expressive.
2. **Standard Java-Style Iterators (`for (item in list)`)**: Readably expressive but hides object generation under the hood for certain collections.
3. **Primitive Index-Based Scans (`for (i in 0 until size)`)**: Less expressive but completely free of object allocations.

## Decision
We strictly banned the use of `.forEach {}` and standard closure iterators within the hot paths of `TrafficGatekeeper`, our request path string checks, and all `RequestComponentVerifier` implementations. We replaced them with explicit index loops over primitive arrays or `Indices` ranges.

## Consequences & Benefits (Why avoid forEach?)
* **Zero Iterator Object Allocation**: Calling a functional `.forEach {}` lambda or a standard object iterator instantiates an internal `Iterator` object allocation on the heap *per collection evaluated per request*. Under a stress assault of 50,000 malicious requests, this forces millions of short-lived allocations, triggering JVM garbage collection overhead.
* **Primitive Stack Execution**: Index loops map directly to primitive integer comparisons and local array lookups (`ALOAD` / `ILOAD` opcodes in JVM bytecode). This keeps the loop data entirely inside the CPU cache registers and thread stacks, bypassing the JVM heap completely.
* **Deterministic Guard Performance**: The execution time of our validation perimeter remains fully predictable and low-latency, denying malicious traffic efficiently without degrading the overall Netty thread loop.
