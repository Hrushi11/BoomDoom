import json
from pathlib import Path

CONFIG = Path(__file__).resolve().parents[2] / "config"


def test_config_links_are_absolute_and_reachable(client):
    body = client.get("/v1/config", headers={"host": "100-22-80-77.sslip.io", "x-forwarded-proto": "https"}).json()
    assert "localhost" not in json.dumps(body)
    assert body["thresholds_url"].endswith("/v1/config/ml_thresholds.json")
    assert body["model_manifest_url"].endswith("/v1/models")
    assert body["selectors_version"] == json.loads((CONFIG / "reels_selectors.json").read_text())["version"]
    for key in ("selectors_url", "thresholds_url"):
        path = "/" + body[key].split("/", 3)[3]
        assert client.get(path).status_code == 200


def test_config_files_match_repo_and_unknown_names_404(client):
    assert client.get("/v1/config/ml_thresholds.json").json() == json.loads((CONFIG / "ml_thresholds.json").read_text())
    assert client.get("/v1/config/feature_flags.json").status_code == 200
    assert client.get("/v1/config/secrets.json").status_code == 404
