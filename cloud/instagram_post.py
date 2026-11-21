#!/usr/bin/env python3
"""ينشر ريلز على حسابات إنستجرام (Business/Creator) بتاعتك من سيرفر GitHub عن طريق الـ API الرسمي (Instagram Content Publishing).
- الحسابات بتتبعت من التطبيق مشفّرة (RSA + AES-GCM) وبتتفك هنا بالمفتاح الخاص من Secret TT_PRIV (نفس مفتاح التك توك وفيسبوك).
- كل حساب ليه توكن خاص بيه (توكن الصفحة المربوطة بيه)؛ مفيش كلمة سر ولا متصفح.
- الرفع بيتم مباشرة من السيرفر (Resumable upload) من غير ما الفيديو يحتاج رابط عام.
- لو إنستجرام رفض (توكن منتهي / صلاحية ناقصة / حد النشر): بنوقف الحساب ده ونسجّل السبب في التقرير.
ملاحظة: حد إنستجرام 100 منشور بالـ API لكل حساب في 24 ساعة."""
import base64, glob, json, os, re, sys, time
from pathlib import Path

import requests

VER = os.environ.get("IG_API_VERSION", "v25.0")
GRAPH = f"https://graph.facebook.com/{VER}"
GAP = int(os.environ.get("IG_GAP", "20"))          # ثواني بين كل نشرتين
CAPTION = os.environ.get("IG_CAPTION", "").strip()[:2200]
DIR = os.environ.get("IG_DIR", "")
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


def accounts():
    """بيفك تشفير الحسابات اللي بعتها التطبيق بالمفتاح الخاص من Secret TT_PRIV."""
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    try:
        items = json.loads(os.environ.get("IG_ACCS", "[]"))
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
            tok = data["token"]
            iid = re.sub(r"[^0-9]", "", str(data["ig_id"]))
            if not iid or not tok:
                raise ValueError("empty")
            SECRETS.append(tok)
            print(f"::add-mask::{tok}")
            name = re.sub(r"[^A-Za-z0-9_-]", "", str(it.get("n", ""))) or f"ig{len(out) + 1}"
            out.append({"name": name, "label": label, "ig_id": iid, "token": tok})
        except Exception:
            log(f"⚠️ الحساب {label}: مش قادر أفك تشفيره (أضفه تاني من التطبيق لو المفتاح اتغيّر)")
    return out


def err_text(r):
    """سبب الرفض من إنستجرام بشكل مختصر ومفهوم."""
    try:
        j = r.json()
    except Exception:
        return f"HTTP {r.status_code}"
    e = j.get("error") if isinstance(j, dict) else None
    if not e:
        return f"HTTP {r.status_code}: {str(j)[:120]}"
    code, sub, msg = e.get("code"), e.get("error_subcode"), str(e.get("error_user_msg") or e.get("message", ""))[:170]
    if code == 190:
        return "التوكن انتهى أو اتلغى. أضف الحساب تاني من التطبيق بتوكن جديد. (" + msg + ")"
    if code in (10, 200, 283) or (code == 100 and "permission" in msg.lower()):
        return "صلاحية ناقصة: لازم instagram_basic و instagram_content_publish و pages_show_list و pages_read_engagement. (" + msg + ")"
    if sub == 2207042 or code in (4, 17, 32, 613) or "limit" in msg.lower():
        return "وصلت للحد المسموح من إنستجرام (100 منشور/24 ساعة لكل حساب). (" + msg + ")"
    return f"كود {code}" + (f"/{sub}" if sub else "") + ": " + msg


def gcall(method, path, token, **kw):
    url = path if path.startswith("http") else f"{GRAPH}/{path.lstrip('/')}"
    try:
        r = requests.request(method, url, headers={"Authorization": f"Bearer {token}"}, timeout=120, **kw)
    except requests.RequestException as e:
        raise Blocked("مشكلة اتصال بإنستجرام: " + clean(type(e).__name__))
    if r.status_code >= 400:
        raise Blocked(err_text(r))
    try:
        return r.json()
    except Exception:
        raise Blocked("رد غريب من إنستجرام")


