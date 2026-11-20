#!/usr/bin/env python3
"""ينشر ريلز على صفحات فيسبوك بتاعتك من سيرفر GitHub عن طريق الـ Graph API الرسمي (Reels Publishing API).
- الصفحات بتتبعت من التطبيق مشفّرة (RSA + AES-GCM) وبتتفك هنا بالمفتاح الخاص من Secret TT_PRIV (نفس مفتاح التك توك).
- كل صفحة ليها توكن خاص بيها؛ مفيش كلمة سر ولا متصفح.
- لو فيسبوك رفض (توكن منتهي / صلاحية ناقصة / حد النشر اليومي): بنوقف الصفحة دي ونسجّل السبب في التقرير.
ملاحظة: فيسبوك بيسمح بحد أقصى 30 ريل بالـ API لكل صفحة في 24 ساعة."""
import base64, glob, json, os, re, sys, time
from pathlib import Path

import requests

VER = os.environ.get("FB_API_VERSION", "v25.0")
GRAPH = f"https://graph.facebook.com/{VER}"
GAP = int(os.environ.get("FB_GAP", "20"))          # ثواني بين كل نشرتين
CAPTION = os.environ.get("FB_CAPTION", "").strip()
DIR = os.environ.get("FB_DIR", "")
NOTES = []
SECRETS = []


class Blocked(Exception):
    pass


def log(m):
    print(clean(m), flush=True)


def clean(s):
    s = str(s)
    for t in SECRETS:
        if t:
            s = s.replace(t, "***")
    return s


def accounts():
    """بيفك تشفير الصفحات اللي بعتها التطبيق بالمفتاح الخاص من Secret TT_PRIV."""
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    try:
        items = json.loads(os.environ.get("FB_ACCS", "[]"))
        key = serialization.load_pem_private_key(os.environ["TT_PRIV"].encode(), None)
    except Exception:
        log("⚠️ مفيش صفحات، أو مفتاح فك التشفير (TT_PRIV) ناقص")
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
            pid = re.sub(r"[^0-9]", "", str(data["page_id"]))
            if not pid or not tok:
                raise ValueError("empty")
            SECRETS.append(tok)
            print(f"::add-mask::{tok}")
            name = re.sub(r"[^A-Za-z0-9_-]", "", str(it.get("n", ""))) or f"page{len(out) + 1}"
            out.append({"name": name, "label": label, "page_id": pid, "token": tok})
        except Exception:
            log(f"⚠️ الصفحة {label}: مش قادر أفك تشفيرها (أضفها تاني من التطبيق لو المفتاح اتغيّر)")
    return out


def err_text(r):
    """سبب الرفض من فيسبوك بشكل مختصر ومفهوم."""
    try:
        j = r.json()
    except Exception:
        return f"HTTP {r.status_code}"
    e = j.get("error") if isinstance(j, dict) else None
    if not e:
        return f"HTTP {r.status_code}: {str(j)[:120]}"
    code, sub, msg = e.get("code"), e.get("error_subcode"), str(e.get("message", ""))[:160]
    if code == 190:
        return "التوكن انتهى أو اتلغى. أضف الصفحة تاني من التطبيق بتوكن جديد. (" + msg + ")"
    if code in (10, 200, 283):
        return "صلاحية ناقصة: لازم pages_manage_posts و pages_read_engagement و pages_show_list وإنك أدمن على الصفحة. (" + msg + ")"
    if code in (4, 17, 32, 613) or "limit" in msg.lower():
        return "وصلت للحد المسموح من فيسبوك (30 ريل/24 ساعة لكل صفحة). (" + msg + ")"
    return f"كود {code}" + (f"/{sub}" if sub else "") + ": " + msg


def gcall(method, url, token, **kw):
    try:
        r = requests.request(method, url, headers={"Authorization": f"Bearer {token}"}, timeout=120, **kw)
    except requests.RequestException as e:
        raise Blocked("مشكلة اتصال بفيسبوك: " + clean(type(e).__name__))
    if r.status_code >= 400:
        raise Blocked(err_text(r))
    try:
        return r.json()
    except Exception:
        raise Blocked("رد غريب من فيسبوك")


