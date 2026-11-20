#!/usr/bin/env python3
"""الخزنة المشفّرة: كل حسابات السوشيال والجدول بتتخزن مشفّرة (AES-256-GCM) في فرع `vault` جوه مستودعك.
المستودع عام، لكن الملف مجرد بايتات مشفّرة؛ المفتاح بيتحسب من APP_TOKEN (سرّك أنت بس: Secret + جوه تطبيقك).
نفس الصيغة بالظبط في التطبيق (Vault.java): base64( nonce(12) || ciphertext+tag )."""
import base64, hashlib, json, os, time, urllib.error, urllib.request

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

API = "https://api.github.com"
BRANCH = "vault"
PATH = "v.bin"


def key_from_token(token: str) -> bytes:
    return hashlib.sha256(b"reels-vault-v1|" + token.encode("utf-8")).digest()


def seal(obj: dict, token: str) -> str:
    nonce = os.urandom(12)
    ct = AESGCM(key_from_token(token)).encrypt(nonce, json.dumps(obj, ensure_ascii=False).encode("utf-8"), None)
    return base64.b64encode(nonce + ct).decode("ascii")


def unseal(b64: str, token: str) -> dict:
    raw = base64.b64decode(b64.strip())
    return json.loads(AESGCM(key_from_token(token)).decrypt(raw[:12], raw[12:], None).decode("utf-8"))


def empty() -> dict:
    return {"v": 1, "keys": {}, "accounts": [], "queue": [], "settings": {}}


class Conflict(Exception):
    pass


class Vault:
    def __init__(self, repo: str, gh_token: str, app_token: str):
        self.repo, self.gh, self.app = repo, gh_token, app_token
        self.sha = None

    def _req(self, method, path, body=None):
        r = urllib.request.Request(API + "/repos/" + self.repo + path, method=method,
                                   data=None if body is None else json.dumps(body).encode(),
                                   headers={"Authorization": "Bearer " + self.gh, "Accept": "application/vnd.github+json",
                                            "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "reels-vault",
                                            "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(r, timeout=40) as f:
                return f.status, json.loads(f.read() or b"{}")
        except urllib.error.HTTPError as e:
            try:
                return e.code, json.loads(e.read() or b"{}")
            except Exception:
                return e.code, {}

    def load(self) -> dict:
        code, j = self._req("GET", f"/contents/{PATH}?ref={BRANCH}")
        if code == 404:
            self.sha = None
            return empty()
        if code != 200:
            raise RuntimeError(f"vault read failed {code}")
        self.sha = j.get("sha")
        txt = base64.b64decode(j["content"]).decode("ascii")
        try:
            return unseal(txt, self.app)
        except Exception:
            raise RuntimeError("الخزنة مش بتتفك: غالبًا التوكن اتغيّر. اربط الحسابات من التطبيق تاني.")

    def save(self, data: dict):
        if self.sha is None:
            c, m = self._req("GET", f"/git/ref/heads/{BRANCH}")
            if c == 404:
                c2, d = self._req("GET", "/git/ref/heads/main")
                if c2 != 200:
                    raise RuntimeError("no main branch")
                self._req("POST", "/git/refs", {"ref": "refs/heads/" + BRANCH, "sha": d["object"]["sha"]})
        body = {"message": "vault", "branch": BRANCH,
                "content": base64.b64encode(seal(data, self.app).encode("ascii")).decode("ascii")}
        if self.sha:
            body["sha"] = self.sha
        code, j = self._req("PUT", f"/contents/{PATH}", body)
        if code in (409, 422):
            raise Conflict()
        if code not in (200, 201):
            raise RuntimeError(f"vault write failed {code}")
        self.sha = j["content"]["sha"]

    def mutate(self, fn, tries=6):
        """قراءة ← تعديل ← كتابة، مع إعادة المحاولة لو التطبيق كتب في نفس اللحظة."""
        for i in range(tries):
            data = self.load()
            out = fn(data)
            try:
                self.save(data)
                return out
            except Conflict:
                time.sleep(1.5 + i)
        raise RuntimeError("vault busy")
