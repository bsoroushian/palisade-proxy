import requests

def test_proxy_allows_whitelisted_http_methods(test_route):
    """Verify that a standard GET request passes cleanly through the perimeter shield."""
    response = requests.get(test_route, verify=False)
    
    assert response.status_code == 200
    assert response.text == "PALISADE_PASSED"

def test_proxy_blocks_unauthorized_http_methods(test_route):
    """Strike the proxy with an unauthorized DELETE request to ensure it drops the call."""
    response = requests.delete(test_route, verify=False)
    
    # Verifies your MethodNotAllowed intercept returns the strict status code
    assert response.status_code == 405
    assert "Method Not Allowed" in response.text
