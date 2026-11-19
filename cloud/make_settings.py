#!/usr/bin/env python3
"""بيحوّل مدخلات GitHub Actions (من متغيرات البيئة) لملف settings.json — من غير ما نحط نصوص المستخدم في أمر shell."""
import json, os

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
    "music_on": yes("IN_MUSIC", "true"),
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
