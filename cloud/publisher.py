#!/usr/bin/env python3
"""ناشر الريلز على السيرفر (GitHub Actions) — بيشتغل لوحده حتى لو موبايلك مقفول أو من غير نت.
  python cloud/publisher.py enqueue   → بعد ما الريلز تتعمل: يحط كل ريل في جدول كل حساب مفعّل
  python cloud/publisher.py run       → ينشر اللي معاده جه (بيتنادى كل 5 دقايق من publisher.yml)
  python cloud/publisher.py pinned    → أرقام العمليات اللي ريلزها لسه مستنية النشر (عشان ما تتمسحش)
الأسرار (توكنات الحسابات) بتتفك في الذاكرة بس ومش بتتطبع في أي لوج."""
import glob, json, math, os, re, subprocess, sys, tempfile, time, urllib.error, urllib.parse, urllib.request, uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vault import Vault  # noqa: E402

GRAPH = "https://graph.facebook.com/v21.0"
MAX_TRIES = 3
RETRY_MIN = 15
STALE_SEC = 40 * 60
SUPPORTED = ("facebook", "instagram", "tiktok", "youtube")


class Fatal(Exception):
    pass


class Transient(Exception):
    pass


def clean(msg, secrets=()):
    s = str(msg)
    for t in secrets:
        if t:
            s = s.replace(t, "***")
    s = re.sub(r"(access_token|client_secret|refresh_token)=[^&\s\"']+", r"\1=***", s)
    return s.replace("\n", " ")[:300]


