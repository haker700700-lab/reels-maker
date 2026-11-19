# -*- coding: utf-8 -*-
"""
محرك صانع الريلز — بيشتغل على الموبايل نفسه (Termux) بدون أي سيرفر خارجي.
الأدوات كلها مفتوحة المصدر: yt-dlp • FFmpeg • Python • Pillow.
"""
import os, re, sys, json, glob, math, time, shutil, subprocess, unicodedata, datetime, threading, zipfile
import urllib.parse, urllib.request

try:
    import numpy as np
except Exception:  # الاختيار الذكي بيتحوّل لتوزيع متساوي لو numpy مش موجود
    np = None

ROOT = os.path.dirname(os.path.abspath(__file__))
WORK = os.path.join(ROOT, "work")
OUT = os.path.join(ROOT, "output")
UPLOADS = os.path.join(ROOT, "uploads")
CACHE = os.path.join(ROOT, "cache")
FONT_DIR = os.path.join(ROOT, "fonts")


def init_dirs():
    for d in (WORK, OUT, UPLOADS, CACHE, FONT_DIR):
        os.makedirs(d, exist_ok=True)


# ════════════════════════════════════════════════════════════
#  الحالة المشتركة مع السيرفر (سجل الرسائل + التقدّم + النتيجة)
# ════════════════════════════════════════════════════════════
_LOCK = threading.RLock()
STATE = {"state": "idle", "job": 0, "logs": [], "msg": "", "pct": 0.0, "clips": [], "saved_to": "", "zip": ""}


def snapshot(since=0, job=None):
    with _LOCK:
        if job is not None and str(job) != str(STATE["job"]):
            since = 0
        return {"state": STATE["state"], "job": STATE["job"], "logs": STATE["logs"][since:],
                "n": len(STATE["logs"]), "msg": STATE["msg"], "pct": STATE["pct"],
                "clips": STATE["clips"], "saved_to": STATE["saved_to"], "zip": STATE["zip"]}


def say(msg, kind="info"):
    with _LOCK:
        STATE["logs"].append({"k": kind, "m": str(msg)})
    print(f"[{kind}] {msg}", flush=True)


class UserError(Exception):
    """خطأ مفهوم للمستخدم (بنعرضه برسالة واضحة بدل Traceback)."""


class Cancelled(BaseException):
    """المستخدم ضغط إلغاء. BaseException عشان ما يتبلعش في أي except Exception."""


CANCEL = threading.Event()
_PROCS = set()
_PLOCK = threading.Lock()
_JOB = {"t": None}


def _is_job():
    return threading.current_thread() is _JOB["t"]


def _chk():
    # الإلغاء بيخص خيط المهمة بس (مش طلبات الواجهة التانية)
    if CANCEL.is_set() and _is_job():
        raise Cancelled()


def kill_all():
    with _PLOCK:
        ps = list(_PROCS)
    for p in ps:
        try:
            p.kill()
        except Exception:
            pass


def cancel():
    CANCEL.set()
    kill_all()


class Progress:
    def __init__(self):
        self.lo, self.hi = 0.0, 100.0

    def stage(self, lo, hi):
        self.lo, self.hi = float(lo), float(hi)

    def update(self, msg, force=False, frac=None):
        with _LOCK:
            STATE["msg"] = str(msg)
            if frac is not None:
                STATE["pct"] = round(self.lo + (self.hi - self.lo) * max(0.0, min(1.0, frac)), 1)

    def reset(self):
        pass


PROG = Progress()


# ════════════════════════════════════════════════════════════
#  أدوات عامة
# ════════════════════════════════════════════════════════════
def to_int(v, d, lo, hi):
    try:
        x = int(float(v))
    except Exception:
        x = d
    return max(lo, min(hi, x))


def to_float(v, d):
    try:
        return float(v)
    except Exception:
        return d


class _R:
    def __init__(self, rc, out="", err=""):
        self.returncode, self.stdout, self.stderr = rc, out, err


def run(cmd, timeout=None, **kw):
    _chk()
    try:
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, errors="replace", **kw)
    except OSError as e:
        return _R(127, "", str(e))
    if _is_job():
        with _PLOCK:
            _PROCS.add(p)
    try:
        out, err = p.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        p.kill()
        out, err = p.communicate()
        return _R(124, out, err)
    finally:
        with _PLOCK:
            _PROCS.discard(p)
    _chk()
    return _R(p.returncode, out, err)


def run_stream(cmd, on_line=None, timeout=None):
    _chk()
    try:
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                             errors="replace", bufsize=1)
    except OSError as e:
        return 127, str(e)
    if _is_job():
        with _PLOCK:
            _PROCS.add(p)
    tail, t0 = [], time.time()
    try:
        for line in p.stdout:
            line = line.rstrip()
            tail.append(line)
            tail = tail[-60:]
            if on_line:
                on_line(line)
            if timeout and time.time() - t0 > timeout:
                p.kill()
                break
    finally:
        p.wait()
        with _PLOCK:
            _PROCS.discard(p)
    _chk()
    return p.returncode, "\n".join(tail)


def fmt_t(sec):
    sec = int(max(0, sec))
    h, r = divmod(sec, 3600)
    m, s = divmod(r, 60)
    return f"{h:d}:{m:02d}:{s:02d}" if h else f"{m:02d}:{s:02d}"


UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
      "Chrome/126.0.0.0 Safari/537.36")
MEDIA_EXTS = ("mp4", "m4v", "mov", "mkv", "webm", "avi", "flv", "mpg", "mpeg", "3gp", "wmv", "ogv", "m3u8", "mpd")
SITES_FIRST = ("youtube.com", "youtu.be", "tiktok.com", "instagram.com", "facebook.com", "fb.watch", "fb.com",
               "twitter.com", "x.com", "vimeo.com", "dailymotion.com", "twitch.tv", "reddit.com", "snapchat.com",
               "pinterest.", "streamable.com", "bilibili.com", "rumble.com", "ok.ru", "vk.com", "linkedin.com",
               "kick.com", "likee.video", "threads.net", "t.me")


# ════════════════════════════════════════════════════════════
#  فحص الفيديو (ffprobe)
# ════════════════════════════════════════════════════════════
def probe(path):
    p = run(["ffprobe", "-v", "error", "-print_format", "json", "-show_format", "-show_streams", path])
    try:
        j = json.loads(p.stdout or "{}")
    except Exception:
        return None
    streams = j.get("streams", []) or []
    v = next((s for s in streams if s.get("codec_type") == "video"
              and not (s.get("disposition") or {}).get("attached_pic")), None)
    has_a = any(s.get("codec_type") == "audio" for s in streams)
    dur = None
    for cand in ((j.get("format") or {}).get("duration"), (v or {}).get("duration")):
        try:
            d = float(cand)
            if d > 0:
                dur = d
                break
        except Exception:
            pass
    w = h = None
    if v:
        w, h = v.get("width"), v.get("height")
        rot = 0
        try:
            rot = int((v.get("tags") or {}).get("rotate", 0))
        except Exception:
            pass
        for sd in v.get("side_data_list", []) or []:
            if "rotation" in sd:
                try:
                    rot = int(float(sd["rotation"]))
                except Exception:
                    pass
        if w and h and abs(rot) % 180 == 90:
            w, h = h, w
    return {"has_video": bool(v), "has_audio": has_a, "dur": dur, "w": w, "h": h}


def validate_media(path, need_video=True, work=None):
    """يرجّع (path, info) لو الملف سليم، وإلا None. بيحاول يصلّح الملفات اللي مدتها مش معروفة."""
    if not path or not os.path.exists(path) or os.path.getsize(path) < 2048:
        return None
    info = probe(path)
    if not info:
        return None
    if need_video and not info["has_video"]:
        return None
    if not need_video and not info["has_audio"]:
        return None
    if not info["dur"]:
        fixed = path + ".fixed.mp4"
        r = run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", path, "-c", "copy", fixed])
        if r.returncode == 0 and os.path.exists(fixed):
            info2 = probe(fixed)
            if info2 and info2["dur"]:
                os.replace(fixed, path)
                info = info2
        if os.path.exists(fixed):
            os.remove(fixed)
    if not info["dur"]:
        return None
    return path, info


# ════════════════════════════════════════════════════════════
#  تنظيف الرابط وتجهيز الترويسات
# ════════════════════════════════════════════════════════════
def extract_url(raw):
    raw = (raw or "").strip()
    raw = raw.replace("\\/", "/").replace("&amp;", "&").replace("\\u0026", "&")
    m = re.search(r"https?://[^\s\"'<>]+", raw)
    if m:
        u = m.group(0)
    elif re.match(r"^[\w.-]+\.[a-z]{2,}(/|$)", raw, re.I) and " " not in raw:
        u = "https://" + raw
    else:
        return None
    u = u.rstrip(").,،;]}»”’")
    return urllib.parse.quote(u, safe=":/?#[]@!$&'()*+,;=%~-._")


def normalize_url(u):
    p = urllib.parse.urlparse(u)
    host = p.netloc.lower()
    if "dropbox.com" in host:
        q = [(k, v) for k, v in urllib.parse.parse_qsl(p.query, keep_blank_values=True) if k not in ("dl", "raw")]
        q.append(("dl", "1"))
        return urllib.parse.urlunparse(p._replace(query=urllib.parse.urlencode(q)))
    if "pixeldrain.com" in host:
        m = re.search(r"/u/([A-Za-z0-9]+)", p.path)
        if m:
            return f"https://pixeldrain.com/api/file/{m.group(1)}"
    if host == "github.com" and "/blob/" in p.path:
        return urllib.parse.urlunparse(p._replace(path=p.path.replace("/blob/", "/raw/", 1)))
    return u


def parse_extra_headers(s):
    out = {}
    for part in re.split(r"\s*(?:\||\n)\s*", (s or "").strip()):
        if ":" in part:
            k, v = part.split(":", 1)
            if k.strip():
                out[k.strip()] = v.strip()
    return out


