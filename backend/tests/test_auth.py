from app.auth import decode_access_token, issue_access_token


def test_health_and_readiness(client):
    assert client.get("/healthz").json() == {"ok": True}
    assert client.get("/readyz").status_code == 200


def test_requires_auth(client):
    r = client.get("/v1/topics")
    assert r.status_code == 401 and r.json()["error"]["code"] == "unauthorized"


def test_refresh_rotates_and_detects_reuse(api, client):
    old = api.tokens["refresh_token"]
    new = client.post("/v1/auth/refresh", json={"refresh_token": old})
    assert new.status_code == 200
    assert client.post("/v1/auth/refresh", json={"refresh_token": old}).status_code == 401
    # reuse of the old token revoked the whole family, including the new one
    assert client.post("/v1/auth/refresh", json={"refresh_token": new.json()["refresh_token"]}).status_code == 401


def test_jwt_and_jwks(api, client):
    assert str(decode_access_token(issue_access_token(__import__("uuid").UUID(api.user_id)))) == api.user_id
    assert client.get("/.well-known/jwks.json").json()["keys"][0]["alg"] == "RS256"


def test_cannot_touch_other_users(make_api):
    a, b = make_api(), make_api()
    assert a.put(f"/v1/users/{b.user_id}/push-token", json={"fcm_token": "x"}).status_code == 403


def test_generate_rate_limit(api):
    topic_id = api.profile({"name": "Psychology", "level": "beginner", "type": "preset"})["topics"][0]["topic_id"]
    codes = [api.post("/v1/lessons/generate", json={"topic_id": topic_id, "count": 5}).status_code for _ in range(5)]
    assert codes == [200, 200, 200, 200, 429]
