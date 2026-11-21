#!/usr/bin/env python3
"""ينشر الريلز على حسابات X (تويتر سابقًا) من سيرفر GitHub عن طريق X API v2 الرسمي (رفع فيديو بالأجزاء + تغريدة).
- الحسابات بتتبعت من التطبيق مشفّرة (RSA + AES-GCM) وبتتفك هنا بالمفتاح الخاص من Secret TT_PRIV (نفس مفتاح التك توك/فيسبوك).
- كل حساب ليه 4 مفاتيح من developer.x.com (OAuth 1.0a): API Key / API Secret / Access Token / Access Token Secret
  (لازم صلاحية التطبيق «Read and write»).
- لو X رفض (مفاتيح غلط / صلاحية ناقصة / حد الاستخدام): بنوقف الحساب ده ونسجّل السبب في التقرير.
ملاحظة: حدود النشر بتتحدد من باقة حسابك المطوّر عند X (الباقة المجانية محدودة جدًا وبتتغيّر)، فراجعها في developer.x.com."""
import base64, glob, hashlib, hmac, json, os, re, secrets, sys, time
import urllib.parse
from pathlib import Path

import requests

API = os.environ.get("X_API", "https://api.x.com")
GAP = int(os.environ.get("X_GAP", "15"))
CAPTION = os.environ.get("X_CAPTION", "").strip()
DIR = os.environ.get("X_DIR", "")
CHUNK = 4 * 1024 * 1024
NOTES = []
SECRETS = []


class Blocked(Exception):
    pass


def clean(s):
    s = str(s)
    for t in SECRETS:
        if t:
            s = s.replace(t, "***")
    return s


def log(m):
    print(clean(m), flush=True)


# ───────── OAuth 1.0a (HMAC-SHA1) — من غير مكتبات خارجية ─────────
def _enc(v):
    return urllib.parse.quote(str(v), safe="~")


def oauth_header(method, url, acc, query=None, nonce=None, ts=None):
    """الـ Authorization header. الجسم (JSON / multipart) مش داخل في التوقيع؛ بس الـ query."""
    op = {
        "oauth_consumer_key": acc["ck"],
        "oauth_nonce": nonce or secrets.token_hex(16),
        "oauth_signature_method": "HMAC-SHA1",
        "oauth_timestamp": str(ts or int(time.time())),
        "oauth_token": acc["at"],
        "oauth_version": "1.0",
    }
    allp = dict(op)
    for k, v in (query or {}).items():
        allp[k] = v
    pstr = "&".join(f"{_enc(k)}={_enc(v)}" for k, v in sorted(allp.items(), key=lambda kv: (_enc(kv[0]), _enc(kv[1]))))
    base = "&".join([method.upper(), _enc(url), _enc(pstr)])
    key = f"{_enc(acc['cs'])}&{_enc(acc['as'])}"
    sig = base64.b64encode(hmac.new(key.encode(), base.encode(), hashlib.sha1).digest()).decode()
    op["oauth_signature"] = sig
    return "OAuth " + ", ".join(f'{_enc(k)}="{_enc(v)}"' for k, v in sorted(op.items()))


def accounts():
    """بيفك تشفير الحسابات اللي بعتها التطبيق بالمفتاح الخاص من Secret TT_PRIV."""
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    try:
        items = json.loads(os.environ.get("X_ACCS", "[]") or "[]")
        key = serialization.load_pem_private_key(os.environ["TT_PRIV"].encode(), None)
    except Exception:
        log("⚠️ مفيش حسابات، أو مفتاح فك التشفير (TT_PRIV) ناقص")
        return []
    oaep = padding.OAEP(mgf=padding.MGF1(hashes.SHA1()), algorithm=hashes.SHA1(), label=None)
    out = []
    for it in items:
        label = str(it.get("d") or it.get("n") or "?")
        try:
            raw = base64.b64decode(it["k"])
            kl = key.key_size // 8
            aes = key.decrypt(raw[:kl], oaep)
            data = json.loads(AESGCM(aes).decrypt(raw[kl:kl + 12], raw[kl + 12:], None).decode("utf-8"))
            acc = {k: str(data[k]).strip() for k in ("ck", "cs", "at", "as")}
            if not all(acc.values()):
                raise ValueError("empty")
            for v in acc.values():
                SECRETS.append(v)
                print(f"::add-mask::{v}")
            acc["label"] = label
            out.append(acc)
        except Exception:
            log(f"⚠️ الحساب {label}: مش قادر أفك تشفيره (أضفه تاني من التطبيق لو المفتاح اتغيّر)")
    return out