def header_variants(url, referer, cookie, extra):
    p = urllib.parse.urlparse(url)
    origin = f"{p.scheme}://{p.netloc}/"

    def mk(ref):
        h = {"User-Agent": UA, "Accept": "*/*", "Accept-Language": "ar,en;q=0.9"}
        if ref:
            h["Referer"] = ref
        if cookie:
            h["Cookie"] = cookie
        h.update(extra)
        return h

    return [mk(referer)] if referer else [mk(None), mk(origin)]


def ext_from(url, ctype="", cdisp=""):
    m = re.search(r'filename\*?=(?:UTF-8\'\')?"?([^";]+)', cdisp or "", re.I)
    if m:
        e = os.path.splitext(urllib.parse.unquote(m.group(1)))[1].lower().lstrip(".")
        if e in MEDIA_EXTS:
            return e
    ct = (ctype or "").lower()
    for k, e in (("mp4", "mp4"), ("webm", "webm"), ("quicktime", "mov"), ("matroska", "mkv"),
                 ("x-msvideo", "avi"), ("mpeg", "mpg"), ("3gpp", "3gp")):
        if k in ct and ("video" in ct or "audio" in ct or "application" in ct):
            return e
    e = os.path.splitext(urllib.parse.urlparse(url).path)[1].lower().lstrip(".")
    return e if e in MEDIA_EXTS else "mp4"


def sniff_kind(head, ctype):
    t = head.lstrip(b"\xef\xbb\xbf \t\r\n")[:2500].lower()
    if t.startswith(b"#extm3u"):
        return "m3u8"
    if t.startswith(b"<"):
        return "mpd" if b"<mpd" in t else "html"
    if t.startswith((b"{", b"[")):
        return "json"
    ct = (ctype or "").lower()
    if ("text/" in ct or "json" in ct or "javascript" in ct) and b"\x00" not in t[:200]:
        return "html"
    return "media"


# ════════════════════════════════════════════════════════════
#  التحميل المباشر (requests)
# ════════════════════════════════════════════════════════════
def http_get(url, headers, timeout=30):
    import requests
    try:
        return requests.get(url, headers=headers, stream=True, timeout=timeout, allow_redirects=True)
    except requests.exceptions.SSLError:
        try:
            requests.packages.urllib3.disable_warnings()
        except Exception:
            pass
        return requests.get(url, headers=headers, stream=True, timeout=timeout, allow_redirects=True, verify=False)


def try_direct(url, headers, base, prog):
    """يرجّع dict: kind = media | html | json | m3u8 | mpd | error"""
    last = "خطأ غير معروف"
    for attempt in range(3):
        _chk()
        try:
            r = http_get(url, headers)
        except Exception as e:
            last = f"تعذّر الاتصال بالرابط ({e.__class__.__name__})"
            time.sleep(1.5)
            continue
        try:
            if r.status_code >= 400:
                return {"kind": "error", "msg": f"السيرفر رد بكود HTTP {r.status_code}", "code": r.status_code}
            ctype = r.headers.get("Content-Type", "")
            it = r.iter_content(chunk_size=1 << 16)
            first = next(it, b"")
            kind = sniff_kind(first, ctype)
            if kind != "media":
                buf = [first]
                size = len(first)
                for ch in it:
                    buf.append(ch)
                    size += len(ch)
                    if size > 4_000_000:
                        break
                text = b"".join(buf).decode(r.encoding or "utf-8", errors="replace")
                return {"kind": kind, "text": text, "final": r.url}
            dest = f"{base}.{ext_from(r.url, ctype, r.headers.get('Content-Disposition', ''))}"
            total = int(r.headers.get("Content-Length", 0) or 0)
            got, t0 = 0, time.time()
            with open(dest, "wb") as f:
                f.write(first)
                got += len(first)
                for ch in it:
                    _chk()
                    f.write(ch)
                    got += len(ch)
                    if total:
                        sp = got / max(1e-6, time.time() - t0) / 1048576
                        prog.update(f"تحميل مباشر: {got/1048576:.0f}/{total/1048576:.0f} MB ({got*100//total}%) — {sp:.1f} MB/s",
                                    frac=got / total)
                    else:
                        prog.update(f"تحميل مباشر: {got/1048576:.0f} MB")
            if total and got < total * 0.98:
                last = "انقطع التحميل قبل ما يكمل"
                continue
            return {"kind": "media", "path": dest}
        except Cancelled:
            raise
        except Exception as e:
            last = f"انقطع التحميل ({e.__class__.__name__})"
            time.sleep(1.5)
        finally:
            try:
                r.close()
            except Exception:
                pass
    return {"kind": "error", "msg": last}


# ════════════════════════════════════════════════════════════
#  yt-dlp و ffmpeg
# ════════════════════════════════════════════════════════════
_YT_OK = None


def have_ytdlp():
    global _YT_OK
    if _YT_OK:
        return True
    _YT_OK = run([sys.executable, "-m", "yt_dlp", "--version"]).returncode == 0
    return _YT_OK


def ytdlp_version():
    r = run([sys.executable, "-m", "yt_dlp", "--version"])
    return (r.stdout or "").strip() if r.returncode == 0 else ""


def update_ytdlp():
    global _YT_OK
    r = run([sys.executable, "-m", "pip", "install", "-U", "--no-input", "-q", "yt-dlp"], timeout=240)
    _YT_OK = None
    v = ytdlp_version()
    return (r.returncode == 0 and bool(v)), (v or (r.stderr or "")[-300:])


def _has_curl_cffi():
    try:
        import curl_cffi  # noqa: F401
        return True
    except Exception:
        return False


def ytdlp_download(url, outbase, referer, cookie, extra, cookies_file, prog, audio_only=False, maxh=1080):
    """يرجّع (path|None, آخر خطأ). بيجرّب كذا أسلوب بالترتيب."""
    if not have_ytdlp():
        return None, "yt-dlp غير مثبّت"
    base = [sys.executable, "-m", "yt_dlp", "--no-playlist", "--newline", "--no-colors", "--ignore-config",
            "--retries", "5", "--fragment-retries", "10", "--socket-timeout", "30",
            "--concurrent-fragments", "16", "--http-chunk-size", "10M", "--user-agent", UA, "--no-check-certificates",
            "-o", outbase + ".%(ext)s"]
    if not audio_only:
        base += ["--merge-output-format", "mp4", "-S", "res,vcodec:h264,acodec:m4a"]
    if referer:
        base += ["--referer", referer]
    if cookie:
        base += ["--add-header", f"Cookie:{cookie}"]
    for k, v in extra.items():
        base += ["--add-header", f"{k}:{v}"]
    if cookies_file:
        base += ["--cookies", cookies_file]
    fmt = "bestaudio/best" if audio_only else f"bv*[height<={maxh}]+ba/b[height<={maxh}]/bv*+ba/b"
    variants = [["-f", fmt]]
    if _has_curl_cffi():
        variants.append(["-f", fmt, "--impersonate", "chrome"])
    if shutil.which("node"):
        variants.append(["-f", fmt, "--js-runtimes", "node"])
    variants += [[], ["--use-extractors", "generic"]]
    last = ""
    for var in variants:
        for f in glob.glob(outbase + ".*"):
            try:
                os.remove(f)
            except Exception:
                pass

        def on_line(line):
            if "[download]" in line and "%" in line:
                m = re.search(r"(\d+(?:\.\d+)?)%", line)
                prog.update("yt-dlp " + line.replace("[download]", "").strip(),
                            frac=(float(m.group(1)) / 100 if m else None))
            elif "[Merger]" in line or "[ExtractAudio]" in line:
                prog.update("بيتم دمج الصوت والصورة...", force=True)

        rc, tail = run_stream(base + var + [url], on_line)
        files = [f for f in glob.glob(outbase + ".*")
                 if not f.endswith((".part", ".ytdl", ".temp", ".json", ".fixed.mp4"))]
        if rc == 0 and files:
            return max(files, key=os.path.getsize), ""
        last = tail
        low = tail.lower()
        if "unsupported url" in low:
            break
        if any(x in low for x in ("private video", "video unavailable", "has been removed", "drm")):
            break
    return None, last


def ytdlp_duration(url, cookies_file="", timeout=45):
    """مدة الفيديو بالثواني من غير تحميل (عشان نقرر الجودة للفيديوهات الطويلة). None لو مش معروفة."""
    if not have_ytdlp():
        return None
    cmd = [sys.executable, "-m", "yt_dlp", "--no-playlist", "--no-warnings", "--ignore-config", "--skip-download",
           "--socket-timeout", "20", "--print", "duration", "--user-agent", UA]
    if cookies_file:
        cmd += ["--cookies", cookies_file]
    r = run(cmd + [url], timeout=timeout)
    if r.returncode != 0:
        return None
    for line in (r.stdout or "").splitlines():
        try:
            v = float(line.strip())
            if v > 0:
                return v
        except ValueError:
            continue
    return None


def ffmpeg_stream(url, headers, out, prog):
    hs = "".join(f"{k}: {v}\r\n" for k, v in headers.items() if k.lower() != "user-agent")
    head = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-stats", "-nostdin", "-y", "-user_agent", UA,
            "-headers", hs, "-protocol_whitelist", "file,http,https,tcp,tls,crypto", "-i", url]
    tries = [head + ["-c", "copy", "-bsf:a", "aac_adtstoasc", "-movflags", "+faststart", out],
             head + ["-c", "copy", out.rsplit(".", 1)[0] + ".mkv"],
             head + ["-c:v", "libx264", "-preset", "veryfast", "-crf", "22", "-c:a", "aac", out]]
    last = ""
    for cmd in tries:
        dest = cmd[-1]

        def on_line(line):
            m = re.search(r"time=(\d+:\d+:\d+)", line)
            if m:
                prog.update(f"ffmpeg بيسحب البث... وصل لـ {m.group(1)}")

        rc, tail = run_stream(cmd, on_line)
        if rc == 0 and os.path.exists(dest) and os.path.getsize(dest) > 2048:
            return dest, ""
        last = tail
    return None, last


