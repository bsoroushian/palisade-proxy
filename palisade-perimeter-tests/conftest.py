import os
import socket
import sys
import signal
import subprocess
import time
import pytest
import urllib3
import psutil

# Suppress warnings for self-signed certificates used in local development
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

@pytest.fixture
def base_url():
    """Provides the live running location of your Palisade Secure Proxy."""
    return "https://localhost:8443"

@pytest.fixture
def test_route(base_url):
    """Provides the path to your conditional environment test loopback endpoint."""
    return f"{base_url}/palisade-test-loopback"

def pytest_configure(config):
    """Registers custom markers for the Palisade suite."""
    config.addinivalue_line(
        "markers", "stress: marks tests as heavy perimeter stress/load tests"
    )

@pytest.fixture(scope="session", autouse=True)
def manage_palisade_proxy():
    """
    Ensures no conflicting proxy instance is running, configures the environment,
    and manages the lifecycle of the Palisade Secure Proxy during tests.
    """
    # 1. Look for existing proxy instances
    proxy_search_term = "com.palisade.core.ApplicationKt"
    is_running = False
    
    for proc in psutil.process_iter(['pid', 'name', 'cmdline']):
        try:
            cmdline = proc.info.get('cmdline')
            if cmdline and any(proxy_search_term in arg for arg in cmdline):
                is_running = True
                break
        except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
            continue

    if is_running:
        print(f"\n[WARNING] An instance of '{proxy_search_term}' is already running!")
        print("[ERROR] Aborting test execution to prevent port binding pollution.")
        pytest.exit("Palisade proxy conflict detected. Execution halted.", returncode=1)

    # 2. Setup your target mock environments and boundary rules
    proxy_env = os.environ.copy()
    proxy_env.update({
        "PALISADE_ENV": "test",
        "PALISADE_TARGET_HOST": "http://localhost:9999",
        "PALISADE_GUARD_MAX_HEADER_COUNT": "30",
        "PALISADE_GUARD_MAX_QUERY_PARAM_VALUE_LENGTH": "256"
    })

    # 3. Handle path mapping safely relative to this script layout
    # Current: project-root/palisade-perimeter-tests/conftest.py
    # Target:  project-root/palisade-core
    suite_dir = os.path.dirname(os.path.abspath(__file__))
    core_dir = os.path.abspath(os.path.join(suite_dir, "..", "palisade-core"))
    gradlew_path = os.path.join(core_dir, "gradlew")
    log_file_path = os.path.join(suite_dir, "palisade_proxy_boot.log")

    print(f"\n[INFO] Booting Palisade Core Proxy via Gradle at: {core_dir}")
    
    process_kwargs = {}
    if os.name != 'nt':  # Linux / macOS (Including WSL2 Ubuntu)
        process_kwargs['preexec_fn'] = os.setsid

    # 4. Spawns Gradle and writes logs to a physical file to prevent deadlocks and clean up the console
    log_file = open(log_file_path, "w", encoding="utf-8")
    
    proxy_process = subprocess.Popen(
        [gradlew_path, "run"],
        cwd=core_dir,
        env=proxy_env,
        stdout=log_file,
        stderr=log_file,
        **process_kwargs
    )

    # 5. WSL2 Adaptive Socket Check
    print("[INFO] Waiting for Netty engine to listen on port 8443...")
    timeout = 30  
    start_time = time.time()
    startup_successful = False

    while time.time() - start_time < timeout:
        # Check if the process crashed immediately
        if proxy_process.poll() is not None:
            log_file.close()
            print("[ERROR] Palisade engine crashed during initialization! Dumping logs:")
            if os.path.exists(log_file_path):
                with open(log_file_path, "r", encoding="utf-8") as f:
                    print(f.read())
            pytest.exit("Palisade engine crashed during initialization.", returncode=1)
        
        # Check if port 8443 is reachable via localhost
        try:
            with socket.create_connection(("localhost", 8443), timeout=0.5):
                startup_successful = True
                print("[SUCCESS] Palisade Secure Proxy is active and listening!")
                break
        except (OSError, ConnectionRefusedError):
            pass
        
        time.sleep(0.5)

    if not startup_successful:
        if os.name == 'nt':
            proxy_process.terminate()
        else:
            os.killpg(os.getpgid(proxy_process.pid), signal.SIGTERM)
        log_file.close()
        print("[ERROR] Timeout reached! Dumping startup logs to terminal:")
        if os.path.exists(log_file_path):
            with open(log_file_path, "r", encoding="utf-8") as f:
                print(f.read())
        pytest.exit("Timeout: Palisade proxy took too long to bind to port 8443.", returncode=1)

    # Yield hooks execution context over to pytest engine runner
    yield

    # 6. Teardown hook
    print("\n[INFO] Cleaning test environment. Tearing down Palisade Proxy...")
    try:
        if os.name == 'nt':
            proxy_process.terminate()
        else:
            os.killpg(os.getpgid(proxy_process.pid), signal.SIGTERM)
        proxy_process.wait(timeout=5)
        print("[SUCCESS] Proxy process tree terminated safely.")
    except Exception as e:
        print(f"[WARNING] Forceful cleanup executed with minor exceptions: {e}")
    finally:
        log_file.close()
