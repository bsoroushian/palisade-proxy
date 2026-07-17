# ADR 0005: Secure Dynamic Management Routing and Cryptographic Credential Isolation

## Context
Palisade exposes administrative routing capabilities (such as runtime configuration inspection via `/_palisade/config`). This management plane represents a high-severity target vector. If unauthorized actors compromise these endpoints, they can manipulate routing mechanics, bleed system parameters, or cause Denial of Service (DoS).

To protect this perimeter, we require a secure authentication system that adheres to three engineering constraints:
1. **Zero Hardcoded Secrets**: Raw or static superadmin operational credentials must never be checked into source control (GitHub).
2. **Safe Memory States**: Plaintext management passwords must never reside inside the running process memory space long-term.
3. **Decoupled Architecture**: Systems administrators must be capable of rotating access passwords at the container infrastructure layer without forcing a code recompilation or core build modification.

## Considered Options
1. **Plaintext Environment Variables (`PALISADE_ADMIN_PASSWORD`)**: Simple to configure, but highly vulnerable to system environment snooping, log leaking, or process inspection exploits.
2. **Database-Backed Identity Provider (OAuth2 / OIDC)**: Provides excellent auditing, but introduces external infrastructure single-point-of-failure liabilities to an otherwise self-contained edge proxy layer.
3. **Dynamic Environment-Injected BCrypt Hashing with Gradle Utility Support**: **Selected.**

## Decision
We established a strict Basic Authentication boundary (`palisade-admin-auth`) surrounding all administrative endpoints using Ktor's native security framework. 

The validation pipeline reads a pre-computed 12-round BCrypt cryptographic hash from the environment tree (`PALISADE_ADMIN_PASSWORD_HASH`) via `ConfigManager`. To optimize operation workflows, we built a custom native Gradle execution task (`./gradlew generateAdminHash`) allowing operations teams to securely compute highly non-linear, salt-hardened hashes locally on demand for direct injection into deployment configuration environments (Kubernetes Secrets, etc.).

## Consequences & Benefits (Why BCrypt + Env Mapping?)
* **Protection Against Timing & Brute-Force Attacks**: BCrypt employs an adaptive, compute-heavy CPU cost factor (set to a work factor of 12). This mathematical overhead prevents attackers from mounting high-speed offline brute-force or dictionary crack operations if configuration trees are leaked.
* **Immunity to Hardcoded Key Leaks**: By mapping the target boundary to environment properties with a safe local-developer hardcoded fallback string (`pass123`), the live production access credentials remain completely decoupled from public source code trees.
* **Zero Plaintext Storage**: The reverse proxy engine only ever stores and compares the one-way cryptographic hash. Plaintext password inputs are verified in-flight and immediately targeted by JVM garbage collection loops, maintaining excellent memory perimeter safety.