# ════════════════════════════════════════════════════════════
#  استخراج روابط الفيديو من صفحات الويب و JSON
# ════════════════════════════════════════════════════════════
_EXT = r"(?:mp4|m3u8|webm|mov|mkv|m4v|mpd)"
RE_ABS = re.compile(r"(?:https?:)?//[^\s\"'<>\\)]+?\." + _EXT + r"(?:\?[^\s\"'<>\\)]*)?", re.I)
RE_REL = re.compile(r"[\"'](/?[^\"'\s<>]+?\." + _EXT + r"(?:\?[^\"'\s<>]*)?)[\"']", re.I)
RE_TAG = re.compile(r"<(?:video|source)[^>]*?\ssrc=[\"']([^\"']+)[\"']", re.I)
RE_OG = re.compile(r"<meta[^>]+(?:property|name)=[\"'](?:og:video(?::secure_url|:url)?|twitter:player:stream)[\"'][^>]+content=[\"']([^\"']+)[\"']", re.I)
RE_OG2 = re.compile(r"<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"'](?:og:video(?::secure_url|:url)?|twitter:player:stream)[\"']", re.I)
RE_KEY = re.compile(r"[\"'](?:contentUrl|video_url|videoUrl|playbackUrl|hlsUrl|hls_url|download_url|downloadUrl|"
                    r"stream_url|streamUrl|mp4_url|mp4Url|file|src|source|url|link|video|hls|mp4)[\"']\s*[:=]\s*[\"'](https?:[^\"']+)[\"']", re.I)
RE_IFRAME = re.compile(r"<iframe[^>]+src=[\"']([^\"']+)[\"']", re.I)
BAD_WORDS = ("thumb", "poster", "preview", "sprite", "trailer", "/ads/", "doubleclick", "tracking", "pixel", "logo", "intro_")


def sniff_candidates(text, base_url):
    t = text.replace("\\/", "/").replace("\\u0026", "&").replace("\\u003d", "=").replace("&amp;", "&")
    found = []

    def add(u, bonus=0, strong=False):
        u = u.strip()
        if not u or u.startswith(("data:", "blob:", "javascript:")):
            return
        if u.startswith("//"):
            u = "https:" + u
        u = urllib.parse.urljoin(base_url, u)
        path = urllib.parse.urlparse(u).path.lower()
        ext = path.rsplit(".", 1)[-1] if "." in path else ""
        has_ext = ext in MEDIA_EXTS
        if not has_ext and not strong:
            return
        sc = bonus
        sc += 30 if ext in ("mp4", "webm", "mov", "mkv", "m4v") else 20 if ext == "m3u8" else 10 if ext == "mpd" else 0
        for q, b in (("1080", 8), ("720", 6), ("480", 3), ("master", 2), ("index", 1), ("download", 2)):
            if q in u.lower():
                sc += b
        if any(w in u.lower() for w in BAD_WORDS):
            sc -= 25
        found.append((sc, u))

    for m in RE_OG.findall(t) + RE_OG2.findall(t):
        add(m, 6, True)
    for m in RE_TAG.findall(t):
        add(m, 4, True)
    for m in RE_ABS.findall(t):
        add(m)
    for m in RE_REL.findall(t):
        add(m)
    for m in RE_KEY.findall(t):
        add(m, 0, True)
    seen, out = set(), []
    for sc, u in sorted(found, key=lambda x: -x[0]):
        if u not in seen:
            seen.add(u)
            out.append(u)
    frames = []
    for m in RE_IFRAME.findall(t):
        u = urllib.parse.urljoin(base_url, m)
        if u.startswith("http") and not any(x in u for x in ("doubleclick", "googletag", "facebook.com/plugins", "google.com/recaptcha")):
            frames.append(u)
    return out[:12], frames[:3]


def browser_sniff(url, prog):
    """متصفح حقيقي على السيرفر: يفتح الصفحة ويلقط روابط الفيديو الفعلية (والتوكن بتاعها بيتولّد من نفس السيرفر)."""
    try:
        from playwright.sync_api import sync_playwright
    except Exception:
        prog.update("بجهّز متصفح السيرفر (مرة واحدة)...", force=True)
        run([sys.executable, "-m", "pip", "install", "-q", "playwright"], timeout=240)
        run([sys.executable, "-m", "playwright", "install", "--with-deps", "chromium"], timeout=600)
        try:
            from playwright.sync_api import sync_playwright
        except Exception:
            return []
    found, seen, cookies = [], set(), ""
    try:
        with sync_playwright() as p:
            b = p.chromium.launch(args=["--no-sandbox", "--autoplay-policy=no-user-gesture-required"])
            ctx = b.new_context(user_agent=UA, viewport={"width": 1280, "height": 720})
            pg = ctx.new_page()

            def add(u):
                if u and u.startswith("http") and u not in seen and not any(w in u.lower() for w in BAD_WORDS):
                    seen.add(u)
                    found.append(u)

            def on_resp(r):
                try:
                    u, ct = r.url, (r.headers.get("content-type") or "").lower()
                    path = urllib.parse.urlparse(u).path.lower()
                    if path.endswith((".m3u8", ".mpd", ".mp4", ".webm", ".m4v", ".mov")) or "video/" in ct \
                            or "mpegurl" in ct or "dash+xml" in ct:
                        add(u)
                except Exception:
                    pass

            pg.on("response", on_resp)
            prog.update("بفتح الصفحة في متصفح السيرفر...", force=True)
            try:
                pg.goto(url, wait_until="domcontentloaded", timeout=45000)
            except Exception:
                pass
            for _ in range(6):
                pg.wait_for_timeout(2500)
                for sel in ("video", ".vjs-big-play-button", "[class*=play]", "button[aria-label*=lay]"):
                    try:
                        pg.click(sel, timeout=800)
                    except Exception:
                        pass
                try:
                    pg.evaluate("document.querySelectorAll('video').forEach(v=>{v.muted=true;v.play().catch(()=>{})})")
                    for s in pg.evaluate("[...document.querySelectorAll('video,source')].map(e=>e.currentSrc||e.src)"):
                        add(s)
                except Exception:
                    pass
                if found:
                    break
            cookies = "; ".join(f"{c['name']}={c['value']}" for c in ctx.cookies())
            b.close()
    except Exception:
        pass
    hd = {"Referer": url, "User-Agent": UA}
    if cookies:
        hd["Cookie"] = cookies
    return [(u, hd) for u in found[:6]]


