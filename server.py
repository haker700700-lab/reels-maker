#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
سيرفر محلي صغير (على 127.0.0.1 بس — مش متاح لأي جهاز تاني) بيقدّم صفحة التطبيق
وبيشغّل محرك الريلز على الموبايل نفسه. مفيش أي اتصال بسيرفرات خارجية غير تحميل الفيديو نفسه.
"""
import os, re, sys, json, time, base64, mimetypes, threading, urllib.parse
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler

import engine

ROOT = os.path.dirname(os.path.abspath(__file__))
WEB = os.path.join(ROOT, "web")
PORT = int(os.environ.get("REELS_PORT", "8765"))
MAX_JSON = 1 << 20

mimetypes.add_type("application/manifest+json", ".json")
mimetypes.add_type("application/javascript", ".js")

_INFO = {}
_INFO_LOCK = threading.Lock()


def get_info(refresh=False):
    with _INFO_LOCK:
        if refresh or not _INFO:
            import shutil
            _INFO.update({"ytdlp": engine.ytdlp_version(), "ffmpeg": bool(shutil.which("ffmpeg")),
                          "raqm": engine.raqm_available(), "storage": bool(engine.shared_dir())})
        return dict(_INFO)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "ReelsMaker"

    def log_message(self, fmt, *args):
        pass

    # ---------- أدوات ----------
    def host_ok(self):
        h = (self.headers.get("Host") or "").rsplit(":", 1)[0].strip("[]").lower()
        return h in ("127.0.0.1", "localhost", "::1")

    def send_json(self, obj, code=200):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def fail(self, code, msg):
        self.close_connection = True
        self.send_json({"ok": False, "msg": msg}, code)

    def read_json(self):
        n = int(self.headers.get("Content-Length") or 0)
        if n > MAX_JSON:
            raise ValueError("too big")
        raw = self.rfile.read(n) if n else b"{}"
        return json.loads(raw.decode("utf-8") or "{}")

    def send_file(self, path, download_name=None, cache=False):
        try:
            size = os.path.getsize(path)
            f = open(path, "rb")
        except OSError:
            return self.fail(404, "not found")
        ctype = mimetypes.guess_type(path)[0] or "application/octet-stream"
        if ctype.startswith("text/") or ctype in ("application/javascript", "application/manifest+json"):
            ctype += "; charset=utf-8"
        start, end, code = 0, size - 1, 200
        rng = self.headers.get("Range")
        if rng:
            m = re.match(r"bytes=(\d*)-(\d*)$", rng.strip())
            if m and (m.group(1) or m.group(2)):
                a, b = m.groups()
                if a == "":
                    start = max(0, size - int(b))
                else:
                    start = int(a)
                    end = min(int(b), size - 1) if b else size - 1
                if start > end or start >= size:
                    f.close()
                    self.send_response(416)
                    self.send_header("Content-Range", f"bytes */{size}")
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                code = 206
        length = end - start + 1
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        if code == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        if download_name:
            self.send_header("Content-Disposition", f'attachment; filename="{download_name}"')
        self.send_header("Cache-Control", "public, max-age=3600" if cache else "no-cache")
        self.end_headers()
        try:
            f.seek(start)
            left = length
            while left > 0:
                chunk = f.read(min(1 << 18, left))
                if not chunk:
                    break
                self.wfile.write(chunk)
                left -= len(chunk)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            self.close_connection = True
        finally:
            f.close()

    # ---------- GET ----------
    def do_GET(self):
        if not self.host_ok():
            return self.fail(403, "forbidden")
        u = urllib.parse.urlparse(self.path)
        path = urllib.parse.unquote(u.path)
        q = urllib.parse.parse_qs(u.query)
        if path == "/api/ping":
            return self.send_json({"ok": True})
        if path == "/api/info":
            return self.send_json(get_info())
        if path == "/api/status":
            try:
                since = int((q.get("since") or ["0"])[0])
            except ValueError:
                since = 0
            job = (q.get("job") or [None])[0]
            if job in ("", "null", "undefined"):
                job = None
            return self.send_json(engine.snapshot(max(0, since), job))
        m = re.fullmatch(r"/out/(\d{8}-\d{6})/(reel_\d{2}\.mp4|reels\.zip)", path)
        if m:
            fp = os.path.join(engine.OUT, m.group(1), m.group(2))
            dl = m.group(2) if (q.get("dl") or [""])[0] == "1" else None
            return self.send_file(fp, download_name=dl)
        # ملفات الواجهة
        rel = "index.html" if path in ("/", "") else path.lstrip("/")
        fp = os.path.realpath(os.path.join(WEB, rel))
        if not fp.startswith(os.path.realpath(WEB) + os.sep) or not os.path.isfile(fp):
            return self.fail(404, "not found")
        return self.send_file(fp, cache=fp.endswith(".png"))

    # ---------- POST ----------
    def do_POST(self):
        if not self.host_ok() or self.headers.get("X-Reels") != "1":
            return self.fail(403, "forbidden")
        u = urllib.parse.urlparse(self.path)
        path, q = u.path, urllib.parse.parse_qs(u.query)
        try:
            if path == "/api/start":
                S = self.read_json()
                ok, msg = engine.start_job(S)
                return self.send_json({"ok": ok, "msg": msg})
            if path == "/api/cancel":
                self.read_json()
                engine.cancel()
                return self.send_json({"ok": True})
            if path == "/api/preview-text":
                S = self.read_json()
                try:
                    pp, notes = engine.preview_text(S)
                except engine.UserError as e:
                    return self.send_json({"ok": False, "msg": str(e)})
                if not pp or not os.path.exists(pp):
                    return self.send_json({"ok": False, "msg": "اكتب نص الأول، أو الخط العربي مش متاح.",
                                           "notes": [m for _, m in notes]})
                with open(pp, "rb") as f:
                    b64 = base64.b64encode(f.read()).decode()
                return self.send_json({"ok": True, "img": "data:image/png;base64," + b64,
                                       "notes": [m for _, m in notes]})
            if path == "/api/update":
                self.read_json()
                ok, v = engine.update_ytdlp()
                get_info(refresh=True)
                return self.send_json({"ok": ok, "version": v})
            if path == "/api/upload":
                return self.handle_upload(q)
        except Exception as e:
            self.close_connection = True
            return self.send_json({"ok": False, "msg": f"{e.__class__.__name__}: {str(e)[:200]}"}, 500)
        return self.fail(404, "not found")

    def handle_upload(self, q):
        kind = (q.get("kind") or ["video"])[0]
        if kind not in ("video", "music", "cookies"):
            return self.fail(400, "bad kind")
        name = (q.get("name") or ["file"])[0]
        name = re.sub(r"[^\w.\-]+", "_", name, flags=re.U)[-80:] or "file"
        n = int(self.headers.get("Content-Length") or 0)
        if n <= 0:
            return self.fail(400, "empty")
        os.makedirs(engine.UPLOADS, exist_ok=True)
        dest = os.path.join(engine.UPLOADS, f"{kind}_{int(time.time())}_{name}")
        left = n
        try:
            with open(dest, "wb") as f:
                while left > 0:
                    chunk = self.rfile.read(min(1 << 20, left))
                    if not chunk:
                        break
                    f.write(chunk)
                    left -= len(chunk)
        except OSError as e:
            self.close_connection = True
            return self.send_json({"ok": False, "msg": f"مفيش مساحة كفاية؟ ({e.__class__.__name__})"}, 500)
        if left > 0:
            try:
                os.remove(dest)
            except OSError:
                pass
            self.close_connection = True
            return self.send_json({"ok": False, "msg": "الرفع اتقطع"}, 400)
        return self.send_json({"ok": True, "path": dest, "name": name})


def main():
    engine.init_dirs()
    engine.prune_uploads()
    try:
        srv = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    except OSError:
        print(f"البورت {PORT} مشغول — غالبًا التطبيق شغّال بالفعل.")
        return
    srv.daemon_threads = True
    print(f"🎬 صانع الريلز شغّال على http://127.0.0.1:{PORT}", flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
