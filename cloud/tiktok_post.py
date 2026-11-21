#!/usr/bin/env python3
"""ينشر ريلز على حسابات تك توك بتاعتك من سيرفر GitHub (متصفح عادي بيدخل ويرفع زي المستخدم).
- الحسابات في Secrets باسم TT_ACC_<اسم> وقيمتها {"user":"...","pass":"..."} (مشفّرة عند GitHub).
- بعد أول دخول بتتحفظ الجلسة (كوكيز) في كاش المستودع، فالدخول بالباسورد بيحصل نادرًا.
- لو التك توك طلب كابتشا/تحقق/كود 2FA: بنقف عند الحساب ده ونسجّل السبب. مش بنحاول نتخطّاه.
ملاحظة: واجهة التك توك بتتغيّر، ولو أي عنصر اتغيّر هتلاقي السبب في التقرير."""
import base64, glob, hashlib, io, json, os, re, sys, tarfile, time
from pathlib import Path

from playwright.sync_api import sync_playwright

STATE = Path(os.environ.get("TT_STATE_DIR", "tt_state"))
SHOTS = Path("tt_shots")
# نفس User-Agent اللي اتسجّل بيه الدخول في التطبيق (TikLoginActivity.DESKTOP_UA) + نفس المنطقة الزمنية، عشان الجلسة تبان من نفس "الجهاز"
UA = os.environ.get("TT_UA", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
TZ = os.environ.get("TT_TZ", "Africa/Cairo")
LOCALE = os.environ.get("TT_LOCALE", "en-US")      # en-US أو ar-EG — الأزرار والنصوص بتتعرّف بالاتنين
UPLOAD = "https://www.tiktok.com/tiktokstudio/upload"
# سلسلة بدائل للرفع (بالترتيب): لو الأولى فشلت قبل ما نضغط «نشر» بنجرّب اللي بعدها. بتتغيّر بـ TT_UPLOAD_URLS (مفصولة بمسافة).
UPLOAD_URLS = os.environ.get("TT_UPLOAD_URLS", "").split() or [
    "https://www.tiktok.com/tiktokstudio/upload?from=webapp",
    "https://www.tiktok.com/creator-center/upload?from=upload",
    "https://www.tiktok.com/upload?lang=en",
]
LOGIN = "https://www.tiktok.com/login/phone-or-email/email"
GAP = int(os.environ.get("TT_GAP", "45"))          # ثواني بين كل نشرتين
CAPTION = os.environ.get("TT_CAPTION", "").strip()
DIR = os.environ.get("TT_DIR", "")
STATE_ENC = Path(os.environ.get("TT_STATE_ENC", "tt_state.enc"))   # الجلسة بتتحفظ في الكاش مشفّرة (المستودع ممكن يكون عام)
STATE.mkdir(exist_ok=True)
SHOTS.mkdir(exist_ok=True)


class Blocked(Exception):
    pass


def log(m):
    print(m, flush=True)


def _fernet():
    from cryptography.fernet import Fernet
    return Fernet(base64.urlsafe_b64encode(hashlib.sha256(os.environ["TT_PRIV"].encode()).digest()))


def restore_state():
    """بيفك تشفير الجلسة المحفوظة من التشغيلة اللي فاتت (لو موجودة وبنفس المفتاح)."""
    try:
        if STATE_ENC.exists():
            raw = _fernet().decrypt(STATE_ENC.read_bytes())
            with tarfile.open(fileobj=io.BytesIO(raw), mode="r:gz") as t:
                for m in t.getmembers():
                    if m.isfile() and "/" not in m.name and ".." not in m.name:
                        t.extract(m, STATE)
            log("♻️ رجّعت جلسات التك توك المحفوظة")
    except Exception:
        log("⚠️ ماقدرتش أرجّع الجلسات المحفوظة (هنبدأ بالكوكيز الجديدة)")


def save_state():
    """بيشفّر الجلسات ويحفظها في ملف واحد للكاش، ويمسح النسخة الصريحة."""
    try:
        buf = io.BytesIO()
        with tarfile.open(fileobj=buf, mode="w:gz") as t:
            for f in STATE.glob("*.json"):
                t.add(f, arcname=f.name)
        STATE_ENC.write_bytes(_fernet().encrypt(buf.getvalue()))
    except Exception:
        log("⚠️ ماقدرتش أحفظ الجلسات")
    for f in STATE.glob("*.json"):
        try:
            f.unlink()
        except Exception:
            pass


def accounts():
    """بيفك تشفير الحسابات اللي بعتها التطبيق (RSA) بالمفتاح الخاص من Secret TT_PRIV."""
    import base64
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding
    try:
        items = json.loads(os.environ.get("TT_ACCS", "[]"))
        key = serialization.load_pem_private_key(os.environ["TT_PRIV"].encode(), None)
    except Exception:
        log("⚠️ مفيش حسابات، أو مفتاح فك التشفير (TT_PRIV) ناقص")
        return []
    oaep = padding.OAEP(mgf=padding.MGF1(hashes.SHA1()), algorithm=hashes.SHA1(), label=None)
    out = []
    for it in items:
        if it.get("t") == "api":      # حسابات التوكن بيتولّاها tiktok_api.py
            continue
        try:
            if "k" in it:   # جلسة (كوكيز) من تسجيل الدخول جوه التطبيق — تشفير هجين
                from cryptography.hazmat.primitives.ciphers.aead import AESGCM
                raw = base64.b64decode(it["k"])
                kl = key.key_size // 8
                aes = key.decrypt(raw[:kl], oaep)
                data = json.loads(AESGCM(aes).decrypt(raw[kl:kl + 12], raw[kl + 12:], None).decode("utf-8"))
                name = re.sub(r"[^A-Za-z0-9_-]", "", str(it.get("n", ""))) or f"acc{len(out) + 1}"
                out.append({"name": name, "cookies": data["cookies"]})
                continue
            user, pw = key.decrypt(base64.b64decode(it["e"]), oaep).decode("utf-8").split("\n", 1)
            name = re.sub(r"[^A-Za-z0-9_-]", "", str(it.get("n", ""))) or f"acc{len(out) + 1}"
            out.append({"name": name, "user": user, "pass": pw})
            print(f"::add-mask::{pw}")
        except Exception:
            log(f"⚠️ الحساب {it.get('n', '?')}: مش قادر أفك تشفيره (أضفه تاني من التطبيق لو المفتاح اتغيّر)")
    return out


def shot(page, name):
    try:
        page.screenshot(path=str(SHOTS / f"{name}.png"))
    except Exception:
        pass


def check_block(page):
    """كابتشا أو تحقق إضافي: نقف ونبلّغ، مش بنحل حاجة."""
    try:
        txt = page.inner_text("body", timeout=3000).lower()
    except Exception:
        txt = ""
    sel = "#captcha_container, .captcha-verify-container, [class*='captcha'], iframe[src*='captcha']"
    if page.locator(sel).count() > 0 or any(w in txt for w in BLOCK_WORDS):
        raise Blocked("التك توك طلب تحقق (كابتشا/كود). سجّل الدخول تاني من زر «تسجيل الدخول عبر صفحة التك توك» في التطبيق")



# كل النصوص هنا عربي + إنجليزي (واجهة الاستوديو بتتغيّر حسب لغة الحساب). الأزرار الأساسية بنعتمد عليها بـ data-e2e (مش بالنص).
POPUP_BTNS = ("Got it", "Not now", "Maybe later", "Skip", "Dismiss", "Close", "OK",
              "حسنًا", "حسناً", "حسنا", "فهمت", "فهمت ذلك", "موافق", "ليس الآن", "ليس الان", "لاحقًا", "لاحقاً", "لاحقا",
              "تخطي", "تخطّي", "تجاهل", "إغلاق", "اغلاق", "إغلاق")
POST_CONFIRM = ("Post now", "Post", "Continue", "Publish anyway",
                "انشر الآن", "نشر الآن", "انشر الان", "نشر", "انشر", "متابعة", "استمرار", "تابع", "المتابعة")
DONE_LABELS = ("Manage your posts", "Upload another video", "View profile",
               "إدارة منشوراتك", "إدارة المنشورات", "ادارة المنشورات", "تحميل فيديو آخر", "رفع فيديو آخر",
               "تحميل فيديو اخر", "رفع فيديو اخر", "عرض الملف الشخصي", "عرض الحساب")
BLOCK_WORDS = ("drag the slider", "verify it's you", "verification code", "enter the code", "too many attempts",
               "اسحب", "رمز التحقق", "أدخل الرمز", "ادخل الرمز", "التحقق من هويتك", "تحقق من أنك", "محاولات كثيرة", "عدد كبير جدًا من المحاولات", "حل الأحجية", "أكمل الأحجية")
EMPTY_RX = re.compile(r"no (content|posts|videos)|haven.t (posted|uploaded)|nothing (here|yet)|no results|"
                      r"لا (يوجد|توجد) (محتوى|منشورات|فيديوهات)|لم (تنشر|تقم بنشر|تقم برفع)|لا شيء|لا نتائج", re.I)


def btn_by_labels(root, labels, role="button"):
    """بيرجّع أول زر ظاهر اسمه واحد من الأسماء (مطابقة كاملة بعد تنضيف المسافات، بغض النظر عن حالة الحروف)."""
    rx = re.compile(r"^\s*(" + "|".join(re.escape(x) for x in labels) + r")\s*$", re.I)
    loc = root.get_by_role(role, name=rx)
    try:
        for i in range(loc.count()):
            if loc.nth(i).is_visible():
                return loc.nth(i)
    except Exception:
        pass
    return None

EDITOR_SELS = ('div[contenteditable="true"]', 'div[contenteditable="plaintext-only"]',
               '.public-DraftEditor-content', '[data-e2e="caption-editor"] [contenteditable]')
NOTES = []


def dismiss_popups(page):
    """يقفل أي نافذة منبثقة (شرح/تنبيه) بتغطّي خانة الوصف. مابنوافقش على أي تفعيل."""
    for fr in page.frames:
        try:
            b = btn_by_labels(fr, POPUP_BTNS)
            if b is not None:
                b.click(timeout=2000)
                page.wait_for_timeout(500)
        except Exception:
            pass


def visible_editor(page, wait_s=25):
    """يدوّر على خانة الوصف الظاهرة فعلاً (مش أول عنصر مخفي) في الصفحة وكل الـ frames."""
    for _ in range(wait_s):
        dismiss_popups(page)
        for fr in page.frames:
            for sel in EDITOR_SELS:
                loc = fr.locator(sel)
                try:
                    n = loc.count()
                except Exception:
                    continue
                for i in range(n):
                    el = loc.nth(i)
                    try:
                        if el.is_visible():
                            return el
                    except Exception:
                        pass
        page.wait_for_timeout(1000)
    return None


def fill_caption(page, caption, tag):
    ed = visible_editor(page)
    if ed is None:
        shot(page, f"{tag}_caption")
        NOTES.append(f"{tag}: خانة الوصف ماظهرتش، اتنشر من غير وصف. " + diag(page))
        return
    try:
        ed.scroll_into_view_if_needed(timeout=5000)
        ed.click(timeout=8000)
    except Exception:
        try:
            ed.click(timeout=5000, force=True)
        except Exception:
            ed.evaluate("e => e.focus()")
    page.keyboard.press("Control+A")
    page.keyboard.press("Delete")
    page.keyboard.insert_text(caption)   # أسرع وأضمن مع العربي من type()


def click_post(page, tag):
    """يدوس زر النشر بأمان: يعيد البحث عنه (الصفحة بتتحدّث بعد كتابة الوصف)، يقفل النوافذ، ويجرّب بدائل."""
    sel = 'button[data-e2e="post_video_button"]'
    for _ in range(4):
        dismiss_popups(page)
        b = find(page, sel)
        if b is None:
            page.wait_for_timeout(1500)
            continue
        try:
            b.scroll_into_view_if_needed(timeout=4000)
            b.click(timeout=10000)
            return
        except Exception:
            pass
        try:
            b.click(timeout=5000, force=True)
            return
        except Exception:
            pass
        try:
            b.evaluate("e => e.click()")
            return
        except Exception:
            page.wait_for_timeout(2000)
    shot(page, f"{tag}_postbtn")
    raise Blocked("زر النشر مابيستجيبش. " + diag(page))


def find(page, selector):
    for fr in page.frames:
        loc = fr.locator(selector)
        if loc.count() > 0:
            return loc.first
    return None


def diag(page):
    """وصف مختصر لحالة الصفحة (بدون أي بيانات حساسة) عشان نعرف سبب الفشل."""
    bits = []
    try:
        bits.append("صفحة: " + page.url.split("?")[0].replace("https://www.tiktok.com", "")[:60])
        msgs = page.locator('[role="alert"], [class*="rror"], [data-e2e*="error"]').all_inner_texts()
        msgs = [m.strip().replace("\n", " ")[:90] for m in msgs if m.strip()]
        if msgs:
            bits.append("رسالة التك توك: " + " | ".join(msgs[:2]))
        else:
            body = page.inner_text("body", timeout=3000).replace("\n", " ")
            bits.append("نص الصفحة: " + body[:110])
    except Exception:
        pass
    return " — ".join(bits)


def has_upload(page, urls=None):
    """الجلسة شغّالة لو أي صفحة رفع من السلسلة اتفتحت وفيها خانة الملف."""
    for u in (urls or UPLOAD_URLS):
        try:
            page.goto(u, wait_until="domcontentloaded")
            page.wait_for_timeout(6000)
            if "login" not in page.url and find(page, 'input[type="file"]') is not None:
                return True
        except Exception:
            pass
    return False


def ensure_login(page, acc):
    ctx = page.context
    if acc.get("cookies"):
        cks = []
        for part in acc["cookies"].split("; "):
            if "=" in part:
                k, v = part.split("=", 1)
                cks.append({"name": k, "value": v, "domain": ".tiktok.com", "path": "/", "secure": True})
        ctx.add_cookies(cks)
        if has_upload(page):
            return "cookies"
        check_block(page)
        shot(page, f"{acc['name']}_cookies")
        raise Blocked("الجلسة مش مقبولة (انتهت أو التك توك رفضها). سجّل الدخول من التطبيق تاني. " + diag(page))
    if has_upload(page):
        return "session"
    page.goto(LOGIN, wait_until="domcontentloaded")
    page.wait_for_timeout(3000)
    user = page.locator('input[name="username"]')
    pw = page.locator('input[type="password"]')
    if user.count() == 0 or pw.count() == 0:
        shot(page, f"{acc['name']}_login_form")
        raise Blocked("صفحة الدخول مختلفة عن المتوقع. " + diag(page))
    user.first.fill(acc["user"])
    pw.first.fill(acc["pass"])
    page.locator('button[type="submit"], button[data-e2e="login-button"]').first.click()
    page.wait_for_timeout(9000)
    check_block(page)
    d_after = diag(page)
    if not has_upload(page):
        check_block(page)
        shot(page, f"{acc['name']}_after_login")
        raise Blocked("الدخول ماتمش. " + d_after)
    return "password"


def watch_posts(page):
    """بيسجّل ردود طلبات POST الخاصة بالنشر عشان نعرف الفشل الصريح (حتى لو الواجهة ما أظهرتوش)."""
    seen = []

    def on_resp(r):
        try:
            if r.request.method != "POST" or "tiktok" not in r.url:
                return
            path = r.url.split("?")[0].split("tiktok.com")[-1]
            if not re.search(r"post|publish|create|submit", path, re.I) or re.search(r"log|report|monitor|collect|stat|event", path, re.I):
                return
            item = {"path": path[:70], "status": r.status, "code": None, "msg": ""}
            try:
                j = r.json()
                if isinstance(j, dict):
                    c = j.get("status_code", j.get("statusCode", j.get("code")))
                    item["code"] = c
                    item["msg"] = str(j.get("status_msg") or j.get("message") or j.get("msg") or "")[:90]
            except Exception:
                pass
            seen.append(item)
        except Exception:
            pass

    page.on("response", on_resp)
    return seen


def post_failed(seen):
    """أول رد فيه خطأ صريح (HTTP >= 400 أو كود غير صفري)."""
    for it in seen:
        if it["status"] >= 400 or (it["code"] not in (None, 0, "0", 200, "200", "ok")):
            return f"التك توك رفض النشر ({it['path']} HTTP {it['status']} كود {it['code']}) {it['msg']}".strip()
    return ""


def post_one(page, video, caption, tag, url=UPLOAD, state=None):
    state = state if state is not None else {}
    page.goto(url, wait_until="domcontentloaded")
    page.wait_for_timeout(5000)
    check_block(page)
    fi = find(page, 'input[type="file"]')
    if not fi:
        raise Blocked("مش لاقي خانة رفع الفيديو")
    fi.set_input_files(video)
    # استنى لحد ما الرفع يخلص ويبقى زر النشر شغّال
    btn = None
    for _ in range(100):  # حتى ~5 دقايق
        page.wait_for_timeout(3000)
        check_block(page)
        b = find(page, 'button[data-e2e="post_video_button"]')
        if b and b.get_attribute("aria-disabled") != "true" and b.is_enabled():
            btn = b
            break
    if not btn:
        shot(page, f"{tag}_upload")
        raise Blocked("الرفع ماخلصش في الوقت المناسب أو زر النشر مش ظاهر")
    if caption:
        fill_caption(page, caption, tag)
    page.wait_for_timeout(2000)
    seen = watch_posts(page)
    state["clicked"] = True      # من هنا ممكن النشر يكون حصل: ممنوع نجرّب بديل (عشان ماننشرش مرتين)
    click_post(page, tag)
    page.wait_for_timeout(2500)
    for _ in range(3):  # نافذة تأكيد لو ظهرت (عربي/إنجليزي) — ممكن يظهروا ورا بعض
        d = btn_by_labels(page, POST_CONFIRM)
        if d is None:
            break
        try:
            d.click(timeout=5000)
        except Exception:
            break
        page.wait_for_timeout(2500)
    # نجاح حقيقي = اتحوّلنا لصفحة المحتوى، أو ظهرت نافذة التأكيد بأزرارها (مش مجرد نص "uploaded" على صفحة الرفع)
    confirmed = False
    for _ in range(40):
        page.wait_for_timeout(2500)
        check_block(page)
        if "tiktokstudio/content" in page.url:
            confirmed = True
            break
        if btn_by_labels(page, DONE_LABELS) is not None or btn_by_labels(page, DONE_LABELS, "link") is not None:
            confirmed = True
            break
    bad = post_failed(seen)
    if bad:
        state["clicked"] = False     # رفض صريح من التك توك = ماتنشرش، فآمن نجرّب بديل
        shot(page, f"{tag}_post")
        raise Blocked(bad)
    if not confirmed:
        shot(page, f"{tag}_post")
        raise Blocked("ماقدرتش أتأكد إن النشر تم. " + diag(page) + (f" — ردود: {seen[:2]}" if seen else ""))
    verify_posted(page, tag, caption)


def post_with_fallback(page, video, caption, tag):
    """بيجرّب صفحات الرفع بالترتيب. بيتحوّل للبديل بس لو الفشل حصل قبل الضغط على «نشر» (أو رفض صريح)،
    وبيقف فورًا عند الكابتشا/التحقق، وعند أي غموض بعد الضغط (عشان مانكرّرش النشر)."""
    errs = []
    for k, url in enumerate(UPLOAD_URLS):
        state = {"clicked": False}
        try:
            post_one(page, video, caption, tag, url, state)
            if k:
                NOTES.append(f"{tag}: اترفع عن طريق الصفحة البديلة رقم {k + 1}")
            return
        except Exception as e:
            msg = str(e)
            if state["clicked"] or (isinstance(e, Blocked) and "تحقق" in msg):
                raise
            errs.append(f"[{k + 1}] {msg[:110]}")
            log(f"↪️ {tag}: صفحة الرفع {k + 1} فشلت ({msg[:80]}) — بجرّب اللي بعدها")
            shot(page, f"{tag}_try{k + 1}")
    raise Blocked("كل صفحات الرفع فشلت: " + " | ".join(errs))


def verify_posted(page, tag, caption=""):
    """يفتح صفحة المحتوى ويتأكد إن فيه فيديوهات فعلاً، ويسجّل حالة الصفحة في التقرير عشان نشوف اللي حصل."""
    empty = EMPTY_RX
    txt = ""
    for _ in range(3):
        page.goto("https://www.tiktok.com/tiktokstudio/content", wait_until="domcontentloaded")
        page.wait_for_timeout(8000)
        try:
            txt = page.inner_text("body", timeout=4000).replace("\n", " ")
        except Exception:
            txt = ""
        if txt and not empty.search(txt):
            break
    shot(page, f"{tag}_content")
    NOTES.append(f"{tag} حالة المحتوى: " + txt[:150])
    if not txt or empty.search(txt):
        raise Blocked("اتضغط نشر بس الفيديو مش ظاهر في صفحة المحتوى. " + diag(page))
    cap = re.sub(r"[#@]\S+", "", caption).strip()[:12]
    if cap and cap not in txt:
        NOTES.append(f"{tag}: صفحة المحتوى مافيهاش بداية الوصف (ممكن لسه بيتعالج أو الوصف اتقصّ) — اتأكد من الحساب")


def main():
    vids = sorted(glob.glob(os.path.join(DIR, "reel_*.mp4")))
    accs = accounts()
    res = {"ok": 0, "fail": 0, "notes": []}
    if not vids or not accs:
        res["notes"].append("مفيش فيديوهات أو مفيش حسابات متضافة")
        write(res, len(vids), len(accs))
        return
    restore_state()
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = {}
        for a in accs:
            sf = STATE / f"{a['name']}.json"
            try:
                c = browser.new_context(locale=LOCALE, timezone_id=TZ, user_agent=UA, viewport={"width": 1280, "height": 900},
                                        storage_state=str(sf) if sf.exists() else None)
                pg = c.new_page()
                how = ensure_login(pg, a)
                c.storage_state(path=str(sf))
                ctx[a["name"]] = (c, pg)
                log(f"✅ {a['name']}: دخول ({how})")
            except Exception as e:
                res["notes"].append(f"{a['name']}: {str(e)[:260]}")
                log(f"❌ {a['name']}: {e}")
        total = len(vids) * len(ctx)
        first = True
        for i, v in enumerate(vids, 1):      # فيديو فيديو على كل الحسابات
            for name, (c, pg) in list(ctx.items()):
                if not first:
                    time.sleep(GAP)
                first = False
                try:
                    post_with_fallback(pg, v, CAPTION, f"{name}_{i}")
                    res["ok"] += 1
                    log(f"✅ {name}: فيديو {i}/{len(vids)}")
                except Exception as e:
                    res["fail"] += 1
                    res["notes"].append(f"{name} فيديو {i}: {str(e)[:260]}")
                    log(f"❌ {name} فيديو {i}: {e}")
                    if isinstance(e, Blocked) and "تحقق" in str(e):
                        ctx.pop(name, None)  # وقف الحساب ده
            for name, (c, pg) in ctx.items():
                try:
                    c.storage_state(path=str(STATE / f"{name}.json"))
                except Exception:
                    pass
        browser.close()
    save_state()
    res["notes"].extend(NOTES)
    write(res, len(vids), len(accs))


def write(res, nv, na):
    line = f"نُشر {res['ok']} (فيديوهات {nv} × حسابات {na})"
    if res["fail"]:
        line += f" — فشل {res['fail']}"
    body = line + ("\n" + "\n".join(res["notes"][:8]) if res["notes"] else "")
    Path(os.environ.get("TT_RESULT", "tt_result.txt")).write_text(body, encoding="utf-8")
    log(body)


if __name__ == "__main__":
    main()