# ════════════════════════════════════════════════════════════
#  المحمّل الشامل: أي رابط فيه فيديو
# ════════════════════════════════════════════════════════════
def fetch_media(raw, work, name, opts, prog, need_video=True):
    """يرجّع (path, info, is_temp). بيرفع UserError برسالة واضحة لو فشل."""
    raw = (raw or "").strip().strip("\"'")
    local = raw[7:] if raw.startswith("file://") else raw
    local = os.path.expanduser(local)
    if local and os.path.isfile(local):
        v = validate_media(local, need_video)
        if v:
            return v[0], v[1], False
        raise UserError("الملف موجود لكنه مش فيديو صالح (أو تالف).")
    url0 = extract_url(raw)
    if not url0:
        raise UserError("مش لاقي رابط صالح. الصق رابط كامل يبدأ بـ https:// أو اختر فيديو من جهازك.")
    url = normalize_url(url0)
    host = urllib.parse.urlparse(url).netloc.lower()
    ref, cookie, extra, cfile = opts["referer"], opts["cookie"], opts["extra"], opts["cookies_file"]
    maxh = opts.get("maxh", 1080)
    hv = header_variants(url, ref, cookie, extra)
    base = os.path.join(work, name)
    errors = []

    def ytd(u, outb, referer, audio_only=False):
        return ytdlp_download(u, outb, referer, cookie, extra, cfile, prog, audio_only=audio_only, maxh=maxh)

    def accept(path):
        if not path:
            return None
        v = validate_media(path, need_video)
        if v:
            return v
        errors.append("اتحمّل ملف لكنه مش فيديو/صوت صالح")
        try:
            os.remove(path)
        except Exception:
            pass
        return None

    def done(v):
        prog.update("تم التحميل ✓", force=True)
        return v[0], v[1], True

    # 1) Google Drive
    if "drive.google.com" in host or "docs.google.com" in host:
        m = re.search(r"/d/([\w-]+)", url) or re.search(r"[?&]id=([\w-]+)", url)
        if m:
            prog.update("تحميل من Google Drive...", force=True)
            try:
                import gdown
                p = gdown.download(f"https://drive.google.com/uc?id={m.group(1)}", base + ".mp4", quiet=True)
                v = accept(p)
                if v:
                    return done(v)
            except Exception as e:
                errors.append(f"gdown: {str(e)[:150]}")

    html_text, html_final, stream_kind = None, url, None

    def do_ytdlp():
        prog.update("جاري التحميل بـ yt-dlp...", force=True)
        p, err = ytd(url, base, ref, audio_only=not need_video)
        if p:
            return accept(p)
        errors.append("yt-dlp: " + err[-400:])
        return None

    def do_direct():
        nonlocal html_text, html_final, stream_kind
        for h in hv:
            prog.update("جاري التحميل المباشر...", force=True)
            res = try_direct(url, h, base, prog)
            if res["kind"] == "media":
                v = accept(res["path"])
                if v:
                    return v
            elif res["kind"] in ("html", "json", "m3u8", "mpd"):
                html_text, html_final, stream_kind = res["text"], res["final"], res["kind"]
                return None
            else:
                errors.append(res["msg"])
        return None

    sites_first = any(host == s or host.endswith("." + s) or host.endswith(s) for s in SITES_FIRST)
    order = [do_ytdlp, do_direct] if sites_first else [do_direct, do_ytdlp]
    for fn in order:
        v = fn()
        if v:
            return done(v)

    # 2) بث HLS/DASH مباشر بـ ffmpeg
    if stream_kind in ("m3u8", "mpd") or urllib.parse.urlparse(url).path.lower().endswith((".m3u8", ".mpd")):
        prog.update("جاري سحب البث بـ ffmpeg...", force=True)
        p, err = ffmpeg_stream(url, hv[-1], base + "_hls.mp4", prog)
        v = accept(p)
        if v:
            return done(v)
        errors.append("ffmpeg: " + err[-300:])

    # 3) تفتيش الصفحة / JSON عن روابط فيديو (وإطارات iframe)
    if html_text is None:
        for h in hv:
            try:
                r = http_get(url, h)
                html_text = r.text[:4_000_000] if r.status_code < 400 else None
                html_final = r.url
                r.close()
                if html_text:
                    break
            except Exception:
                pass
    tried = set()

    def sniff(text, page_url, depth):
        cands, frames = sniff_candidates(text, page_url)
        for cu in cands:
            if cu in tried or len(tried) >= 14:
                continue
            tried.add(cu)
            prog.update(f"لقيت رابط فيديو داخل الصفحة، بجرّبه: {cu[:90]}", force=True)
            low = urllib.parse.urlparse(cu).path.lower()
            chdrs = header_variants(cu, page_url, cookie, extra)
            if low.endswith((".m3u8", ".mpd")):
                p, err = ytd(cu, base + "_s", page_url)
                v = accept(p)
                if not v:
                    p, err = ffmpeg_stream(cu, chdrs[0], base + "_s.mp4", prog)
                    v = accept(p)
                if v:
                    return v
                continue
            for h in chdrs:
                res = try_direct(cu, h, base + "_s", prog)
                if res["kind"] == "media":
                    v = accept(res["path"])
                    if v:
                        return v
                    break
                if res["kind"] in ("m3u8", "mpd"):
                    p, err = ffmpeg_stream(cu, h, base + "_s.mp4", prog)
                    v = accept(p)
                    if v:
                        return v
                    break
        if depth < 2:
            for fu in frames:
                if fu in tried:
                    continue
                tried.add(fu)
                try:
                    r = http_get(fu, header_variants(fu, page_url, cookie, extra)[-1])
                    t2 = r.text[:3_000_000] if r.status_code < 400 else ""
                    r.close()
                except Exception:
                    continue
                if t2:
                    v = sniff(t2, fu, depth + 1)
                    if v:
                        return v
        return None

    if html_text:
        v = sniff(html_text, html_final or url, 0)
        if v:
            return done(v)
        _, frames = sniff_candidates(html_text, html_final or url)
        for fu in frames[:2]:
            p, err = ytd(fu, base + "_f", html_final or url, audio_only=not need_video)
            v = accept(p)
            if v:
                return done(v)

    # 3.5) متصفح حقيقي على السيرفر (للصفحات والروابط الموقّعة)
    for mu, mh in browser_sniff(url, prog):
        prog.update(f"المتصفح لقى فيديو، بجرّبه: {mu[:80]}", force=True)
        low = urllib.parse.urlparse(mu).path.lower()
        p, err = ytdlp_download(mu, base + "_b", url, mh.get("Cookie"), {}, cfile, prog, maxh=maxh)
        v = accept(p)
        if not v and low.endswith((".m3u8", ".mpd")):
            p, err = ffmpeg_stream(mu, mh, base + "_b.mp4", prog)
            v = accept(p)
        if not v and not low.endswith((".m3u8", ".mpd")):
            res = try_direct(mu, mh, base + "_b", prog)
            if res["kind"] == "media":
                v = accept(res["path"])
        if v:
            return done(v)

    # 4) آخر محاولة: ffmpeg على الرابط كما هو
    prog.update("آخر محاولة بـ ffmpeg...", force=True)
    p, err = ffmpeg_stream(url, hv[-1], base + "_raw.mp4", prog)
    v = accept(p)
    if v:
        return done(v)
    errors.append("ffmpeg: " + err[-200:])

    blob = " ".join(errors).lower()
    hints = []
    if "sign in to confirm" in blob or "not a bot" in blob or "cookies" in blob:
        hints.append("الموقع بيطلب تسجيل دخول. من «إعدادات متقدمة» ارفع ملف كوكيز (cookies.txt) من متصفحك، أو استخدم رابط تحميل مباشر.")
    if "403" in blob or "401" in blob or "forbidden" in blob:
        hints.append("الرابط محمي أو التوكن انتهى. افتحه من المتصفح وانسخه من جديد وشغّل فورًا، أو ضع Referer (رابط الصفحة اللي فيها الفيديو) والكوكيز في الإعدادات المتقدمة.")
    if "404" in blob or "410" in blob:
        hints.append("الرابط اتشال أو التوكن انتهت صلاحيته. هات رابط جديد.")
    if "drm" in blob:
        hints.append("الفيديو ده محمي بـ DRM (زي نتفليكس) ومينفعش يتحمّل.")
    if "private" in blob or "login" in blob:
        hints.append("الفيديو خاص أو محتاج تسجيل دخول.")
    if "yt-dlp: " in blob and ("unable to extract" in blob or "http error 4" in blob or "nsig" in blob):
        hints.append("جرّب زرار «تحديث yt-dlp» تحت في الصفحة — المواقع بتغيّر نفسها كتير.")
    if not hints:
        hints.append("تأكد إن الرابط شغّال لما تفتحه في المتصفح، وإنه ماينتهيش (روابط التوكن بتنتهي بسرعة). لو الفيديو جوه صفحة، افتح الصفحة وانسخ رابط الفيديو نفسه (mp4/m3u8).")
    raise UserError("فشل تحميل الفيديو من الرابط ده بعد كل المحاولات.\n• " + "\n• ".join(hints)
                    + ("\n\nتفاصيل تقنية: " + (errors[-1][-250:] if errors else "")))


# ════════════════════════════════════════════════════════════
#  النص العربي: خطوط + تشكيل + رسم
# ════════════════════════════════════════════════════════════
FONT_CATALOG = [("Tajawal", "tajawal/Tajawal-ExtraBold.ttf"), ("Almarai", "almarai/Almarai-ExtraBold.ttf"),
                ("Lalezar", "lalezar/Lalezar-Regular.ttf"), ("Amiri", "amiri/Amiri-Bold.ttf")]
FONT_MIRRORS = ["https://raw.githubusercontent.com/google/fonts/main/ofl/{p}",
                "https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/{p}",
                "https://github.com/google/fonts/raw/main/ofl/{p}"]


def download_font(rel, quiet=True):
    os.makedirs(FONT_DIR, exist_ok=True)
    dest = os.path.join(FONT_DIR, os.path.basename(rel))
    if os.path.exists(dest) and os.path.getsize(dest) > 20000:
        return dest
    for m in FONT_MIRRORS:
        try:
            req = urllib.request.Request(m.format(p=rel), headers={"User-Agent": UA})
            data = urllib.request.urlopen(req, timeout=20).read()
            if len(data) > 20000 and data[:4] in (b"\x00\x01\x00\x00", b"OTTO", b"true", b"ttcf"):
                with open(dest, "wb") as f:
                    f.write(data)
                return dest
        except Exception:
            continue
    return None


def prefetch_fonts():
    for label, rel in FONT_CATALOG:
        print(f"  خط {label}:", "تمام ✓" if download_font(rel) else "ما اتحمّلش (مش مشكلة، في خط بديل)")


def font_candidates(preferred):
    """بيدّي الخطوط واحد ورا التاني، من الأفضل للأضعف (ومن غير تحميل إلا لما يلزم)."""
    seen = set()

    def ok(p):
        if p and p not in seen and os.path.exists(p):
            seen.add(p)
            return True
        return False

    order = sorted(FONT_CATALOG, key=lambda x: 0 if x[0] == preferred else 1)
    p = download_font(order[0][1])
    if ok(p):
        yield p
    for _, rel in order[1:]:
        p = os.path.join(FONT_DIR, os.path.basename(rel))
        if ok(p):
            yield p
    for p in sorted(glob.glob(os.path.join(FONT_DIR, "*.[to]tf"))):
        if ok(p):
            yield p
    for pat in ("/system/fonts/*Arabic*", "/system/fonts/*Naskh*", "/system/fonts/*Kufi*", "/system/fonts/DroidSansArabic*"):
        for p in sorted(glob.glob(pat), key=lambda x: (0 if "bold" in x.lower() else 1, x)):
            if p.lower().endswith((".ttf", ".otf")) and ok(p):
                yield p
    r = run(["fc-list", ":lang=ar", "--format", "%{file}\n"])
    for line in (r.stdout or "").splitlines():
        line = line.strip()
        if line.lower().endswith((".ttf", ".otf")) and ok(line):
            yield line
    for p in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if ok(p):
            yield p
    for _, rel in order[1:]:
        p = download_font(rel)
        if ok(p):
            yield p


_CMAP = {}


def font_cmap(path):
    if path in _CMAP:
        return _CMAP[path]
    try:
        from fontTools.ttLib import TTFont
        _CMAP[path] = set(TTFont(path, lazy=True, fontNumber=0).getBestCmap().keys())
    except Exception:
        _CMAP[path] = None
    return _CMAP[path]


_RENDER = {}


def missing_chars(path, chars):
    """الحروف اللي الخط مش بيرسمها فعلًا (مربع أو فاضي). بنجرّب رسم حقيقي بدل ما نثق في جدول الخط،
    وده بيشتغل حتى لو fontTools مش مثبتة."""
    chars = {c for c in chars if not c.isspace() and c != "|"}
    try:
        from PIL import Image, ImageDraw, ImageFont
        key = path
        if key not in _RENDER:
            _RENDER[key] = ImageFont.truetype(path, 56, layout_engine=ImageFont.Layout.BASIC)
        font = _RENDER[key]

        def sig(ch):
            im = Image.new("L", (110, 110), 0)
            ImageDraw.Draw(im).text((10, 10), ch, font=font, fill=255)
            return im.tobytes() if im.getbbox() else None

        tofu = sig("\uFFFF")
        out = set()
        for c in chars:
            sg = sig(c)
            if sg is None or sg == tofu:
                out.add(c)
        return out
    except Exception:
        cm = font_cmap(path)
        return {c for c in chars if cm is not None and ord(c) not in cm}


def is_arabic_cp(c):
    o = ord(c)
    return 0x0600 <= o <= 0x06FF or 0x0750 <= o <= 0x077F or 0xFB50 <= o <= 0xFDFF or 0xFE70 <= o <= 0xFEFF


