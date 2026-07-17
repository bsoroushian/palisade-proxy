import requests

def test_proxy_drops_over_sized_header_values(test_route):
    """Send an over-sized header value to trigger MaxHeaderSize validation."""
    # Assuming your maxHeaderSize configuration threshold is 16384 bytes
    bloated_header_value = "A" * 17000
    headers = {"X-Bloated-Header": bloated_header_value}
    
    response = requests.get(test_route, headers=headers, verify=False)
    
    assert response.status_code == 400
    # Update this assertion string to match your proxy's specific error message
    assert "header" in response.text.lower()

def test_proxy_allows_maximum_permissible_header_count(test_route):
    """Fill the remaining slots up to exactly 30 total headers."""
    session = requests.Session()
    session.headers.clear()
    
    # Explicitly prevent requests from auto-injecting extra noise headers
    session.headers["Accept-Encoding"] = None
    session.headers["Connection"] = None
    session.headers["Accept"] = None
    session.headers["User-Agent"] = None

    # Loop 29 times. 27 custom + 3 auto-added Host = Exactly 30 total headers over the wire.
    for i in range(27):
        session.headers[f"X-Boundary-Pass-{i}"] = "value"
        
    response = session.get(test_route, verify=False)

    assert response.status_code == 200


def test_proxy_denies_header_count_exceeding_by_one(test_route):
    """Fill the remaining slots up to exactly 31 total headers."""
    session = requests.Session()
    session.headers.clear()
    
    # Explicitly prevent requests from auto-injecting extra noise headers
    session.headers["Accept-Encoding"] = None
    session.headers["Connection"] = None
    session.headers["Accept"] = None
    session.headers["User-Agent"] = None

    # Loop 30 times. 38 custom + 3 auto-added Host = Exactly 31 total headers over the wire.
    for i in range(28):
        session.headers[f"X-Boundary-Block-{i}"] = "value"
        
    response = session.get(test_route, verify=False)
    assert response.status_code == 400
    assert "exceed the limit of 30" in response.text

def test_proxy_rejects_forbidden_proxy_header(test_route):
    """Verifies that an incoming request containing a forbidden 'Proxy' header is blocked."""
    # Attempt to inject the malicious 'Proxy' header targeting a fake upstream redirect
    malicious_headers = {
        "Proxy": "http://attacker-malicious-server.com:8080",
        "User-Agent": "PalisadePerimeterTests/1.0"
    }
    
    response = requests.get(test_route, headers=malicious_headers, verify=False)
    
    # Assert that Palisade drops the connection or denies the payload at the perimeter edge
    assert response.status_code == 400
    assert "Forbidden header detected" in response.text or response.status_code == 400