def check_page(acc):
    """بنتأكد إن التوكن شغّال ويخص الصفحة دي قبل ما نبدأ."""
    j = gcall("GET", f"{GRAPH}/{acc['page_id']}", acc["token"], params={"fields": "id,name"})
    if str(j.get("id")) != acc["page_id"]:
        raise Blocked("التوكن مش بتاع الصفحة دي")
    return j.get("name", acc["label"])


def post_one(acc, video, caption, tag):
    tok, pid = acc["token"], acc["page_id"]
    size = os.path.getsize(video)
    # 1) بدء جلسة الرفع
    j = gcall("POST", f"{GRAPH}/{pid}/video_reels", tok, data={"upload_phase": "start"})
    vid, up = j.get("video_id"), j.get("upload_url")
    if not vid or not up:
        raise Blocked("فيسبوك ماسمحش ببدء الرفع")
    # 2) رفع الملف نفسه (على rupload.facebook.com)
    with open(video, "rb") as f:
        try:
            r = requests.post(up, headers={"Authorization": f"OAuth {tok}", "offset": "0", "file_size": str(size)},
                              data=f, timeout=600)
        except requests.RequestException as e:
            raise Blocked("فشل رفع الملف: " + clean(type(e).__name__))
    if r.status_code >= 400 or not r.json().get("success"):
        raise Blocked("فشل رفع الملف: " + err_text(r))
    # 3) نشر الريل
    data = {"upload_phase": "finish", "video_id": vid, "video_state": "PUBLISHED"}
    if caption:
        data["description"] = caption
    j = gcall("POST", f"{GRAPH}/{pid}/video_reels", tok, data=data)
    if not j.get("success"):
        raise Blocked("فيسبوك ماأكدش النشر")
    # 4) متابعة المعالجة لحد ما الريل يبقى منشور (أو يفشل بسبب واضح)
    last = ""
    for _ in range(60):  # حتى ~5 دقايق
        time.sleep(5)
        try:
            s = gcall("GET", f"{GRAPH}/{vid}", tok, params={"fields": "status"}).get("status", {})
        except Blocked as e:
            last = str(e)
            continue
        vs = s.get("video_status", "")
        pub = (s.get("publishing_phase") or {}).get("publish_status", "")
        proc_err = ((s.get("processing_phase") or {}).get("error") or {}).get("message")
        up_err = ((s.get("uploading_phase") or {}).get("error") or {}).get("message")
        if proc_err or up_err or vs in ("error", "upload_failed"):
            raise Blocked("فيسبوك رفض الفيديو: " + str(proc_err or up_err or vs)[:160])
        if pub == "published" or vs == "ready":
            return
    NOTES.append(f"{tag}: اتبعت للنشر بس لسه بيتعالج عند فيسبوك (تأكد من الصفحة)" + (f" — {last}" if last else ""))


def main():
    vids = sorted(glob.glob(os.path.join(DIR, "reel_*.mp4")))
    accs = accounts()
    res = {"ok": 0, "fail": 0, "notes": []}
    if not vids or not accs:
        res["notes"].append("مفيش فيديوهات أو مفيش صفحات متضافة")
        write(res, len(vids), len(accs))
        return
    live = []
    for a in accs:
        try:
            a["label"] = check_page(a)
            live.append(a)
            log(f"✅ {a['label']}: التوكن شغّال")
        except Exception as e:
            res["notes"].append(f"{a['label']}: {str(e)[:260]}")
            log(f"❌ {a['label']}: {e}")
    first = True
    for i, v in enumerate(vids, 1):      # فيديو فيديو على كل الصفحات
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
                if "التوكن" in str(e) or "صلاحية" in str(e) or "الحد المسموح" in str(e):
                    live.remove(a)  # وقف الصفحة دي
    res["notes"].extend(NOTES)
    write(res, len(vids), len(accs))


def write(res, nv, na):
    line = f"نُشر {res['ok']} (فيديوهات {nv} × صفحات {na})"
    if res["fail"]:
        line += f" — فشل {res['fail']}"
    body = line + ("\n" + "\n".join(res["notes"][:8]) if res["notes"] else "")
    Path("fb_result.txt").write_text(clean(body), encoding="utf-8")
    log(body)


if __name__ == "__main__":
    main()
