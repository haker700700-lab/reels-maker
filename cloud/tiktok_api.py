#!/usr/bin/env python3
"""ينشر ريلز على حسابات تك توك بتاعتك من سيرفر GitHub عن طريق الـ API الرسمي (Content Posting API — Direct Post).
- الحسابات بتتبعت من التطبيق مشفّرة (RSA + AES-GCM) وبتتفك هنا بالمفتاح الخاص من Secret TT_PRIV (نفس مفتاح فيسبوك).
- كل حساب فيه: Client key/secret + Refresh token (بيعيش سنة). السيرفر بيجدّد الـ Access token (24 ساعة) لوحده في كل مرة.
- مفيش متصفح ولا كلمة سر ولا تحقق.
- لو التك توك رفض (توكن منتهي / صلاحية ناقصة / حد النشر): بنوقف الحساب ده ونسجّل السبب في التقرير.
ملاحظة مهمة: التطبيق اللي لسه ما عدّاش مراجعة (Audit) من التك توك بيقدر ينشر بخصوصية «أنا بس» (SELF_ONLY) بس."""
import base64, glob, json, os, re, sys, time
from pathlib import Path

import requests

BASE = os.environ.get("TT_API_BASE", "https://open.tiktokapis.com").rstrip("/")
GAP = int(os.environ.get("TT_GAP", "30"))          # ثواني بين كل نشرتين
CAPTION = os.environ.get("TT_CAPTION", "").strip()
DIR = os.environ.get("TT_DIR", "")
NOTES = []
SECRETS = []
JSON_H = {"Content-Type": "application/json; charset=UTF-8"}


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


def accounts():
    """بيفك تشفير الحسابات اللي بعتها التطبيق بالمفتاح الخاص من Secret TT_PRIV."""
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    try:
        items = json.loads(os.environ.get("TT_ACCS", "[]"))
        key = serialization.load_pem_private_key(os.environ["TT_PRIV"].encode(), None)
    except Exception:
        log("⚠️ مفيش حسابات، أو مفتاح فك التشفير (TT_PRIV) ناقص")
        return []
    oaep = padding.OAEP(mgf=padding.MGF1(hashes.SHA1()), algorithm=hashes.SHA1(), label=None)
    out = []
    for it in items:
        label = str(it.get("d") or it.get("n") or "?")
        if it.get("t") != "api":
            log(f"⚠️ الحساب {label}: ده حساب قديم (تسجيل دخول). أضفه تاني بالتوكن من التطبيق.")
            NOTES.append(f"{label}: حساب قديم — أضفه تاني بالتوكن")
            continue
        try:
            raw = base64.b64decode(it["k"])
            kl = key.key_size // 8
            aes = key.decrypt(raw[:kl], oaep)
            d = json.loads(AESGCM(aes).decrypt(raw[kl:kl + 12], raw[kl + 12:], None).decode("utf-8"))
            for k in ("client_secret", "refresh_token", "access_token"):
                if d.get(k):
                    SECRETS.append(d[k])
                    print(f"::add-mask::{d[k]}")
            if not d.get("refresh_token") and not d.get("access_token"):
                raise ValueError("empty")
            name = re.sub(r"[^A-Za-z0-9_-]", "", str(it.get("n", ""))) or f"acc{len(out) + 1}"
            out.append({"name": name, "label": label, **d})
        except Exception:
            log(f"⚠️ الحساب {label}: مش قادر أفك تشفيره (أضفه تاني من التطبيق لو المفتاح اتغيّر)")
    return out


def err_text(j, status=0):
    """سبب الرفض من التك توك بشكل مختصر ومفهوم."""
    e = j.get("error") if isinstance(j, dict) else None
    if isinstance(e, dict):
        code, msg = str(e.get("code", "")), str(e.get("message", ""))[:160]
    elif isinstance(e, str):
        code, msg = e, str(j.get("error_description", ""))[:160]
    else:
        return f"HTTP {status}"
    c = code.lower()
    if c in ("access_token_invalid", "invalid_grant", "invalid_token") or "expired" in msg.lower():
        return "التوكن انتهى أو اتلغى. أضف الحساب تاني من التطبيق. (" + msg + ")"
    if c in ("scope_not_authorized", "scope_permission_missed"):
        return "صلاحية ناقصة: لازم video.publish (فعّل Content Posting API → Direct Post) وأعد التفويض. (" + msg + ")"
    if "unaudited" in c:
        return "تطبيق التك توك لسه ما اتراجعش: النشر بيبقى خاص (أنا بس) لحد ما يعدّي المراجعة. (" + msg + ")"
    if c in ("rate_limit_exceeded", "spam_risk_too_many_posts", "reached_active_user_cap"):
        return "وصلت للحد المسموح من التك توك. جرّب بعدين. (" + msg + ")"
    if c in ("invalid_client", "client_key_invalid"):
        return "الـ Client key أو الـ secret غلط. أضف الحساب تاني. (" + msg + ")"
    return f"كود {code}: {msg}"


def call(method, url, **kw):
    try:
        r = requests.request(method, url, timeout=120, **kw)
    except requests.RequestException as e:
        raise Blocked("مشكلة اتصال بالتك توك: " + clean(type(e).__name__))
    try:
        j = r.json()
    except Exception:
        j = {}
    return r, j


