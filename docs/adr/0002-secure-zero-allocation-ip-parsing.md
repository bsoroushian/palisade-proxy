# ADR 0002: Secure Zero-Allocation IP Extraction from Upstream Proxies

## Context
When Palisade is deployed behind an edge load balancer or CDN infrastructure (such as Cloudflare or AWS ALB), incoming HTTP connections to the proxy originate from the load balancer's internal IP network rather than the human visitor. 

To rate-limit and verify malicious behaviors accurately per client, Palisade must inspect the `X-Forwarded-For` header. However, this header presents two severe architectural vulnerabilities:
1. **IP Spoofing**: Attackers can prepend arbitrary IP strings to the header from their client tools.
2. **GC Heap Starvation**: Standard string parsing (`.split(",")`) generates multiple heap allocations per request, which can cause CPU exhaustion via Garbage Collection loops under a Layer 7 DDoS attack.

## Considered Options
1. **Standard Idiomatic Parsing (`string.split().first().trim()`)**: Highly vulnerable to client-side injection spoofing and generates massive heap trash per request under load.
2. **Ktor ForwardedHeader Support Plugin**: Introduces global interceptor hooks and internal object map structures that add processing overhead outside our core `TrafficGatekeeper` boundary.
3. **Right-to-Left Character Boundary Scanner (`ForwardedAddressParser`)**: **Selected.**

## Decision
We implemented a custom, static utility engine called `ForwardedAddressParser`. It scans the raw `X-Forwarded-For` string from right to left using primitive indexing pointers. It identifies the last delimiter and cleans structural whitespaces without allocating temporary intermediate objects.

## Consequences & Benefits (Why this design?)
* **Injection-Attack Resilience**: By reading the chain strictly from right to left, Palisade isolates the final IP appended directly by our trusted corporate network infrastructure. Any fake values injected by an external attacker on the left side of the string are safely ignored.
* **Bypassing the JVM Heap**: Pointer manipulations and character scans are processed purely within CPU registers and local thread stack frames. No `ArrayList` or short-lived string fragments are instantiated on the heap during evaluation.
* **Deterministic Resource Consumption**: A single substring allocation occurs only at the very final step when extracting the validated client identifier, shielding the reverse proxy from memory exhaustion attacks.