def check_account(acc):
    """بنتأكد إن التوكن شغّال ويخص الحساب ده، وإن الحساب Business/Creator، وبنقرا حد النشر."""
    j = gcall("GET", acc["ig_id"], acc["token"], params={"fields": "id,username"})
    if str(j.get("id")) != acc["ig_id"]:
        raise Blocked("التوكن مش بتاع الحساب ده")
    try:
        q = gcall("GET", f"{acc['ig_id']}/content_publishing_limit", acc["token"],
                  params={"fields": "quota_usage,config"}).get("data") or [{}]
        used = q[0].get("quota_usage")
        total = (q[0].get("config") or {}).get("quota_total")
        if used is not None and total:
            acc["left"] = int(total) - int(used)
            if acc["left"] <= 0:
                raise Blocked("وصلت للحد المسموح من إنستجرام (100 منشور/24 ساعة). جرّب بعدين.")
    except Blocked as e:
        if "الحد" in str(e):
            raise
    return "@" + j["username"] if j.get("username") else acc["label"]


def post_one(acc, video, caption, tag):
    tok, iid = acc["token"], acc["ig_id"]
    size = os.path.getsize(video)
    # 1) إنشاء حاوية ريل بالرفع المباشر
    data = {"media_type": "REELS", "upload_type": "resumable", "share_to_feed": "true"}
    if caption:
        data["caption"] = caption
    j = gcall("POST", f"{iid}/media", tok, data=data)
    cid, up = j.get("id"), j.get("uri")
    if not cid:
        raise Blocked("إنستجرام ماسمحش ببدء الرفع")
    up = up or f"https://rupload.facebook.com/ig-api-upload/{VER}/{cid}"
    # 2) رفع الملف نفسه
    with open(video, "rb") as f:
        try:
            r = requests.post(up, headers={"Authorization": f"OAuth {tok}", "offset": "0", "file_size": str(size)},
                              data=f, timeout=900)
        except requests.RequestException as e:
            raise Blocked("فشل رفع الملف: " + clean(type(e).__name__))
    try:
        ok = r.status_code < 400 and r.json().get("success")
    except Exception:
        ok = False
    if not ok:
        raise Blocked("فشل رفع الملف: " + err_text(r))
    # 3) استنى المعالجة تخلص
    last = ""
    for _ in range(72):  # حتى ~6 دقايق
        time.sleep(5)
        try:
            s = gcall("GET", cid, tok, params={"fields": "status_code,status"})
        except Blocked as e:
            last = str(e)
            continue
        sc = s.get("status_code", "")
        if sc in ("ERROR", "EXPIRED"):
            raise Blocked("إنستجرام رفض الفيديو: " + str(s.get("status", sc))[:170])
        if sc in ("FINISHED", "PUBLISHED"):
            break
    else:
        raise Blocked("المعالجة ماخلصتش في الوقت المناسب عند إنستجرام" + (f" — {last}" if last else ""))
    if sc == "PUBLISHED":
        return
    # 4) النشر (بنعيد لو لسه مش جاهز)
    err = ""
    for k in range(4):
        try:
            p = gcall("POST", f"{iid}/media_publish", tok, data={"creation_id": cid})
            if p.get("id"):
                return
            err = "إنستجرام ماأكدش النشر"
        except Blocked as e:
            err = str(e)
            if "2207027" not in err and "not ready" not in err.lower() and "غير جاهز" not in err:
                break
        time.sleep(10)
    raise Blocked(err or "فشل النشر")


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
            log(f"✅ {a['label']}: التوكن شغّال")
        except Exception as e:
            res["notes"].append(f"{a['label']}: {clean(str(e))[:260]}")
            log(f"❌ {a['label']}: {e}")
    first = True
    for i, v in enumerate(vids, 1):      # فيديو فيديو على كل الحسابات
        for a in list(live):
            if a.get("left") is not None and a["left"] <= 0:
                live.remove(a)
                res["notes"].append(f"{a['label']}: وقفت عند حد النشر اليومي")
                continue
            if not first:
                time.sleep(GAP)
            first = False
            try:
                post_one(a, v, CAPTION, f"{a['label']} فيديو {i}")
                if a.get("left") is not None:
                    a["left"] -= 1
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
    Path("ig_result.txt").write_text(clean(body), encoding="utf-8")
    log(body)


if __name__ == "__main__":
    main()
