import requests
import re

def test_proxy_header_count_boundaries(test_route):
    """Dynamically determine proxy overhead and strictly test boundary limits."""
    session = requests.Session()
    session.headers.clear()
    
    # 1. Intentionally trigger a block with a high count to read Ktor's internal overhead
    for i in range(40):
        session.headers[f"X-Probe-{i}"] = "value"
        
    response = session.get(test_route, verify=False)
    assert response.status_code == 400
    
    # Extract the actual number of headers Ktor saw using regex
    # Example: "Number of '43' headers exceed the limit..." -> extracts 43
    match = re.search(r"Number of '(\d+)' headers", response.text)
    if not match:
        raise AssertionError(f"Could not parse header count from proxy error: {response.text}")
        
    ktor_total_seen = int(match.group(1))
    # Overhead = What Ktor saw (e.g., 43) minus what Python sent (40) -> 3 infrastructure headers
    engine_overhead = ktor_total_seen - 40
    
    # 2. RUN TEST A: Exactly Maximum Permissible (30 total inside Ktor)
    session_pass = requests.Session()
    session_pass.headers.clear()
    
    # Fill remaining slots: 30 allowed limit minus the hidden engine overhead
    custom_pass_count = 30 - engine_overhead
    for i in range(custom_pass_count):
        session_pass.headers[f"X-Pass-{i}"] = "value"
        
    response_pass = session_pass.get(test_route, verify=False)
    assert response_pass.status_code == 200, f"Allowed path failed! Sent headers: {response_pass.request.headers.keys()}"

    # 3. RUN TEST B: Exceeding By Exactly One (31 total inside Ktor)
    session_block = requests.Session()
    session_block.headers.clear()
    
    custom_block_count = 31 - engine_overhead
    for i in range(custom_block_count):
        session_block.headers[f"X-Block-{i}"] = "value"
        
    response_block = session_block.get(test_route, verify=False)
    assert response_block.status_code == 400
    assert "exceed the limit of 30" in response_block.text