def http(method, url, headers=None, data=None, js=None, form=None, timeout=120):
    h = dict(headers or {})
    body = data
    if js is not None:
        body = json.dumps(js).encode()
        h.setdefault("Content-Type", "application/json; charset=UTF-8")
    if form is not None:
        body = urllib.parse.urlencode(form).encode()
        h.setdefault("Content-Type", "application/x-www-form-urlencoded")
    req = urllib.request.Request(url, data=body, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as f:
            raw = f.read()
            return f.status, dict(f.headers), raw
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read()
    except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
        raise Transient("network: " + str(e)[:100])


def jparse(raw):
    try:
        return json.loads(raw or b"{}")
    except Exception:
        return {}


def api_err(code, j, what):
    e = j.get("error") if isinstance(j, dict) else None
    if isinstance(e, dict):
        m = e.get("message") or e.get("error_user_msg") or json.dumps(e)[:200]
        sub = e.get("code")
    else:
        m = str(e or j)[:200]
        sub = None
    text = f"{what}: {code} {m}"
    if code in (429, 500, 502, 503, 504) or sub in (1, 2, 4, 17, 32, 341):
        raise Transient(text)
    raise Fatal(text)


# ───────────────────────── فيسبوك (صفحات) ─────────────────────────
def wait_fb_video(video_id, token, want=("ready", "complete", "processing"), tries=60):
    last = ""
    for _ in range(tries):
        c, _, raw = http("GET", f"{GRAPH}/{video_id}?fields=status&access_token={urllib.parse.quote(token)}")
        st = jparse(raw).get("status", {})
        last = json.dumps(st)[:160]
        up = (st.get("uploading_phase") or {}).get("status")
        if up == "complete" or st.get("video_status") in ("ready", "published"):
            return
        if up == "error" or st.get("video_status") == "error":
            raise Fatal("facebook upload: " + last)
        time.sleep(5)
    raise Transient("facebook upload slow: " + last)


def post_facebook(acc, path, caption):
    tok, pid = acc["token"], acc["ext_id"]
    c, _, raw = http("POST", f"{GRAPH}/{pid}/video_reels", form={"upload_phase": "start", "access_token": tok})
    j = jparse(raw)
    if c != 200 or "video_id" not in j:
        api_err(c, j, "fb start")
    vid, up = j["video_id"], j["upload_url"]
    size = os.path.getsize(path)
    with open(path, "rb") as f:
        data = f.read()
    c, _, raw = http("POST", up, headers={"Authorization": "OAuth " + tok, "offset": "0", "file_size": str(size)}, data=data, timeout=600)
    if c != 200:
        api_err(c, jparse(raw), "fb upload")
    wait_fb_video(vid, tok)
    form = {"upload_phase": "finish", "video_id": vid, "video_state": "PUBLISHED", "access_token": tok}
    if caption:
        form["description"] = caption
    c, _, raw = http("POST", f"{GRAPH}/{pid}/video_reels", form=form)
    j = jparse(raw)
    if c != 200 or not j.get("success", True):
        api_err(c, j, "fb finish")
    return vid


# ───────────────────────── إنستجرام (حساب احترافي مربوط بصفحة) ─────────────────────────
def post_instagram(acc, path, caption):
    tok, iid = acc["token"], acc["ext_id"]
    form = {"media_type": "REELS", "upload_type": "resumable", "access_token": tok}
    if caption:
        form["caption"] = caption
    c, _, raw = http("POST", f"{GRAPH}/{iid}/media", form=form)
    j = jparse(raw)
    if c != 200 or "id" not in j:
        api_err(c, j, "ig container")
    cid = j["id"]
    uri = j.get("uri") or f"https://rupload.facebook.com/ig-api-upload/v21.0/{cid}"
    size = os.path.getsize(path)
    with open(path, "rb") as f:
        data = f.read()
    c, _, raw = http("POST", uri, headers={"Authorization": "OAuth " + tok, "offset": "0", "file_size": str(size)}, data=data, timeout=600)
    if c != 200:
        api_err(c, jparse(raw), "ig upload")
    last = ""
    for _ in range(72):
        c, _, raw = http("GET", f"{GRAPH}/{cid}?fields=status_code,status&access_token={urllib.parse.quote(tok)}")
        j = jparse(raw)
        last = j.get("status_code", "") or last
        if last == "FINISHED":
            break
        if last in ("ERROR", "EXPIRED"):
            raise Fatal("ig processing: " + str(j.get("status", last))[:200])
        time.sleep(5)
    else:
        raise Transient("ig processing slow: " + last)
    c, _, raw = http("POST", f"{GRAPH}/{iid}/media_publish", form={"creation_id": cid, "access_token": tok})
    j = jparse(raw)
    if c != 200 or "id" not in j:
        api_err(c, j, "ig publish")
    return j["id"]


# ───────────────────────── يوتيوب (Shorts) ─────────────────────────
def refresh_google(acc, keys):
    k = keys.get("google") or {}
    c, _, raw = http("POST", "https://oauth2.googleapis.com/token",
                     form={"client_id": k.get("id", ""), "client_secret": k.get("secret", ""),
                           "refresh_token": acc.get("refresh", ""), "grant_type": "refresh_token"})
    j = jparse(raw)
    if c != 200 or "access_token" not in j:
        if c in (400, 401):
            raise Fatal("يوتيوب: الربط انتهى — اربط الحساب تاني من التطبيق")
        raise Transient("google refresh " + str(c))
    return j["access_token"], int(time.time()) + int(j.get("expires_in", 3600))


def post_youtube(acc, path, caption):
    tok = acc["token"]
    title = (caption.split("\n")[0] if caption else "Reel")[:90] or "Reel"
    desc = (caption + "\n\n#Shorts") if caption else "#Shorts"
    meta = {"snippet": {"title": title, "description": desc, "categoryId": "22"},
            "status": {"privacyStatus": "public", "selfDeclaredMadeForKids": False}}
    size = os.path.getsize(path)
    c, hd, raw = http("POST", "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status",
                      headers={"Authorization": "Bearer " + tok, "X-Upload-Content-Type": "video/mp4",
                               "X-Upload-Content-Length": str(size)}, js=meta)
    if c != 200:
        api_err(c, jparse(raw), "yt init")
    loc = {k.lower(): v for k, v in hd.items()}.get("location")
    with open(path, "rb") as f:
        data = f.read()
    c, _, raw = http("PUT", loc, headers={"Content-Type": "video/mp4"}, data=data, timeout=900)
    j = jparse(raw)
    if c not in (200, 201):
        api_err(c, j, "yt upload")
    return j.get("id", "")


# ───────────────────────── تيك توك ─────────────────────────
def refresh_tiktok(acc, keys):
    k = keys.get("tiktok") or {}
    c, _, raw = http("POST", "https://open.tiktokapis.com/v2/oauth/token/",
                     form={"client_key": k.get("id", ""), "client_secret": k.get("secret", ""),
                           "grant_type": "refresh_token", "refresh_token": acc.get("refresh", "")})
    j = jparse(raw)
    if c != 200 or "access_token" not in j:
        if c in (400, 401):
            raise Fatal("تيك توك: الربط انتهى — اربط الحساب تاني من التطبيق")
        raise Transient("tiktok refresh " + str(c))
    return j["access_token"], j.get("refresh_token", acc.get("refresh", "")), int(time.time()) + int(j.get("expires_in", 86400))


def post_tiktok(acc, path, caption):
    tok = acc["token"]
    h = {"Authorization": "Bearer " + tok}
    c, _, raw = http("POST", "https://open.tiktokapis.com/v2/post/publish/creator_info/query/", headers=h, js={})
    j = jparse(raw)
    if c != 200 or j.get("error", {}).get("code") not in (None, "ok"):
        api_err(c, {"error": j.get("error", {})}, "tt creator")
    opts = (j.get("data") or {}).get("privacy_level_options") or ["SELF_ONLY"]
    # حسابات التطبيقات اللي لسه ما اتراجعتش من تيك توك بتقبل «أنا فقط» بس؛ نختار الأعلى المتاح
    privacy = next((p for p in ("PUBLIC_TO_EVERYONE", "MUTUAL_FOLLOW_FRIENDS", "FOLLOWER_OF_CREATOR", "SELF_ONLY") if p in opts), opts[0])
    size = os.path.getsize(path)
    chunk = size if size <= 64 * 1024 * 1024 else 32 * 1024 * 1024
    total = max(1, math.ceil(size / chunk))
    body = {"post_info": {"title": (caption or "")[:2200], "privacy_level": privacy,
                          "disable_duet": False, "disable_comment": False, "disable_stitch": False},
            "source_info": {"source": "FILE_UPLOAD", "video_size": size, "chunk_size": chunk, "total_chunk_count": total}}
    c, _, raw = http("POST", "https://open.tiktokapis.com/v2/post/publish/video/init/", headers=h, js=body)
    j = jparse(raw)
    if c != 200 or j.get("error", {}).get("code") not in (None, "ok"):
        api_err(c, {"error": j.get("error", {})}, "tt init")
    up, pid = j["data"]["upload_url"], j["data"]["publish_id"]
    with open(path, "rb") as f:
        for i in range(total):
            part = f.read(chunk)
            a = i * chunk
            c, _, raw = http("PUT", up, headers={"Content-Type": "video/mp4", "Content-Length": str(len(part)),
                                                  "Content-Range": f"bytes {a}-{a + len(part) - 1}/{size}"}, data=part, timeout=600)
            if c not in (200, 201, 206):
                api_err(c, jparse(raw), "tt upload")
    last = ""
    for _ in range(36):
        c, _, raw = http("POST", "https://open.tiktokapis.com/v2/post/publish/status/fetch/", headers=h, js={"publish_id": pid})
        d = jparse(raw).get("data") or {}
        last = d.get("status", "")
        if last == "PUBLISH_COMPLETE":
            return pid
        if last == "FAILED":
            raise Fatal("tiktok: " + str(d.get("fail_reason", ""))[:120])
        time.sleep(5)
    raise Transient("tiktok processing slow: " + last)


POSTERS = {"facebook": post_facebook, "instagram": post_instagram, "youtube": post_youtube, "tiktok": post_tiktok}


# ───────────────────────── الجدولة ─────────────────────────
def sh(*a):
    return subprocess.run(a, capture_output=True, text=True)


def fetch_reel(job, fname, cache):
    key = (job, fname)
    if key in cache and os.path.exists(cache[key]):
        return cache[key]
    d = tempfile.mkdtemp(prefix="r_")
    r = sh("gh", "release", "download", "reels-" + job, "--pattern", fname, "--dir", d, "--clobber")
    p = os.path.join(d, fname)
    if r.returncode != 0 or not os.path.exists(p):
        raise Fatal("ملف الريل اتمسح من السيرفر (" + fname + ")")
    cache[key] = p
    return p


def enqueue(job, folder):
    repo, gh, app = os.environ["GH_REPO"], os.environ["GH_TOKEN"], os.environ["APP_TOKEN"]
    if not app:
        print("APP_TOKEN مش موجود — مفيش نشر.")
        return 0
    reels = sorted(os.path.basename(p) for p in glob.glob(os.path.join(folder, "reel_*.mp4")))
    if not reels:
        print("مفيش ريلز للنشر.")
        return 0
    v = Vault(repo, gh, app)

    def fn(data):
        if not (data.get("settings") or {}).get("auto_post"):
            print("النشر التلقائي مقفول — اتخطّينا.")
            return 0
        now = time.time()
        added = 0
        for acc in data.get("accounts", []):
            if not acc.get("enabled", True) or acc.get("platform") not in SUPPORTED:
                continue
            start = max(0.0, float(acc.get("start_h", 0))) * 3600
            step = max(0.05, float(acc.get("step_h", 1))) * 3600
            mine = [q for q in data["queue"] if q["acc"] == acc["id"] and q["state"] in ("pending", "publishing", "done")]
            t = now + start
            if mine:
                t = max(t, max(q["due"] for q in mine) + step)
            for i, f in enumerate(reels):
                data["queue"].append({"id": uuid.uuid4().hex[:10], "acc": acc["id"], "job": job, "file": f,
                                      "due": int(t), "state": "pending", "tries": 0, "idx": i + 1, "of": len(reels)})
                t += step
                added += 1
        # نظّف الأقدم من 14 يوم
        cut = now - 14 * 86400
        data["queue"] = [q for q in data["queue"] if q["state"] in ("pending", "publishing") or q.get("ts", q["due"]) > cut]
        return added

    n = v.mutate(fn)
    print(f"اتضاف {n} منشور للجدول.")
    return n


def run():
    repo, gh, app = os.environ["GH_REPO"], os.environ["GH_TOKEN"], os.environ["APP_TOKEN"]
    if not app:
        print("APP_TOKEN مش موجود.")
        return
    v = Vault(repo, gh, app)
    data = v.load()
    now = time.time()
    # منشورات علّقت من تشغيل اتقتل
    stale = [q["id"] for q in data["queue"] if q["state"] == "publishing" and now - q.get("ts", 0) > STALE_SEC]
    if stale:
        def unstick(d):
            for q in d["queue"]:
                if q["id"] in stale and q["state"] == "publishing":
                    q["state"], q["err"], q["ts"] = "failed", "اتقطع وسط النشر — اتأكد من الصفحة قبل ما تعيد", int(time.time())
        v.mutate(unstick)
        data = v.load()
    due = sorted([q for q in data["queue"] if q["state"] == "pending" and q["due"] <= now], key=lambda q: q["due"])
    if not due:
        print("مفيش حاجة معادها جه.")
        return
    print(f"{len(due)} منشور جاهز للنشر.")
    cache = {}
    deadline = time.time() + 24 * 60
    for q in due:
        if time.time() > deadline:
            print("خلص وقت التشغيل — الباقي في الدور الجاي.")
            break
        accs = {a["id"]: a for a in data["accounts"]}
        acc = accs.get(q["acc"])
        if not acc or not acc.get("enabled", True):
            finish(v, q["id"], "failed", "الحساب اتشال أو اتقفل")
            continue
        mark(v, q["id"], "publishing")
        try:
            acc = ensure_token(v, acc, data.get("keys", {}))
            path = fetch_reel(q["job"], q["file"], cache)
            cap = (acc.get("desc") or "").strip()
            POSTERS[acc["platform"]](acc, path, cap)
            finish(v, q["id"], "done", "")
            print(f"✅ {acc['platform']} {q['file']}")
        except Fatal as e:
            finish(v, q["id"], "failed", clean(e, (acc.get("token"), acc.get("refresh"))))
            print(f"❌ {acc['platform']} {q['file']}: {clean(e, (acc.get('token'),))}")
        except Transient as e:
            tries = q.get("tries", 0) + 1
            if tries >= MAX_TRIES:
                finish(v, q["id"], "failed", "فشل بعد " + str(MAX_TRIES) + " محاولات: " + clean(e, (acc.get("token"),)))
            else:
                retry_later(v, q["id"], tries, clean(e, (acc.get("token"),)))
            print(f"⏳ {acc['platform']} {q['file']}: {clean(e, (acc.get('token'),))}")
        except Exception as e:
            finish(v, q["id"], "failed", "خطأ: " + clean(e, (acc.get("token"),)))
        # فاصل بسيط بين المنشورات عشان نتجنب حدود المعدّل
        time.sleep(3)


def mark(v, qid, state):
    def fn(d):
        for q in d["queue"]:
            if q["id"] == qid:
                q["state"], q["ts"] = state, int(time.time())
    v.mutate(fn)


def finish(v, qid, state, err):
    def fn(d):
        for q in d["queue"]:
            if q["id"] == qid:
                q["state"], q["err"], q["ts"] = state, err, int(time.time())
    v.mutate(fn)


def retry_later(v, qid, tries, err):
    def fn(d):
        for q in d["queue"]:
            if q["id"] == qid:
                q["state"], q["tries"], q["err"] = "pending", tries, err
                q["due"] = int(time.time()) + RETRY_MIN * 60
    v.mutate(fn)


def ensure_token(v, acc, keys):
    """تجديد توكن يوتيوب/تيك توك قبل ما ينتهي، وحفظ الجديد في الخزنة."""
    exp = acc.get("expires", 0)
    if acc["platform"] not in ("youtube", "tiktok") or not exp or exp - time.time() > 300:
        return acc
    if acc["platform"] == "youtube":
        tok, exp2 = refresh_google(acc, keys)
        ref = acc.get("refresh")
    else:
        tok, ref, exp2 = refresh_tiktok(acc, keys)

    def fn(d):
        for a in d["accounts"]:
            if a["id"] == acc["id"]:
                a["token"], a["expires"], a["refresh"] = tok, exp2, ref
    v.mutate(fn)
    acc = dict(acc, token=tok, expires=exp2, refresh=ref)
    return acc


def pinned():
    repo, gh, app = os.environ["GH_REPO"], os.environ["GH_TOKEN"], os.environ["APP_TOKEN"]
    if not app:
        return
    try:
        data = Vault(repo, gh, app).load()
    except Exception:
        return
    for j in sorted({q["job"] for q in data["queue"] if q["state"] in ("pending", "publishing")}):
        print("reels-" + j)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "enqueue":
        enqueue(sys.argv[2], sys.argv[3])
    elif cmd == "run":
        run()
    elif cmd == "pinned":
        pinned()
    else:
        print(__doc__)
