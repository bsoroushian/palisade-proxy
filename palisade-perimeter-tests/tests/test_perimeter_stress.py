import concurrent.futures
import pytest
import requests

def fire_rate_limited_request(test_route):
    """Fires a standard GET request to verify the rate limiting subsystem behavior."""
    try:
        # Standard GET request to a valid loopback or index route
        response = requests.get(test_route, verify=False, timeout=2.0)
        return response.status_code
    except Exception:
        # Catches raw TCP connection resets or socket drops
        return "CRASHED"

@pytest.mark.stress
def test_proxy_rate_limiter_perimeter_short_circuit(test_route):
    """Floods the proxy from a single client worker pool to assert 429 short-circuit triggers."""
    # This must be higher than your maxBurstTokens configuration threshold (default 100)
    TOTAL_BURST_TRAFFIC = 150       
    CONCURRENT_THREADS = 25   
    
    status_codes_collected = []

    print(f"\n🔥 Commencing Rate Limiter Perimeter Assault: Bursting {TOTAL_BURST_TRAFFIC} requests...")

    # Fire high-concurrency requests to trigger token depletion
    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENT_THREADS) as executor:
        futures = [executor.submit(fire_rate_limited_request, test_route) for _ in range(TOTAL_BURST_TRAFFIC)]
        
        for future in concurrent.futures.as_completed(futures):
            status_codes_collected.append(future.result())

    # --- SECURITY PERIMETER ASSERTIONS ---
    
    # 1. Ensure the proxy handled high concurrency without socket or thread-pool crashes
    crash_count = status_codes_collected.count("CRASHED")
    assert crash_count == 0, f"⚠️ SECURITY FAILURE: Rate limiter processing caused proxy connection crashes!"

    passed_count = status_codes_collected.count(200)
    rate_limited_count = status_codes_collected.count(429)

    print(f"📊 Rate Limiter Perimeter Metrics Summary:")
    print(f"   -> Total Requests Passed (200): {passed_count}")
    print(f"   -> Total Requests Blocked (429): {rate_limited_count}")

    # 2. Assert that the rate limiter kicked in and blocked traffic once tokens hit zero
    assert rate_limited_count > 0, "🚨 PERIMETER BREACH: Rate limiter completely failed to drop any burst traffic!"
    
    # 3. UPDATED CEILING ASSERTION: Allow a strict buffer (e.g., max 105) for time-based replenishment 
    # during the short execution window of the python test framework.
    assert passed_count <= 105, f"🚨 PERIMETER BREACH: Proxy allowed {passed_count} bursts, leaking past the token ceiling allowance!"
    
    # 4. Alternative Absolute Assert: Ensure at least a solid block chunk was intercepted
    assert rate_limited_count >= 40, f"🚨 PERIMETER BREACH: Rate limiter only caught {rate_limited_count} requests, expected at least 40+ short-circuits."