def has_arabic(s):
    return any(is_arabic_cp(c) for c in s)


def raqm_available():
    try:
        from PIL import features
        return bool(features.check("raqm"))
    except Exception:
        return False


def get_shaper():
    try:
        import arabic_reshaper
        try:
            from bidi import get_display
        except ImportError:
            from bidi.algorithm import get_display
        rs = arabic_reshaper.ArabicReshaper(configuration={"support_ligatures": True, "delete_harakat": False}) \
            if hasattr(arabic_reshaper, "ArabicReshaper") else None

        _M = str.maketrans("()[]{}<>«»", ")(][}{><»«")
        try:
            # بعض إصدارات python-bidi مش بتعكس الأقواس في الاتجاه العربي. بنختبر ونصلّح.
            _probe = get_display("(\u0627)")
            fix = _probe.startswith(")")
        except Exception:
            fix = False

        def shape(s):
            r = get_display(rs.reshape(s) if rs else arabic_reshaper.reshape(s))
            return r.translate(_M) if fix else r
        return shape
    except Exception:
        return None


def clean_text(s):
    s = s.replace("\\n", "\n")
    out = []
    for ch in s:
        cat = unicodedata.category(ch)
        if ch == "\n" or ch == "|":
            out.append(ch)
        elif cat in ("Cf", "Cc"):
            continue
        else:
            out.append(ch)
    return "".join(out)


def pick_font(text, preferred, force_basic=False):
    """يرجّع (path, mode, نص_منظّف, محذوفات) لأول خط يغطي النص فعلًا."""
    raqm = raqm_available() and not force_basic
    shape = None if raqm else get_shaper()
    if not raqm and shape is None:
        raise UserError("مكتبات تشكيل العربي (arabic-reshaper / python-bidi) مش مثبتة. شغّل install.sh تاني.")
    for p in font_candidates(preferred):
        if raqm:
            miss = missing_chars(p, set(text))
            if any(is_arabic_cp(c) for c in miss):
                continue
            cleaned = "".join(c for c in text if c not in miss)
            return p, "raqm", cleaned, sorted(miss)
        else:
            shaped_all = shape(re.sub(r"[|\n]", " ", text))
            miss = missing_chars(p, set(shaped_all))
            if any(is_arabic_cp(c) for c in miss):
                continue
            cleaned = text
            miss_orig = missing_chars(p, set(text))
            if miss_orig:
                cleaned = "".join(c for c in text if c not in miss_orig or is_arabic_cp(c))
            return p, "basic", cleaned, sorted(miss | miss_orig)
    return None, None, text, []


STYLES = {
    "صندوق داكن شفاف": dict(box=(0, 0, 0, 165), fg=(255, 255, 255, 255), stroke=None),
    "صندوق أبيض": dict(box=(255, 255, 255, 238), fg=(18, 18, 18, 255), stroke=None),
    "صندوق ذهبي": dict(box=(250, 204, 21, 245), fg=(28, 25, 23, 255), stroke=None),
    "بدون صندوق (حدود وظل)": dict(box=None, fg=(255, 255, 255, 255), stroke=(0, 0, 0, 255)),
}


