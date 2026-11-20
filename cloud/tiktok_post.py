#!/usr/bin/env python3
"""ينشر ريلز على حسابات تك توك بتاعتك من سيرفر GitHub (متصفح عادي بيدخل ويرفع زي المستخدم).
- الحسابات في Secrets باسم TT_ACC_<اسم> وقيمتها {"user":"...","pass":"..."} (مشفّرة عند GitHub).
- بعد أول دخول بتتحفظ الجلسة (كوكيز) في كاش المستودع، فالدخول بالباسورد بيحصل نادرًا.
- لو التك توك طلب كابتشا/تحقق/كود 2FA: بنقف عند الحساب ده ونسجّل السبب. مش بنحاول نتخطّاه.
ملاحظة: واجهة التك توك بتتغيّر، ولو أي عنصر اتغيّر هتلاقي السبب في التقرير."""
import glob, json, os, re, sys, time
from pathlib import Path

from playwright.sync_api import sync_playwright

STATE = Path(os.environ.get("TT_STATE_DIR", "tt_state"))
SHOTS = Path("tt_shots")
UPLOAD = "https://www.tiktok.com/tiktokstudio/upload"
LOGIN = "https://www.tiktok.com/login/phone-or-email/email"
GAP = int(os.environ.get("TT_GAP", "45"))          # ثواني بين كل نشرتين
CAPTION = os.environ.get("TT_CAPTION", "").strip()
DIR = os.environ.get("TT_DIR", "")
STATE.mkdir(exist_ok=True)
SHOTS.mkdir(exist_ok=True)


class Blocked(Exception):
    pass


def log(m):
    print(m, flush=True)


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
    if page.locator(sel).count() > 0 or any(
        w in txt for w in ("drag the slider", "verify it's you", "verification code", "enter the code", "too many attempts")
    ):
        raise Blocked("التك توك طلب تحقق (كابتشا/كود). استخدم زر «تسجيل الدخول عبر صفحة التك توك» في التطبيق")



POPUP_BTNS = ("Got it", "Not now", "Maybe later", "Skip", "Dismiss", "OK", "Cancel")
EDITOR_SELS = ('div[contenteditable="true"]', 'div[contenteditable="plaintext-only"]',
               '.public-DraftEditor-content', '[data-e2e="caption-editor"] [contenteditable]')
NOTES = []


def dismiss_popups(page):
    """يقفل أي نافذة منبثقة (شرح/تنبيه) بتغطّي خانة الوصف. مابنوافقش على أي تفعيل."""
    for fr in page.frames:
        for label in POPUP_BTNS:
            try:
                b = fr.get_by_role("button", name=label, exact=True)
                if b.count() > 0 and b.first.is_visible():
                    b.first.click(timeout=2000)
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


def has_upload(page):
    page.goto(UPLOAD, wait_until="domcontentloaded")
    page.wait_for_timeout(6000)
    return "login" not in page.url and find(page, 'input[type="file"]') is not None


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


def post_one(page, video, caption, tag):
    page.goto(UPLOAD, wait_until="domcontentloaded")
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
    page.wait_for_timeout(1500)
    btn.click()
    page.wait_for_timeout(2500)
    for label in ("Post now", "Post", "Continue"):  # نافذة تأكيد لو ظهرت
        d = page.get_by_role("button", name=label, exact=True)
        if d.count() > 0 and d.first.is_visible():
            d.first.click()
            break
    ok = re.compile(r"(video|post).*(uploaded|published|posted)|manage your posts|being uploaded", re.I)
    for _ in range(40):
        page.wait_for_timeout(2500)
        check_block(page)
        if "tiktokstudio/content" in page.url or page.get_by_text(ok).count() > 0:
            return
    shot(page, f"{tag}_post")
    raise Blocked("ماقدرتش أتأكد إن النشر تم")


def main():
    vids = sorted(glob.glob(os.path.join(DIR, "reel_*.mp4")))
    accs = accounts()
    res = {"ok": 0, "fail": 0, "notes": []}
    if not vids or not accs:
        res["notes"].append("مفيش فيديوهات أو مفيش حسابات متضافة")
        write(res, len(vids), len(accs))
        return
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = {}
        for a in accs:
            sf = STATE / f"{a['name']}.json"
            try:
                c = browser.new_context(locale="en-US", viewport={"width": 1280, "height": 900},
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
                    post_one(pg, v, CAPTION, f"{name}_{i}")
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
    res["notes"].extend(NOTES)
    write(res, len(vids), len(accs))


def write(res, nv, na):
    line = f"نُشر {res['ok']} (فيديوهات {nv} × حسابات {na})"
    if res["fail"] or res["notes"]:
        line += f" — فشل {res['fail']}"
    body = line + ("\n" + "\n".join(res["notes"][:8]) if res["notes"] else "")
    Path("tt_result.txt").write_text(body, encoding="utf-8")
    log(body)


if __name__ == "__main__":
    main()
