#!/data/data/com.termux/files/usr/bin/bash
# تشغيل محرّك الريلز وفتح الصفحة. الخيار --quiet: يشغّل المحرّك من غير ما يفتح المتصفح.
DIR="$(cd "$(dirname "$0")" && pwd)"
PORT="${REELS_PORT:-8765}"
URL="http://127.0.0.1:$PORT"
QUIET=0; [ "${1:-}" = "--quiet" ] && QUIET=1

up() { curl -fs --max-time 2 "$URL/api/ping" >/dev/null 2>&1; }

if ! up; then
  (cd "$DIR" && nohup python3 server.py >server.log 2>&1 &)
  for _ in $(seq 1 24); do up && break; sleep 0.5; done
fi

if [ $QUIET -eq 0 ]; then
  if up; then
    echo "🎬 جاهز: $URL"
    if command -v termux-open-url >/dev/null 2>&1; then termux-open-url "$URL"
    else am start -a android.intent.action.VIEW -d "$URL" >/dev/null 2>&1; fi
  else
    echo "❌ المحرّك ما اشتغلش. شوف السبب: cat $DIR/server.log"
  fi
fi