def get_access(acc):
    """Access token صالح: بنجدّده من الـ refresh token لو متاح (وده الأصل)."""
    if acc.get("refresh_token") and acc.get("client_key") and acc.get("client_secret"):
        r, j = call("POST", f"{BASE}/v2/oauth/token/", headers={"Content-Type": "application/x-www-form-urlencoded"},
                    data={"client_key": acc["client_key"], "client_secret": acc["client_secret"],
                          "grant_type": "refresh_token", "refresh_token": acc["refresh_token"]})
        if r.status_code >= 400 or not j.get("access_token"):
            raise Blocked(err_text(j, r.status_code))
        SECRETS.append(j["access_token"])
        print(f"::add-mask::{j['access_token']}")
        if j.get("refresh_token") and j["refresh_token"] != acc["refresh_token"]:
            NOTES.append(f"{acc['label']}: التك توك طلع refresh token جديد — لو الحساب وقف بعد كده أضفه تاني")
        return j["access_token"]
    if acc.get("access_token"):
        return acc["access_token"]
    raise Blocked("التوكن ناقص (محتاج Refresh token + Client key/secret)")


def api(tok, path, body):
    h = dict(JSON_H, Authorization=f"Bearer {tok}")
    r, j = call("POST", f"{BASE}{path}", headers=h, data=json.dumps(body))
    e = j.get("error") or {}
    if r.status_code >= 400 or (isinstance(e, dict) and e.get("code") not in (None, "", "ok")):
        raise Blocked(err_text(j, r.status_code))
    return j.get("data") or {}


def check_account(acc):
    """بنتأكد إن التوكن شغّال وبنعرف مستويات الخصوصية المسموحة قبل ما نبدأ."""
    tok = get_access(acc)
    info = api(tok, "/v2/post/publish/creator_info/query/", {})
    opts = info.get("privacy_level_options") or ["SELF_ONLY"]
    acc["token"] = tok
    acc["privacy"] = "PUBLIC_TO_EVERYONE" if "PUBLIC_TO_EVERYONE" in opts else opts[0]
    acc["maxdur"] = info.get("max_video_post_duration_sec")
    acc["label"] = info.get("creator_nickname") or acc["label"]
    if acc["privacy"] != "PUBLIC_TO_EVERYONE":
        NOTES.append(f"{acc['label']}: النشر بخصوصية «أنا بس» (التطبيق لسه ما اتراجعش من التك توك)")


def post_one(acc, video, caption, tag):
    tok = acc["token"]
    size = os.path.getsize(video)
    chunk = size if size <= 64 * 1048576 else 32 * 1048576
    n = max(1, size // chunk)
    post = {"title": caption[:2000], "privacy_level": acc["privacy"], "disable_duet": False,
            "disable_comment": False, "disable_stitch": False}
    d = api(tok, "/v2/post/publish/video/init/", {
        "post_info": post,
        "source_info": {"source": "FILE_UPLOAD", "video_size": size, "chunk_size": chunk, "total_chunk_count": n}})
    pid, up = d.get("publish_id"), d.get("upload_url")
    if not pid or not up:
        raise Blocked("التك توك ماسمحش ببدء الرفع")
    with open(video, "rb") as f:
        for i in range(n):
            start = i * chunk
            end = size - 1 if i == n - 1 else start + chunk - 1
            data = f.read(end - start + 1)
            try:
                r = requests.put(up, data=data, timeout=600, headers={
                    "Content-Type": "video/mp4", "Content-Length": str(len(data)),
                    "Content-Range": f"bytes {start}-{end}/{size}"})
            except requests.RequestException as e:
                raise Blocked("فشل رفع الملف: " + clean(type(e).__name__))
            if r.status_code >= 400:
                raise Blocked(f"فشل رفع الملف (HTTP {r.status_code})")
    last = ""
    for _ in range(60):  # حتى ~5 دقايق
        time.sleep(5)
        try:
            s = api(tok, "/v2/post/publish/status/fetch/", {"publish_id": pid})
        except Blocked as e:
            last = str(e)
            continue
        st = s.get("status", "")
        if st == "FAILED":
            raise Blocked("التك توك رفض الفيديو: " + str(s.get("fail_reason", ""))[:160])
        if st in ("PUBLISH_COMPLETE", "SEND_TO_USER_INBOX"):
            return
    NOTES.append(f"{tag}: اتبعت للنشر بس لسه بيتعالج عند التك توك (تأكد من الحساب)" + (f" — {last}" if last else ""))


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
            check_account(a)
            live.append(a)
            log(f"✅ {a['label']}: التوكن شغّال ({a['privacy']})")
        except Exception as e:
            res["notes"].append(f"{a['label']}: {str(e)[:260]}")
            log(f"❌ {a['label']}: {e}")
    first = True
    for i, v in enumerate(vids, 1):      # فيديو فيديو على كل الحسابات
        for a in list(live):
            if not first:
                time.sleep(GAP)
            first = False
            try:
                post_one(a, v, CAPTION, f"{a['label']} فيديو {i}")
                res["ok"] += 1
                log(f"✅ {a['label']}: فيديو {i}/{len(vids)}")
            except Exception as e:
                res["fail"] += 1
                res["notes"].append(f"{a['label']} فيديو {i}: {clean(str(e))[:260]}")
                log(f"❌ {a['label']} فيديو {i}: {e}")
                if any(w in str(e) for w in ("التوكن", "صلاحية", "الحد المسموح")):
                    live.remove(a)  # وقف الحساب ده
    res["notes"].extend(NOTES)
    write(res, len(vids), len(accs))


def write(res, nv, na):
    line = f"نُشر {res['ok']} (فيديوهات {nv} × حسابات {na})"
    if res["fail"]:
        line += f" — فشل {res['fail']}"
    body = line + ("\n" + "\n".join(res["notes"][:8]) if res["notes"] else "")
    Path("tt_result.txt").write_text(clean(body), encoding="utf-8")
    log(body)


if __name__ == "__main__":
    main()
