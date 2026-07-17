# Palisade Secure Reverse Proxy & Perimeter Validation Harness

An enterprise-grade, high-throughput HTTPS reverse proxy engineered to enforce strict zero-trust boundary controls, protocol validation, and perimeter threat mitigation. Built with **Kotlin**, **Ktor**, and **Netty**, and fully validated via an automated, isolated lifecycle testing harness in **Python**.

[![Security Focus](https://shields.io)](https://fortinet.com)
[![Kotlin](https://shields.io)](https://kotlinlang.org)
[![Python](https://shields.io)](https://python.org)

---

## 🏗️ System Architecture & Threat Model

Palisade sits at the network edge, acting as a defensive shield for internal microservices. It intercepts raw traffic, performs lightweight, non-blocking deep packet inspections, and drops malicious anomalies before they can pollute downstream application runtimes.

[ Public Internet ]
│  (Malicious Payload / Injections)
▼
┌──────────────────────────────┐
│  palisade-core (HTTPS:8443)  │  ◄── Automated Lifecycle Hook
├──────────────────────────────┤      (palisade-perimeter-tests)
│  • Header Count & Size Guard │
│  • Httpoxy Mitigation Layer  │
│  • Query Param Length Filter │
└──────────────┬───────────────┘│  
(Sanitized & Verified Traffic)
▼
┌──────────────────────────────┐
│ catalogue-service (HTTP:9999)│  ◄── Mock Downstream Target
└──────────────────────────────┘

 ### 🛡️ Core Defensive Capabilities
* **HTTP OxyFile (Httpoxy) Attack Mitigation:** Explicitly blocks and strips incoming `Proxy` headers at the gateway edge to prevent remote code execution and downstream connection hijacking.
* **Header Structural Validation:** Restricts global header counts (`maxHeaderCount=30`) and enforces strict limits on header token sizes to neutralize buffer overflow and Denial of Service (DoS) vectors.
* **Query Parameter Boundary Filters:** Implements tight length constraints on raw string queries (`maxQueryParamValueLength=256`) to isolate SQLi/XSS scanning payloads before they hit application parsers.
* **Secured Operator Management Plane:** Protects sensitive core configurations using Ktor's `Authentication` plugin via HTTP Basic Auth. Passwords are dynamically validated against an isolated, 12-round **BCrypt cryptographic hash** injected via environment variables (`PALISADE_SUPERADMIN_PASS_HASH`), leaving telemetry routes (`/metrics` and `/health`) open for public scraping.

---

## 📂 Repository Structure

* [**`palisade-core`**](./palisade-core/): The core reverse proxy engine. Written in asynchronous, non-blocking Kotlin utilizing Netty native transport layers.
* [**`palisade-perimeter-tests`**](./palisade-perimeter-tests/): The Python-based validation harness. Programmatically handles end-to-end container setups, mock routing injections, boundary verification, and heavy load stress tests.
* [**`catalogue-service`**](./catalogue-service/): A lightweight, stateless downstream microservice utilized as an upstream target for proxy loopback evaluation.

## 📐 Architecture Decision Records (ADRs)

We use Architecture Decision Records to document the history and context behind our core technical design choices. You can find the full context in the [`/docs/adr/`](./docs/adr/) directory:

* [ADR 0001: Intercept Ktor Pipeline](./docs/adr/0001-intercept-ktor-pipeline.md) — Custom request lifecycle pipeline hooks for low-latency inspection.
* [ADR 0002: Secure Zero-Allocation IP Parsing](./docs/adr/0002-secure-zero-allocation-ip-parsing.md) — Mitigating garbage collection pauses during high-throughput network parsing.
* [ADR 0003: Distributed Rate Limiting via Redis Lua](./docs/adr/0003-distributed-rate-limiting-via-redis-lua.md) — Atomic, thread-safe rate checking using Redis script offloading.
* [ADR 0004: Avoiding For-Each Iterator Allocations](./docs/adr/0004-avoiding-foreach-iterator-allocations.md) — Optimizing critical hot-paths against heap allocation overhead.
* [ADR 0005: Secure Dynamic Management Routing](./docs/adr/0005-secure-dynamic-management-routing.md) — Hardening internal configuration endpoints against external access.

---
## ⚡ Quick Start & Verification

Palisade includes a fully automated lifecycle test harness that configures, boots, tests, and tears down the infrastructure cleanly without manual intervention.

### Prerequisites (WSL2 Ubuntu / Linux)
Ensure your environment is locked down and contains local virtual dependencies:
```bash
# Verify Kotlin/Gradle capabilities inside palisade-core
cd palisade-core && ./gradlew ktlintCheck

# Initialize the automated Python perimeter test suite
cd ../palisade-perimeter-tests
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### Run Functional Perimeter Checks
```bash
pytest -s -v -m "not stress"
```

### Run Heavy Load Perimeter Stress Tests
```bash
pytest -s -v -m "stress"
```