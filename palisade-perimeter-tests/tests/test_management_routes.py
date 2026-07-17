import requests

def test_operator_management_config_endpoint_with_auth(base_url):
    """Verifies that the secured /_palisade/config route rejects unauthenticated requests and accepts valid admin credentials."""
    # 1. Test that unauthenticated requests are strictly denied
    bad_response = requests.get(f"{base_url}/_palisade/config", verify=False)
    assert bad_response.status_code == 401

    # 2. Test that legitimate admin credentials successfully pull the JSON payload
    # 'pass123' matches the default fallback hash we defined in the proxy config
    good_response = requests.get(
        f"{base_url}/_palisade/config", 
        auth=("admin", "pass123"), 
        verify=False
    )
    
    assert good_response.status_code == 200
    config_data = good_response.json()
    assert config_data["verifierParams"]["maxHeaderCount"] == 30
