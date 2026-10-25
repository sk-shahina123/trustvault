"""Exercise a deployed TrustVault with synthetic data and the public demo accounts.

Usage: python scripts/live_security_check.py https://YOUR-SERVICE.onrender.com
Creates and deletes one new test document; never edits existing documents.
"""
import http.cookiejar
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from html.parser import HTMLParser


class Inputs(HTMLParser):
    def __init__(self):
        super().__init__()
        self.inputs = {}

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == "input" and "name" in values:
            self.inputs[values["name"]] = values.get("value", "")


class Client:
    def __init__(self, base):
        self.base = base
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))

    def request(self, path, method="GET", body=None, headers=None):
        req = urllib.request.Request(self.base + path, data=body, method=method,
                                     headers={"Accept": "application/json", **(headers or {})})
        try:
            response = self.opener.open(req, timeout=90)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            data = response.read()
            return response.status, response.headers, data

    def json(self, path):
        status, _, body = self.request(path)
        assert status == 200, (path, status)
        return json.loads(body)

    def login(self, email, password):
        status, _, body = self.request("/login", headers={"Accept": "text/html"})
        assert status == 200, ("Login page", status)
        parser = Inputs()
        parser.feed(body.decode())
        assert "_csrf" in parser.inputs, "Login CSRF token missing"
        data = urllib.parse.urlencode({"username": email, "password": password,
                                      "_csrf": parser.inputs["_csrf"]}).encode()
        self.request("/login", "POST", data, {"Content-Type": "application/x-www-form-urlencoded", "Accept": "text/html"})
        assert self.json("/api/me")["email"] == email, "Login failed"

    def change(self, path, method, body=None, content_type=None):
        csrf = self.json("/api/csrf")
        headers = {csrf["headerName"]: csrf["token"]}
        if content_type:
            headers["Content-Type"] = content_type
        return self.request(path, method, body, headers)


def run(base):
    parsed = urllib.parse.urlsplit(base)
    if parsed.scheme != "https" or not parsed.hostname or parsed.path not in ("", "/") or parsed.query or parsed.fragment:
        raise ValueError("Supply the deployed HTTPS origin, without a path or query")
    clients = {role: Client(base.rstrip("/")) for role in ("owner", "viewer", "admin", "anonymous")}
    for role, password in (("owner", "OwnerDemo!2026"), ("viewer", "ViewerDemo!2026"), ("admin", "AdminDemo!2026")):
        clients[role].login(role + "@trustvault.test", password)
    owner, viewer, admin, anonymous = (clients[role] for role in ("owner", "viewer", "admin", "anonymous"))
    assert any(c.name == "JSESSIONID" and c.secure and c.has_nonstandard_attr("HttpOnly") for c in owner.cookies), "Secure HttpOnly session cookie missing"
    marker = "TrustVault live security check " + uuid.uuid4().hex
    boundary = "----TrustVault" + uuid.uuid4().hex
    payload = (f'--{boundary}\r\nContent-Disposition: form-data; name="name"\r\n\r\n{marker}\r\n'
               f'--{boundary}\r\nContent-Disposition: form-data; name="category"\r\n\r\nSecurity test\r\n'
               f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="security-check.txt"\r\n'
               f'Content-Type: text/plain\r\n\r\n{marker}\r\n--{boundary}--\r\n').encode()
    status, _, body = owner.change("/api/documents", "POST", payload, "multipart/form-data; boundary=" + boundary)
    assert status == 201, ("Upload failed", status)
    document_id = json.loads(body)["id"]
    assert isinstance(document_id, int) and document_id > 0
    path = "/api/documents/" + str(document_id)
    deleted = False
    try:
        assert anonymous.request(path + "/download")[0] == 401
        assert viewer.request(path + "/download")[0] == 404
        assert admin.request(path + "/download")[0] == 404
        assert all(item["id"] != document_id for item in viewer.json("/api/documents"))
        status, headers, data = owner.request(path + "/download")
        assert status == 200 and data.decode() == marker
        assert "no-store" in headers.get("Cache-Control", "")
        assert headers.get("X-Content-Type-Options") == "nosniff"
        assert headers.get("Content-Disposition", "").startswith("attachment;")
        assert owner.request(path, "DELETE")[0] == 403, "Missing CSRF was accepted"
        grant = urllib.parse.urlencode({"email": "viewer@trustvault.test", "permission": "READ"}).encode()
        assert owner.change(path + "/permissions", "POST", grant, "application/x-www-form-urlencoded")[0] == 200
        assert viewer.request(path + "/download")[0] == 200
        assert viewer.change(path, "DELETE")[0] == 403
        edit = (f'--{boundary}\r\nContent-Disposition: form-data; name="name"\r\n\r\nDenied edit\r\n'
                f'--{boundary}\r\nContent-Disposition: form-data; name="category"\r\n\r\nSecurity test\r\n--{boundary}--\r\n').encode()
        assert viewer.change(path + "/edit", "POST", edit, "multipart/form-data; boundary=" + boundary)[0] == 403
        assert viewer.request("/api/admin/audit-events")[0] == 403
        recipient = next(item["userId"] for item in owner.json(path + "/permissions") if item["email"] == "viewer@trustvault.test")
        assert owner.change(path + "/permissions/" + str(recipient), "DELETE")[0] == 200
        # Use the same signed-in viewer and the same URL after revocation.
        assert viewer.request(path + "/download")[0] == 404
        assert all(item["id"] != document_id for item in viewer.json("/api/documents"))
        assert owner.change(path, "DELETE")[0] == 200
        deleted = True
        assert owner.request(path + "/download")[0] == 404
        events = [e for e in admin.json("/api/admin/audit-events") if e["documentId"] == document_id]
        observed = {(e["eventType"], e["outcome"]) for e in events}
        expected = {("UPLOAD", "SUCCESS"), ("GRANT", "SUCCESS"), ("REVOKE", "SUCCESS"),
                    ("DOWNLOAD", "SUCCESS"), ("DOWNLOAD", "DENIED"), ("EDIT", "DENIED"),
                    ("DELETE", "DENIED"), ("DELETE", "SUCCESS")}
        assert expected.issubset(observed), ("Missing audit events", sorted(expected - observed))
        print(json.dumps({"result": "PASS", "url": base, "synthetic_document_id": document_id,
                          "document_deleted": True, "audit_events_verified": sorted(observed)}, indent=2))
    finally:
        if not deleted:
            status = owner.change(path, "DELETE")[0]
            if status != 200:
                print("Cleanup needed for synthetic test document " + str(document_id), file=sys.stderr)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python scripts/live_security_check.py https://YOUR-SERVICE.onrender.com")
    run(sys.argv[1])
