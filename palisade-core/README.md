# Palisade Core Engine

The core runtime proxy engine designed to evaluate high-throughput perimeter guards natively within the Netty pipeline.

## 🛠️ Tech Stack & Tooling
* **Runtime:** Kotlin 2.4.0 (JVM)
* **Server Framework:** Ktor 3.5.0 with Netty Engine
* **Serialization:** `kotlinx-serialization-json`
* **Quality & Linting:** `ktlint` (12.2.0) with custom `.editorconfig` rules

## 💡 Key Architectural Implementations

### Non-Blocking Connection Drops
Instead of relying on heavy application-level filters, Palisade cuts connections aggressively at the socket layer when boundary limits are violated. The `EndApplicationCall` utility bypasses traditional Ktor pipelines to write optimized `Http2GoAwayFrame` or `DefaultFullHttpResponse` close directives directly to the underlying Netty context (`ChannelHandlerContext`).

### Operational Visibility
Includes a dedicated administrative route path prefix (`/_palisade`) housing:
* `/metrics`: High-fidelity Prometheus scraping metrics (`micrometer`).
* `/health`: Baseline system health diagnostics.
* `/config`: Live, immutable environment state reflection leveraging content negotiation middleware.

## 🔒 Operational Security & DevOps Tooling

The administrative path (`/_palisade/config`) is strictly locked down behind an administrative credential barrier. To support modern, zero-trust deployment environments (such as Kubernetes Secrets or CI/CD pipelines), operators are expected to inject an environment variable holding a secure BCrypt password hash.

### Built-in Administrative Tooling
To facilitate secure deployments, a custom Gradle task is bundled into the build loop. Operators can generate cryptographically strong, 12-round BCrypt hashes directly from their terminal without relying on untrusted online tools or installing external binary packages:

```bash
./gradlew generateAdminHash -Ppass="your_desired_secure_password"
```

This ensures raw production passwords are never hardcoded or checked into source control, adhering closely to the 12-Factor App design methodology.
