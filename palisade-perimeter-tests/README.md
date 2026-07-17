# Palisade Perimeter Validation Suite

A black-box integration and boundary test framework written in Python using `pytest`.

## ⚙️ Automated Test Lifecycle (The Harness)
To guarantee repeatable, production-grade testing, this suite avoids global system dependencies. It uses a custom session-scoped `pytest` fixture (`manage_palisade_proxy`) to fully automate the local cloud-native lifecycle:

1. **Conflict Detection:** Scans the active operating system processes utilizing native `psutil` lookups for existing instances of `com.palisade.core.ApplicationKt`. If a duplicate instance is found, execution halts instantly to prevent port pollution.
2. **Environment Configuration:** Programmatically injects isolated configuration thresholds (`PALISADE_GUARD_MAX_HEADER_COUNT=30`) and ensures the security barrier is verified by explicitly asserting both `401 Unauthorized` and authenticated `200 OK` states within the test dictionary loop.
3. **Asynchronous Bootstrapping:** Spawns the Gradle runtime daemon via `subprocess.Popen`, piping compilation outputs silently to an on-disk log file (`palisade_proxy_boot.log`).
4. **Adaptive Port Verification:** Utilizes a socket-level connection ping loop against `localhost:8443` ensuring full IPv4/IPv6 cross-compatibility inside **WSL2** environments. Testing kicks off the exact millisecond the port goes active.
5. **Deterministic Cleanup:** Intercepts the process tree group (`os.killpg`) upon suite completion to cleanly sweep and terminate lingering JVM or Gradle worker processes.
