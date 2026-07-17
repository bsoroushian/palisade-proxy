# ADR 001: Intercepting Ktor Pipeline vs. Netty Pipeline

## Context
We need to inspect incoming requests as early as possible to mitigate protocol-level attacks. Netty handles the underlying transport, while Ktor handles the application layer.

## Decision
We intercepted the Ktor application pipeline at the `Setup` phase instead of injecting custom handlers directly into the Netty pipeline.

## Consequences / Why
* Netty initializes different handler chains dynamically depending on HTTP/1.1 vs HTTP/2 negotiation.
* Using Netty's `pipeline.addAfter()` is fragile because target handler names change based on the protocol version.
* Ktor's pipeline provides a unified interface regardless of the underlying HTTP version.
* Early Ktor interception still catches malicious payloads before route matching occurs.
