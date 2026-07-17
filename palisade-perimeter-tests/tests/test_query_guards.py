import requests

def test_proxy_drops_excessive_query_parameter_counts(test_route):
    """Flood the proxy with 60 query parameters to trigger MaxQueryParamCountVerifier."""
    malicious_params = {f"key_{i}": f"val_{i}" for i in range(60)}
    
    response = requests.get(test_route, params=malicious_params, verify=False)
    
    assert response.status_code == 400
    assert "exceeds the allowable limit" in response.text

def test_proxy_drops_over_sized_query_parameter_values(test_route):
    """Send an over-sized parameter value to trigger QueryParameterLengthVerifier."""
    # Assuming your maxQueryParamValueLength configuration threshold is 256
    bloated_value = "A" * 300 
    malicious_params = {"search": bloated_value}
    
    response = requests.get(test_route, params=malicious_params, verify=False)
    
    assert response.status_code == 400
    assert "individual query key or value exceeds the allowable limit" in response.text