def err_text(r):
    try:
        j = r.json()
    except Exception:
        return f"HTTP {r.status_code}"
    detail = ""
    if isinstance(j, dict):
        detail = str(j.get("detail") or j.get("title") or j.get("error") or (j.get("errors") or [{}])[0].get("message", ""))[:160]
    if r.status_code == 401:
        return "المفاتيح غلط أو اتلغت. أضف الحساب تاني من التطبيق. (" + detail + ")"
    if r.status_code == 403:
        return "صلاحية ناقصة: لازم تطبيقك يبقى «Read and write» وتولّد الـ Access Token بعد تغيير الصلاحية، أو باقتك لا تسمح بالنشر/رفع الفيديو. (" + detail + ")"
    if r.status_code == 429:
        return "وصلت للحد المسموح من X (باقتك). (" + detail + ")"
    return f"HTTP {r.status_code}: {detail}"


def xcall(method, path, acc, query=None, **kw):
    url = API + path
    hdr = {"Authorization": oauth_header(method, url, acc, query)}
    try:
        r = requests.request(method, url, headers=hdr, params=query, timeout=120, **kw)
    except requests.RequestException as e:
        raise Blocked("مشكلة اتصال بـ X: " + clean(type(e).__name__))
    if r.status_code >= 400:
        raise Blocked(err_text(r))
    try:
        return r.json() if r.content else {}
    except Exception:
        raise Blocked("رد غريب من X")


def check_account(acc):
    j = xcall("GET", "/2/users/me", acc)
    u = (j.get("data") or {}).get("username")
    if not u:
        raise Blocked("المفاتيح مش بتاعة حساب صالح")
    return "@" + u


def upload_video(acc, video):
    size = os.path.getsize(video)
    j = xcall("POST", "/2/media/upload/initialize", acc,
              json={"media_type": "video/mp4", "total_bytes": size, "media_category": "tweet_video"})
    mid = str((j.get("data") or {}).get("id") or "")
    if not mid:
        raise Blocked("X ماسمحش ببدء رفع الفيديو")
    with open(video, "rb") as f:
        seg = 0
        while True:
            chunk = f.read(CHUNK)
            if not chunk:
                break
            xcall("POST", f"/2/media/upload/{mid}/append", acc,
                  files={"media": ("chunk.bin", chunk, "application/octet-stream")}, data={"segment_index": str(seg)})
            seg += 1
    j = xcall("POST", f"/2/media/upload/{mid}/finalize", acc)
    info = (j.get("data") or {}).get("processing_info")
    for _ in range(60):      # حتى ~5 دقايق لمعالجة الفيديو
        if not info or info.get("state") == "succeeded":
            return mid
        if info.get("state") == "failed":
            raise Blocked("X رفض الفيديو: " + str((info.get("error") or {}).get("message", ""))[:160])
        time.sleep(max(2, min(15, int(info.get("check_after_secs", 5)))))
        j = xcall("GET", "/2/media/upload", acc, query={"command": "STATUS", "media_id": mid})
        info = (j.get("data") or {}).get("processing_info")
    raise Blocked("معالجة الفيديو عند X طوّلت — جرّب تاني")


def post_one(acc, video, caption):
    mid = upload_video(acc, video)
    body = {"media": {"media_ids": [mid]}}
    if caption:
        body["text"] = caption[:280]
    j = xcall("POST", "/2/tweets", acc, json=body)
    tid = (j.get("data") or {}).get("id")
    if not tid:
        raise Blocked("X ماأكدش النشر")
    return tid


def main():
    vids = sorted(glob.glob(os.path.join(DIR, "reel_*.mp4")))
    accs = accounts()
    res = {"ok": 0, "fail": 0, "notes": []}
    if not vids or not accs:
        res["notes"].append("مفيش فيديوهات أو مفيش حسابات متضافة")
        write(res, len(vids), len(accs))
        return
    live = []
    for a in accs:
        try:
            a["label"] = check_account(a)
            live.append(a)
            log(f"✅ {a['label']}: المفاتيح شغّالة")
        except Exception as e:
            res["notes"].append(f"{a['label']}: {clean(str(e))[:260]}")
            log(f"❌ {a['label']}: {e}")
    first = True
    for i, v in enumerate(vids, 1):
        for a in list(live):
            if not first:
                time.sleep(GAP)
            first = False
            try:
                post_one(a, v, CAPTION)
                res["ok"] += 1
                log(f"✅ {a['label']}: فيديو {i}/{len(vids)}")
            except Exception as e:
                res["fail"] += 1
                res["notes"].append(f"{a['label']} فيديو {i}: {clean(str(e))[:260]}")
                log(f"❌ {a['label']} فيديو {i}: {e}")
                if any(w in str(e) for w in ("المفاتيح", "صلاحية", "الحد المسموح")):
                    live.remove(a)
    res["notes"].extend(NOTES)
    write(res, len(vids), len(accs))


def write(res, nv, na):
    line = f"نُشر {res['ok']} (فيديوهات {nv} × حسابات {na})"
    if res["fail"]:
        line += f" — فشل {res['fail']}"
    body = line + ("\n" + "\n".join(res["notes"][:8]) if res["notes"] else "")
    Path("x_result.txt").write_text(clean(body), encoding="utf-8")
    log(body)


if __name__ == "__main__":
    main()