def build_text_overlay(work, W, H, ratio, S, quiet=False):
    """يرجّع (png, preview_png, notes). notes = قائمة (نوع, رسالة)."""
    from PIL import Image, ImageDraw, ImageFont, ImageFilter
    notes = []

    def note(m, k="info"):
        notes.append((k, m))
        if not quiet:
            say(m, k)

    raw = clean_text(S["text"]).strip()
    if not raw:
        return None, None, notes
    pref = S["font_choice"].split()[0]
    path, mode, text, missing = pick_font(raw, pref)
    if not path:
        note("ملقيتش خط عربي صالح للنص، هيتعمل الريل من غير نص. (شغّل install.sh تاني لتحميل الخطوط)", "warn")
        return None, None, notes
    if missing:
        note("اتشالت رموز مش مدعومة في الخط (زي الإيموجي): " + " ".join(missing[:10]), "warn")
    shape = get_shaper() if mode == "basic" else None

    def prep(line):
        return line if mode == "raqm" else shape(line)

    def kw(line):
        return {"direction": "rtl", "language": "ar"} if (mode == "raqm" and has_arabic(line)) else {}

    layout = ImageFont.Layout.RAQM if mode == "raqm" else ImageFont.Layout.BASIC
    style = STYLES.get(S["style"], STYLES["صندوق داكن شفاف"])
    base_fs = int(W * {"صغير": 0.050, "متوسط": 0.064, "كبير": 0.078}.get(S["font_size"], 0.064))
    max_box_w = int(W * 0.94)
    pad_x_k, pad_y_k = 0.60, 0.36
    probe_img = ImageDraw.Draw(Image.new("RGBA", (10, 10)))

    # سطر واحد دايمًا: بنوحّد كل الفواصل (| أو سطر جديد) في سطر، وبنصغّر الخط لحد ما يكفي العرض
    text = re.sub(r"\s*(?:\||\n)\s*", " ", text).strip()
    text = re.sub(r"\s{2,}", " ", text)
    min_fs = max(18, int(W * 0.030))
    words = text.split()
    cut = False
    fs = base_fs
    while True:
        font = ImageFont.truetype(path, fs, layout_engine=layout)
        pad_x, pad_y = int(fs * pad_x_k), int(fs * pad_y_k)
        maxw = max_box_w - 2 * pad_x
        widest = probe_img.textlength(prep(text), font=font, **kw(text))
        if widest <= maxw + 1:
            break
        if fs > min_fs:
            fs = max(min_fs, min(fs - 1, int(fs * maxw / widest)))
            continue
        if len(words) > 1:  # نص طويل جدًا حتى بأصغر خط مقروء: بنقصّ آخره بدل ما نخلّي الخط مش مقروء
            words.pop()
            text = " ".join(words) + "…"
            cut = True
            continue
        break
    if cut:
        note("النص طويل جدًا عشان يتحط في سطر واحد بخط مقروء، فاتقصّ آخره. اختصره لو عايزه كامل.", "warn")
    lines = [text]
    widest = probe_img.textlength(prep(text), font=font, **kw(text))

    lh = int(fs * 1.5)
    bw = int(widest) + 2 * pad_x
    bh = len(lines) * lh + 2 * pad_y
    top_lim = {"9:16": 0.13, "4:5": 0.07, "1:1": 0.06}[ratio]
    bot_lim = {"9:16": 0.78, "4:5": 0.90, "1:1": 0.92}[ratio]
    if S["pos"] == "أعلى":
        top = int(H * top_lim)
    elif S["pos"] == "وسط":
        top = int(H * 0.5 - bh / 2)
    else:
        top = int(H * bot_lim - bh)
    top = max(8, min(top, H - bh - 8))
    left = (W - bw) // 2

    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if style["box"]:
        sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle([left, top + int(fs * 0.12), left + bw, top + bh + int(fs * 0.12)],
                                             radius=int(fs * 0.55), fill=(0, 0, 0, 120))
        img = Image.alpha_composite(img, sh.filter(ImageFilter.GaussianBlur(fs * 0.22)))
        d = ImageDraw.Draw(img)
        d.rounded_rectangle([left, top, left + bw, top + bh], radius=int(fs * 0.55), fill=style["box"])
    else:
        sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        sd = ImageDraw.Draw(sh)
        for i, l in enumerate(lines):
            sd.text((W / 2, top + pad_y + i * lh + lh / 2 + fs * 0.06), prep(l), font=font, fill=(0, 0, 0, 190),
                    anchor="mm", **kw(l))
        img = Image.alpha_composite(img, sh.filter(ImageFilter.GaussianBlur(fs * 0.09)))
        d = ImageDraw.Draw(img)
    sw = max(2, fs // 13) if style["stroke"] else 0
    for i, l in enumerate(lines):
        extra = {"stroke_width": sw, "stroke_fill": style["stroke"]} if sw else {}
        d.text((W / 2, top + pad_y + i * lh + lh / 2), prep(l), font=font, fill=style["fg"], anchor="mm", **kw(l), **extra)
    os.makedirs(work, exist_ok=True)
    png = os.path.join(work, "text.png")
    img.save(png)

    prev = Image.new("RGBA", (W, H), (24, 28, 40, 255))
    pd = ImageDraw.Draw(prev)
    for yy in range(H):
        c = int(24 + 30 * yy / H)
        pd.line([(0, yy), (W, yy)], fill=(c, c + 6, c + 22, 255))
    prev = Image.alpha_composite(prev, img).convert("RGB")
    prev.thumbnail((360, 640))
    pp = os.path.join(work, "text_preview.png")
    prev.save(pp)
    note(f"تم تجهيز النص العربي بخط {os.path.basename(path).split('-')[0].split('.')[0]} "
         f"({'تشكيل متقدم' if mode == 'raqm' else 'تشكيل يدوي'}).", "ok")
    return png, pp, notes


def dims_for(ratio, res):
    W = 1080 if str(res) == "1080" else 720
    H = {"9:16": W * 16 // 9, "4:5": W * 5 // 4, "1:1": W}[ratio]
    return W, H - (H % 2)


def preview_text(raw):
    """معاينة سريعة للنص من الواجهة (من غير ما نعمل ريل)."""
    S = normalize_settings(raw, need_rights=False)
    W, H = dims_for(S["ratio"], S["res"])
    wd = os.path.join(CACHE, "preview")
    png, pp, notes = build_text_overlay(wd, W, H, S["ratio"], S, quiet=True)
    return pp, notes


# ════════════════════════════════════════════════════════════
#  اختيار أحسن لحظات الفيديو (طاقة الصوت) أو توزيع متساوي
# ════════════════════════════════════════════════════════════
def audio_energy(src, hop=0.25, max_seconds=240):
    if np is None:
        return None
    sr = 8000
    cmd = ["ffmpeg", "-v", "error", "-nostdin", "-i", src, "-vn", "-ac", "1", "-ar", str(sr), "-f", "f32le", "-"]
    try:
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    except OSError:
        return None
    with _PLOCK:
        _PROCS.add(p)
    frame = int(sr * hop) * 4
    vals, t0 = [], time.time()
    try:
        while True:
            _chk()
            b = p.stdout.read(frame)
            if not b:
                break
            a = np.frombuffer(b[: len(b) // 4 * 4], dtype=np.float32)
            if a.size:
                vals.append(float(np.sqrt(np.mean(a * a))))
            if time.time() - t0 > max_seconds:
                p.kill()
                return None
    finally:
        try:
            p.stdout.close()
        except Exception:
            pass
        p.wait()
        with _PLOCK:
            _PROCS.discard(p)
    _chk()
    return np.array(vals) if vals else None


def uniform_windows(dur, n, L):
    return [(max(0.0, min(dur - L, (i + 0.5) * dur / n - L / 2)), L) for i in range(n)]


def no_overlap(ws, dur):
    """كل ريل من جزء مختلف تمامًا في الفيديو: مفيش مقطع بيتكرر ولا بيتداخل مع اللي قبله."""
    out, end = [], 0.0
    for st, ln in sorted(ws):
        st = max(st, end)
        ln = min(ln, dur - st)
        if ln < 3 and out:
            continue
        out.append((round(st, 2), round(max(ln, 0.5), 2)))
        end = st + ln
    return out


def smart_windows(src, dur, n, L, min_l, max_l):
    if np is None:
        return None
    hop = 0.25
    e = audio_energy(src, hop)
    if e is None or len(e) < 16:
        return None
    db = 20 * np.log10(e + 1e-5)
    floor, ceil = float(np.percentile(db, 10)), float(np.percentile(db, 95))
    if ceil - floor < 3:
        return None
    s = np.clip((db - floor) / (ceil - floor), 0, 1)
    N, Lf = len(s), int(round(L / hop))
    if Lf < 1 or Lf * n > N:
        return None
    cs, cs2 = np.concatenate([[0], np.cumsum(s)]), np.concatenate([[0], np.cumsum(s * s)])
    starts = N - Lf + 1
    mean = (cs[Lf:Lf + starts] - cs[:starts]) / Lf
    var = (cs2[Lf:Lf + starts] - cs2[:starts]) / Lf - mean ** 2
    score = mean + 0.3 * np.sqrt(np.maximum(var, 0))
    NEG = -1e18
    best_prev, cands = np.zeros(N + 1), []
    for j in range(1, n + 1):
        cand = np.full(N + 1, NEG)
        idx = np.arange(Lf, N + 1)
        cand[Lf:] = best_prev[idx - Lf] + score[idx - Lf]
        cands.append(cand)
        best_prev = np.maximum.accumulate(cand)
    i, sel = N, []
    for j in range(n, 0, -1):
        cand = cands[j - 1]
        k = int(np.argmax(cand[: i + 1]))
        if cand[k] < -1e17:
            return None
        st = k - Lf
        sel.append(st)
        i = st
    sel.reverse()

    def quiet(t0_, t1_):
        a, b = int(max(0, t0_) / hop), int(min(t1_, N * hop - hop) / hop)
        if b < a:
            return None
        f = a + int(np.argmin(db[a:b + 1]))
        return (f + 0.5) * hop

    out, prev_end = [], 0.0
    for k, st in enumerate(sel):
        st_t = st * hop
        en_t = st_t + L
        nxt = sel[k + 1] * hop if k + 1 < len(sel) else dur
        s2 = quiet(max(prev_end, st_t - 1.5), min(st_t + 1.0, en_t - min_l))
        if s2 is None:
            s2 = st_t
        e2 = quiet(max(s2 + min_l, en_t - 2.0), min(s2 + max_l, en_t + 1.5, nxt, dur - 0.05))
        if e2 is None or e2 - s2 < min_l - 0.01:
            s2, e2 = max(prev_end, st_t), min(st_t + L, dur)
        out.append((round(s2, 2), round(e2 - s2, 2)))
        prev_end = s2 + (e2 - s2)
    return out


# ════════════════════════════════════════════════════════════
#  الموسيقى
# ════════════════════════════════════════════════════════════
def make_builtin_music(path):
    freqs = [110.0, 220.0, 220.7, 261.63, 329.63, 392.0, 493.88]
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
    for f in freqs:
        cmd += ["-f", "lavfi", "-i", f"sine=frequency={f}:sample_rate=44100:duration=40"]
    ins = "".join(f"[{i}:a]" for i in range(len(freqs)))
    g = (f"{ins}amix=inputs={len(freqs)},volume={len(freqs)},tremolo=f=0.12:d=0.35,lowpass=f=1400,"
         "aecho=0.8:0.6:600|1000:0.35|0.25,aformat=channel_layouts=stereo,"
         "loudnorm=I=-14:TP=-2:LRA=7,aresample=44100,afade=t=in:d=3,afade=t=out:st=37:d=3")
    r = run(cmd + ["-filter_complex", g, "-ac", "2", path])
    return r.returncode == 0 and os.path.exists(path), r.stderr[-300:]


def convert_music(src, dst):
    r = run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", src, "-vn", "-ac", "2", "-ar", "44100",
             "-af", "loudnorm=I=-14:TP=-2:LRA=7,aresample=44100", dst])
    return r.returncode == 0 and os.path.exists(dst)


# ════════════════════════════════════════════════════════════
#  بناء أمر ffmpeg لكل ريل
# ════════════════════════════════════════════════════════════
def build_cmd(src, start, length, out, P, text_png, music, use_text, use_music):
    W, H, spd = P["W"], P["H"], P["spd"]
    out_len = length / spd
    inputs = ["-ss", f"{start:.3f}", "-t", f"{length:.3f}", "-i", src]
    idx, t_idx, m_idx = 1, None, None
    if use_text and text_png:
        inputs += ["-loop", "1", "-framerate", "30", "-i", text_png]
        t_idx = idx
        idx += 1
    if use_music and music:
        inputs += ["-stream_loop", "-1", "-i", music]
        m_idx = idx
        idx += 1

    enh = ",eq=contrast=1.04:saturation=1.10,unsharp=5:5:0.6:3:3:0.0" if P["enhance"] else ""
    tail = (f",setpts={1/spd:.5f}*PTS" if spd != 1 else "") + (",reverse" if P["reverse"] else "")
    if P["fill"] == "blur":
        bw, bh = (W // 4) // 2 * 2, (H // 4) // 2 * 2
        vf = (f"[0:v]fps=30,setsar=1,split[a][b];"
              f"[a]scale={bw}:{bh}:force_original_aspect_ratio=increase,crop={bw}:{bh},boxblur=8:2,"
              f"eq=brightness=-0.07:saturation=1.15,scale={W}:{H},setsar=1[bg];"
              f"[b]scale={W}:{H}:force_original_aspect_ratio=decrease:force_divisible_by=2,setsar=1[fg];"
              f"[bg][fg]overlay=(W-w)/2:(H-h)/2,format=yuv420p,setsar=1{enh}{tail}[v0]")
    else:
        vf = (f"[0:v]fps=30,scale={W}:{H}:force_original_aspect_ratio=increase:force_divisible_by=2,"
              f"crop={W}:{H},setsar=1,format=yuv420p{enh}{tail}[v0]")
    vf += f";[v0][{t_idx}:v]overlay=0:0:shortest=1[v]" if t_idx is not None else ";[v0]null[v]"

    fo = max(0.0, out_len - 0.5)
    fade = f"afade=t=in:d=0.12,afade=t=out:st={fo:.3f}:d=0.5"
    base_a = "aresample=44100,aformat=channel_layouts=stereo"
    at = ",".join(x for x in [f"atempo={spd}" if spd != 1 else "", "areverse" if P["reverse"] else ""] if x)
    a0 = f"[0:a]{base_a}" + (f",{at}" if at else "")
    use_orig = P["use_orig"]
    if use_orig and m_idx is not None:
        fa = (f"{a0}[a0];[{m_idx}:a]{base_a},volume={P['mvol']}[m];"
              f"[a0][m]amix=inputs=2:duration=first:dropout_transition=0,volume=2,"
              f"loudnorm=I=-16:TP=-1.5:LRA=11,aresample=44100,{fade}[a]")
    elif use_orig:
        fa = f"{a0},loudnorm=I=-16:TP=-1.5:LRA=11,aresample=44100,{fade}[a]"
    elif m_idx is not None:
        fa = f"[{m_idx}:a]{base_a},volume=0.5,{fade}[a]"
    else:
        fa = None
    fc = vf + (";" + fa if fa else "")
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-progress", "pipe:1", "-nostats"] \
        + inputs + ["-filter_complex", fc, "-map", "[v]"]
    cmd += (["-map", "[a]", "-c:a", "aac", "-b:a", "128k", "-ar", "44100", "-ac", "2"] if fa else ["-an"])
    cmd += ["-t", f"{out_len:.3f}", "-c:v", "libx264", "-preset", "superfast", "-crf", "24", "-maxrate", "6M",
            "-bufsize", "12M", "-profile:v", "high", "-pix_fmt", "yuv420p", "-r", "30",
            "-movflags", "+faststart", "-map_metadata", "-1", out]
    return cmd, out_len


def render_clip(cmd, out_len, on_frac):
    errs = []

    def on_line(line):
        m = re.match(r"out_time_(?:us|ms)=(\d+)", line)
        if m:
            on_frac(min(1.0, int(m.group(1)) / 1e6 / max(0.1, out_len)))
            return
        if re.match(r"^[a-z_0-9]+=", line):
            return
        if line.strip():
            errs.append(line)

    rc, _ = run_stream(cmd, on_line)
    return rc, "\n".join(errs[-6:])


# ════════════════════════════════════════════════════════════
#  الإعدادات
# ════════════════════════════════════════════════════════════
def normalize_settings(raw, need_rights=True):
    g = raw.get
    S = {
        "url": str(g("url", "") or ""),
        "referer": str(g("referer", "") or "").strip(),
        "cookie": str(g("cookie", "") or "").strip(),
        "cookies_file": str(g("cookies_file", "") or "").strip(),
        "extra": parse_extra_headers(str(g("extra", "") or "")),
        "min_l": to_int(g("min_l", 15), 15, 3, 600),
        "max_l": to_int(g("max_l", 25), 25, 3, 600),
        "n": to_int(g("n", 5), 5, 1, 30),
        "pick": str(g("pick", "ذكي (أحسن اللحظات)")),
        "ratio": str(g("ratio", "9:16")).split()[0],
        "res": "1080" if str(g("res", "1080")) == "1080" else "720",
        "fill": str(g("fill", "تلقائي (الأنسب)")),
        "spd": to_float(g("spd", 1.0), 1.0),
        "enhance": bool(g("enhance", True)),
        "use_orig": bool(g("use_orig", True)),
        "reverse": bool(g("reverse", False)),
        "use_text": bool(g("use_text", True)),
        "text": str(g("text", "") or ""),
        "pos": str(g("pos", "أسفل")),
        "font_size": str(g("font_size", "متوسط")),
        "font_choice": str(g("font_choice", "Tajawal (عصري)")),
        "style": str(g("style", "صندوق داكن شفاف")),
        "music_on": bool(g("music_on", True)),
        "music_src": str(g("music_src", "موسيقى هادئة مدمجة")),
        "music_url": str(g("music_url", "") or ""),
        "mvol": to_int(g("mvol", 8), 8, 1, 30),
        "rights": bool(g("rights", False)),
    }
    if S["ratio"] not in ("9:16", "4:5", "1:1"):
        S["ratio"] = "9:16"
    if not (0.5 <= S["spd"] <= 2.0):
        S["spd"] = 1.0
    return S


# ════════════════════════════════════════════════════════════
#  التسليم (مجلد التطبيق + معرض الجهاز)
# ════════════════════════════════════════════════════════════
def shared_dir():
    for base in (os.path.expanduser("~/storage/shared"), "/storage/emulated/0", "/sdcard"):
        if os.path.isdir(base) and os.access(base, os.W_OK):
            return base
    return None


def prune_outputs(keep=3):
    try:
        dirs = sorted(d for d in os.listdir(OUT) if re.fullmatch(r"\d{8}-\d{6}", d))
        for d in dirs[:-keep]:
            shutil.rmtree(os.path.join(OUT, d), ignore_errors=True)
    except Exception:
        pass


def prune_uploads(max_age=2 * 86400):
    try:
        for f in glob.glob(os.path.join(UPLOADS, "*")):
            if time.time() - os.path.getmtime(f) > max_age:
                os.remove(f)
    except Exception:
        pass


# ════════════════════════════════════════════════════════════
#  البرنامج الرئيسي للمهمة
# ════════════════════════════════════════════════════════════
def _main(raw):
    T0 = time.time()
    prog = PROG
    S = normalize_settings(raw)
    if not S["rights"]:
        raise UserError("علّم على خانة «أؤكد أني أملك حقوق الفيديو» وبعدين ابدأ تاني.")
    if not S["url"].strip():
        raise UserError("الصق رابط الفيديو أو اختر فيديو من جهازك.")
    if S["max_l"] < S["min_l"]:
        S["min_l"], S["max_l"] = S["max_l"], S["min_l"]
        say("أقل مدة كانت أكبر من أقصى مدة، فبدّلتهم تلقائيًا.", "warn")
    if S["cookies_file"] and not os.path.isfile(S["cookies_file"]):
        say("ملف الكوكيز مش موجود، هتجاهله.", "warn")
        S["cookies_file"] = ""

    shutil.rmtree(WORK, ignore_errors=True)
    os.makedirs(WORK + "/clips")
    if S["cookies_file"]:
        shutil.copy(S["cookies_file"], WORK + "/cookies.txt")
        S["cookies_file"] = WORK + "/cookies.txt"
    opts = {k: S[k] for k in ("referer", "cookie", "extra", "cookies_file")}
    opts["maxh"] = 1080 if S["res"] == "1080" else 720

    # فيديو طويل (أكتر من 40 دقيقة): بنحمّل مصدر 720 بدل 1080 — أسرع بكتير وبنفس جودة الريل تقريبًا
    try:
        _u0 = extract_url(S["url"])
        if _u0 and not os.path.isfile(os.path.expanduser(S["url"].strip())) and opts["maxh"] > 720:
            _u1 = normalize_url(_u0)
            _h = urllib.parse.urlparse(_u1).netloc.lower()
            if any(_h == x or _h.endswith(x) for x in SITES_FIRST):
                _d0 = ytdlp_duration(_u1, S["cookies_file"])
                if _d0 and _d0 > 2400:
                    opts["maxh"] = 720
                    say(f"الفيديو طويل ({fmt_t(_d0)}): هحمّله بجودة 720 عشان الشغل يخلص أسرع.", "info")
    except Exception:
        pass

    # ---------- 1) التحميل ----------
    say("1️⃣ تحميل الفيديو...")
    prog.stage(0, 35)
    prog.update("بجهّز التحميل...", force=True, frac=0)
    src, info, src_temp = fetch_media(S["url"], WORK, "source", opts, prog, need_video=True)
    dur = info["dur"]
    prog.stage(35, 40)
    say(f"تم التحميل: {os.path.getsize(src)/1048576:.1f} MB — المدة {fmt_t(dur)} — "
        f"{info['w']}×{info['h']}{'' if info['has_audio'] else ' — بدون صوت'}", "ok")

    # ---------- 2) الأبعاد والتقطيع ----------
    ratio = S["ratio"]
    W, H = dims_for(ratio, S["res"])
    fill = S["fill"]
    if fill.startswith("قص"):
        fill = "crop"
    elif fill.startswith("خلفية"):
        fill = "blur"
    else:
        sa = (info["w"] or W) / (info["h"] or H)
        r = sa / (W / H)
        fill = "crop" if 0.8 <= r <= 1.25 else "blur"
        say("الملء التلقائي: " + ("قص الأطراف (الفيديو قريب من المقاس)" if fill == "crop" else "خلفية مغبشة (عشان الفيديو كله يفضل ظاهر)"))
    spd = S["spd"]
    min_l, max_l = S["min_l"], S["max_l"]
    n_req = S["n"]
    if dur < min_l:
        L, n = dur, 1
        say(f"الفيديو ({fmt_t(dur)}) أقصر من أقل مدة، هعمل ريل واحد بطوله كله.", "warn")
    else:
        L = max(min_l, min(max_l, dur / n_req))
        L = math.floor(L * 10) / 10
        n = max(1, min(n_req, int(dur // L)))
        if n < n_req:
            say(f"الفيديو مش بيكفي {n_req} مقاطع بالمدة دي، هعمل {n} مقاطع بدل كده.", "warn")

    windows = None
    if n >= 1 and dur >= min_l and S["pick"].startswith("ذكي") and info["has_audio"]:
        prog.update("بحلّل الصوت عشان أختار أحسن اللحظات...", force=True)
        try:
            windows = smart_windows(src, dur, n, L, min_l, max_l)
        except Cancelled:
            raise
        except Exception:
            windows = None
        if windows:
            say("تم اختيار أنشط اللحظات (كلام/صوت عالي) بدل التوزيع المتساوي، عشان المشاهد ما يزهقش.", "ok")
    if not windows:
        windows = uniform_windows(dur, n, L) if dur >= min_l else [(0.0, dur)]
        say("تم توزيع المقاطع بالتساوي على طول الفيديو.")
    windows = no_overlap(windows, dur)
    for i, (st, ln) in enumerate(windows):
        say(f"ريل {i+1}: {fmt_t(st)} ← {fmt_t(st+ln)}  ({ln:.0f} ث)")

    reverse = S["reverse"]
    if reverse and max(ln for _, ln in windows) / spd * 30 * W * H * 1.5 > 1.5e9:
        reverse = False
        say("العكس بياخد رامات كبيرة للمدة دي على الموبايل، فلغيته تلقائيًا (جرّب جودة 720 أو مدة أقصر).", "warn")

    # ---------- 3) النص ----------
    prog.stage(40, 45)
    text_png = None
    if S["use_text"] and S["text"].strip():
        prog.update("بجهّز النص العربي...", force=True)
        try:
            text_png, _pp, _notes = build_text_overlay(WORK, W, H, ratio, S)
        except UserError:
            raise
        except Cancelled:
            raise
        except Exception as e:
            say(f"مشكلة في تجهيز النص ({e.__class__.__name__}: {str(e)[:120]}) — هكمّل من غير نص.", "warn")
            text_png = None

    # ---------- 4) الموسيقى ----------
    music = None
    if S["music_on"]:
        prog.update("بجهّز الموسيقى...", force=True)
        music = WORK + "/music.wav"
        try:
            if S["music_src"].startswith("رابط") and S["music_url"].strip():
                mp, _, _ = fetch_media(S["music_url"], WORK, "music_src", opts, prog, need_video=False)
                if not convert_music(mp, music):
                    raise UserError("تعذّر تحويل الموسيقى.")
            else:
                if S["music_src"].startswith("رابط"):
                    say("ما كتبتش رابط موسيقى، هستخدم الموسيقى الهادئة المدمجة.", "warn")
                cached = os.path.join(CACHE, "builtin_music.wav")
                if not (os.path.exists(cached) and os.path.getsize(cached) > 10000):
                    ok, err = make_builtin_music(cached)
                    if not ok:
                        raise UserError("فشل توليد الموسيقى: " + err)
                shutil.copy(cached, music)
            say("تم تجهيز الموسيقى.", "ok")
        except UserError as e:
            say(f"الموسيقى: {e} — هكمّل بدون موسيقى.", "warn")
            music = None

    P = {"W": W, "H": H, "spd": spd, "fill": fill, "enhance": S["enhance"], "reverse": reverse,
         "use_orig": bool(S["use_orig"] and info["has_audio"]), "mvol": S["mvol"] / 100}
    if S["use_orig"] and not info["has_audio"]:
        say("الفيديو الأصلي مفيهوش صوت.", "warn")

    # ---------- 5) تصيير الريلز ----------
    say(f"2️⃣ بعمل {len(windows)} ريل ({W}×{H})... (على الموبايل ممكن ياخد كام دقيقة، سيب الشاشة شغّالة أو التطبيق مفتوح)")
    prog.stage(45, 97)
    total = len(windows)
    fracs = [0.0] * total
    flock = threading.Lock()
    t_render = time.time()
    headless = bool(os.environ.get("REELS_HEADLESS"))
    workers = max(1, min(2, total, os.cpu_count() or 1)) if headless else 1

    def render_one(i):
        st, ln = windows[i]
        out = f"{WORK}/clips/reel_{i+1:02d}.mp4"
        attempts = [(True, True), (True, False), (False, True), (False, False)]
        if not text_png:
            attempts = [(False, True), (False, False)]
        if not music:
            attempts = [a for a in attempts if not a[1]] or [(False, False)]
            attempts = list(dict.fromkeys(attempts))
        done_ok, last_err, out_len, lost = False, "", ln / spd, []
        for (ut, um) in attempts:
            cmd, out_len = build_cmd(src, st, ln, out, P, text_png, music, ut, um)

            def on_frac(f):
                with flock:
                    fracs[i] = f
                    tot = sum(fracs) / total
                el = time.time() - t_render
                eta = el / tot * (1 - tot) if tot > 0.05 else None
                prog.update(f"بعمل الريلز ({total}) — {int(tot * 100)}%" + (f" — المتبقي تقريبًا {fmt_t(eta)}" if eta else ""),
                            frac=tot)

            rc, err = render_clip(cmd, out_len, on_frac)
            if rc == 0 and os.path.exists(out) and os.path.getsize(out) > 10000:
                done_ok = True
                if (text_png and not ut) or (music and not um):
                    lost = [x for x, f in (("النص", text_png and not ut), ("الموسيقى", music and not um)) if f]
                break
            last_err = err[-350:]
        return out, out_len, done_ok, lost, last_err

    prog.update(f"بعمل الريلز ({total})...", force=True, frac=0)
    if workers > 1:
        from concurrent.futures import ThreadPoolExecutor
        with ThreadPoolExecutor(max_workers=workers) as ex:
            results = list(ex.map(render_one, range(total)))
    else:
        results = [render_one(i) for i in range(total)]
    clips = []
    for i, (out, out_len, done_ok, lost, last_err) in enumerate(results):
        if done_ok:
            if lost:
                say(f"الريل {i+1}: اتعمل لكن من غير {' و'.join(lost)} بسبب مشكلة تقنية.", "warn")
            clips.append((out, out_len))
            say(f"الريل {i+1} جاهز — {out_len:.1f} ث — {os.path.getsize(out)/1048576:.1f} MB", "ok")
        else:
            say(f"فشل الريل {i+1}: {last_err}", "err")
    if not clips:
        raise UserError("ماتعملش أي ريل. جرّب تلغي «عكس الفيديو» أو تقلّل أقصى مدة أو تختار جودة 720، ولو استمرت المشكلة ابعتلي الرسالة اللي فوق.")
    if src_temp:
        try:
            os.remove(src)
        except Exception:
            pass

    # ---------- 6) التسليم ----------
    prog.stage(97, 100)
    prog.update("بحفظ الملفات...", force=True, frac=0)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    odir = os.path.join(OUT, stamp)
    os.makedirs(odir, exist_ok=True)
    final = []
    for p, ol in clips:
        dst = os.path.join(odir, os.path.basename(p))
        shutil.copy(p, dst)
        final.append((dst, ol))
    try:
        with zipfile.ZipFile(os.path.join(odir, "reels.zip"), "w", zipfile.ZIP_STORED) as z:
            for dst, _ in final:
                z.write(dst, os.path.basename(dst))
    except Exception:
        pass

    saved = ""
    sd = shared_dir()
    if sd:
        try:
            dest = os.path.join(sd, "Movies", "ReelsMaker", stamp)
            os.makedirs(dest, exist_ok=True)
            for dst, _ in final:
                shutil.copy(dst, dest)
                run(["am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d",
                     "file://" + os.path.join(dest, os.path.basename(dst))], timeout=10)
            saved = f"Movies/ReelsMaker/{stamp}"
            say(f"اتحفظت كمان في الذاكرة: {saved} (لو مظهرتش في المعرض افتح تطبيق «الملفات»).", "ok")
        except Cancelled:
            raise
        except Exception as e:
            say(f"تعذّر النسخ لذاكرة الجهاز ({e.__class__.__name__}). نزّل الريلز من الأزرار تحت.", "warn")
    else:
        if not os.environ.get("REELS_HEADLESS"):
            say("ما عنديش صلاحية لذاكرة الجهاز، نزّل الريلز من الأزرار تحت (أو شغّل termux-setup-storage).", "warn")

    with _LOCK:
        STATE["clips"] = [{"name": os.path.basename(d), "url": f"/out/{stamp}/{os.path.basename(d)}",
                           "mb": round(os.path.getsize(d) / 1048576, 1), "sec": round(ol, 1)} for d, ol in final]
        STATE["zip"] = f"/out/{stamp}/reels.zip"
        STATE["saved_to"] = saved
        STATE["pct"] = 100.0
        STATE["msg"] = "خلصنا!"
    prune_outputs(3)
    say(f"خلصنا! {len(final)} ريل في {fmt_t(time.time() - T0)}.", "ok")


def _wake(on):
    run(["termux-wake-lock" if on else "termux-wake-unlock"], timeout=8)


def _job(raw):
    succeeded = False
    try:
        _wake(True)
        _main(raw)
        succeeded = True
        with _LOCK:
            STATE["state"] = "done"
    except UserError as e:
        say(str(e), "err")
        with _LOCK:
            STATE["state"] = "error"
    except Cancelled:
        say("تم إلغاء العملية.", "warn")
        with _LOCK:
            STATE["state"] = "cancelled"
    except Exception as e:
        import traceback
        traceback.print_exc()
        say(f"حصل خطأ غير متوقع: {e.__class__.__name__}: {str(e)[:300]}", "err")
        with _LOCK:
            STATE["state"] = "error"
    finally:
        kill_all()
        CANCEL.clear()
        _wake(False)
        shutil.rmtree(WORK, ignore_errors=True)
        # الملفات المرفوعة بتتمسح بعد النجاح بس (لو فشل/اتلغى تقدر تحاول تاني من غير رفع)
        if succeeded:
            for pth in (str(raw.get("url", "")), str(raw.get("music_url", "")), str(raw.get("cookies_file", ""))):
                try:
                    if pth and pth.startswith(UPLOADS) and os.path.isfile(pth):
                        os.remove(pth)
                except Exception:
                    pass


def start_job(raw):
    if not raw.get("rights"):
        return False, "علّم على خانة «أؤكد أني أملك حقوق الفيديو» الأول."
    if not str(raw.get("url", "")).strip():
        return False, "الصق رابط الفيديو أو اختر فيديو من جهازك."
    with _LOCK:
        if STATE["state"] == "running":
            return False, "في عملية شغّالة حاليًا، استنى تخلص أو اضغط إلغاء."
        STATE.update({"state": "running", "job": STATE["job"] + 1, "logs": [], "msg": "بدأنا...", "pct": 0.0,
                      "clips": [], "saved_to": "", "zip": ""})
    CANCEL.clear()
    t = threading.Thread(target=_job, args=(raw,), daemon=True)
    _JOB["t"] = t
    t.start()
    return True, ""


# ════════════════════════════════════════════════════════════
#  فحص ذاتي: python3 engine.py --selftest
# ════════════════════════════════════════════════════════════
def selftest():
    init_dirs()

    def row(name, ok, extra=""):
        print(f"  {'✅' if ok else '❌'} {name} {extra}")

    print("فحص الأدوات:")
    row("ffmpeg", bool(shutil.which("ffmpeg")))
    row("ffprobe", bool(shutil.which("ffprobe")))
    v = ytdlp_version()
    row("yt-dlp", bool(v), v)
    row("numpy (الاختيار الذكي)", np is not None)
    try:
        import PIL
        row("Pillow", True, PIL.__version__)
    except Exception:
        row("Pillow", False)
    rq = raqm_available()
    sh = get_shaper() is not None
    row("تشكيل العربي", rq or sh, "(raqm)" if rq else "(arabic-reshaper)" if sh else "")
    try:
        import requests  # noqa: F401
        row("requests", True)
    except Exception:
        row("requests", False)
    row("node (يوتيوب)", bool(shutil.which("node")), "اختياري")
    try:
        p, mode, _, miss = pick_font("اختبار الخط العربي", "Tajawal")
        row("خط عربي", bool(p), os.path.basename(p) if p else "")
    except Exception as e:
        row("خط عربي", False, str(e))
    row("ذاكرة الجهاز", bool(shared_dir()), "(termux-setup-storage)" if not shared_dir() else "")


def cli_run(json_path):
    """تشغيل مهمة واحدة بدون واجهة (للسحابة / GitHub Actions). بيطبع مكان الملفات في آخر سطر."""
    os.environ["REELS_HEADLESS"] = "1"
    init_dirs()
    with open(json_path, encoding="utf-8") as f:
        raw = json.load(f)
    raw["rights"] = bool(raw.get("rights"))
    _JOB["t"] = threading.current_thread()
    try:
        _main(raw)
    except UserError as e:
        say(str(e), "err")
        return 2
    except Exception as e:
        import traceback
        traceback.print_exc()
        say(f"خطأ غير متوقع: {e.__class__.__name__}: {str(e)[:300]}", "err")
        return 1
    finally:
        shutil.rmtree(WORK, ignore_errors=True)
    z = STATE.get("zip") or ""
    print("RESULT_DIR=" + os.path.join(OUT, os.path.basename(os.path.dirname(z))), flush=True)
    return 0


if __name__ == "__main__":
    if "--run" in sys.argv:
        sys.exit(cli_run(sys.argv[sys.argv.index("--run") + 1]))
    if "--selftest" in sys.argv:
        selftest()
    elif "--fonts" in sys.argv:
        init_dirs()
        prefetch_fonts()
