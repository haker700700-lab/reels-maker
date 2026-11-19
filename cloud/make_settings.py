#!/usr/bin/env python3
"""بيحوّل مدخلات GitHub Actions (من متغيرات البيئة) لملف settings.json — من غير ما نحط نصوص المستخدم في أمر shell."""
import hashlib, json, os

def env(k, d=""):
    return os.environ.get(k, d)

def yes(k, d="false"):
    return env(k, d).strip().lower() in ("1", "true", "yes", "on")

S = {
    "url": env("IN_URL").strip(),
    "n": int(env("IN_N", "5") or 5),
    "min_l": int(env("IN_MIN", "15") or 15),
    "max_l": int(env("IN_MAX", "25") or 25),
    "res": env("IN_RES", "1080"),
    "ratio": env("IN_RATIO", "9:16"),
    "pick": env("IN_PICK", "ذكي (أحسن اللحظات)"),
    "reverse": yes("IN_REVERSE"),
    "use_text": bool(env("IN_TEXT").strip()),
    "text": env("IN_TEXT"),
    "music_on": yes("IN_MUSIC", "false"),
    # الخصائص: crop=قص بالطول • best=أحسن اللحظات • face=تتبّع الوجوه • enhance=تحسين الصورة • orig=صوت الفيديو
    # denoise=تنقية الصوت • fade=تلاشي ناعم • musicfile=الموسيقى ملف منفصل • none=ولا خاصية
    "feat": env("IN_FEAT").strip() or "crop,best,face,enhance,orig",
    "rights": yes("IN_RIGHTS"),
}
cookies = env("YT_COOKIES")
if cookies.strip():
    with open("cookies.txt", "w", encoding="utf-8") as f:
        f.write(cookies)
    S["cookies_file"] = os.path.abspath("cookies.txt")
with open("settings.json", "w", encoding="utf-8") as f:
    json.dump(S, f, ensure_ascii=False)
print("settings ok (الرابط مش بيتطبع عشان المستودع عام)")

# بصمة الطلب (الرابط + الإعدادات): نفس الرابط بنفس الإعدادات = نفس الريلز، فمنعيدش الشغل ولا بنكرر حاجة
key = json.dumps([S["url"], S["n"], S["min_l"], S["max_l"], S["res"], S["reverse"], S["text"], S["music_on"], S["feat"]],
                 ensure_ascii=False, sort_keys=True)
digest = hashlib.sha256(key.encode("utf-8")).hexdigest()[:20]
out = os.environ.get("GITHUB_OUTPUT")
if out:
    with open(out, "a", encoding="utf-8") as f:
        f.write("hash=" + digest + "\n")
