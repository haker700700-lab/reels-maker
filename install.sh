#!/data/data/com.termux/files/usr/bin/bash
# تجهيز صانع الريلز على Termux — مرة واحدة بس. كل الأدوات مفتوحة المصدر.
cd "$(dirname "$0")" || exit 1
DIR="$(pwd)"
export DEBIAN_FRONTEND=noninteractive

echo "🎬 تجهيز صانع الريلز (مرة واحدة — حوالي 3 لـ 8 دقايق حسب النت)"
echo

echo "1/5 صلاحية الذاكرة (لو ظهرت نافذة اضغط «سماح»)..."
[ -d "$HOME/storage" ] || { termux-setup-storage; sleep 6; }

echo "2/5 تحديث قوائم الحزم..."
pkg update -y -o Dpkg::Options::="--force-confold" >/dev/null 2>&1 || apt update -y >/dev/null 2>&1

echo "3/5 تثبيت Python و FFmpeg..."
if ! pkg install -y -o Dpkg::Options::="--force-confold" python ffmpeg python-numpy python-pillow curl; then
  echo
  echo "❌ فشل تثبيت الحزم الأساسية. غالبًا مشكلة في مرآة التحميل."
  echo "   اكتب: termux-change-repo   اختار أي مرآة تانية، وبعدين شغّل: bash install.sh"
  exit 1
fi
# اختياري: تشكيل عربي أفضل + مشغّل JS ليوتيوب
pkg install -y libraqm >/dev/null 2>&1 || true
pkg install -y nodejs >/dev/null 2>&1 || pkg install -y nodejs-lts >/dev/null 2>&1 || true

echo "4/5 تثبيت yt-dlp ومكتبات النص العربي..."
python3 -m pip install --no-input -q -U yt-dlp || { echo "❌ فشل تثبيت yt-dlp"; exit 1; }
for p in requests six fonttools arabic-reshaper "python-bidi==0.4.2" gdown; do
  python3 -m pip install --no-input -q "$p" || echo "   ⚠️ تعذّر تثبيت $p (مش مشكلة لو مش أساسي)"
done

echo "5/5 تحميل الخطوط العربية..."
python3 engine.py --fonts

# أمر مختصر: reels
printf '#!/data/data/com.termux/files/usr/bin/sh\nexec bash "%s/start.sh" "$@"\n' "$DIR" > "$PREFIX/bin/reels"
chmod +x "$PREFIX/bin/reels" start.sh
# اختصار لـ Termux:Widget (اختياري)
mkdir -p "$HOME/.shortcuts" && printf '#!/data/data/com.termux/files/usr/bin/bash\nexec bash "%s/start.sh"\n' "$DIR" > "$HOME/.shortcuts/ReelsMaker" && chmod +x "$HOME/.shortcuts/ReelsMaker"
# تشغيل المحرّك تلقائي أول ما تفتح Termux
if ! grep -q "reels-maker autostart" "$HOME/.bashrc" 2>/dev/null; then
  printf '\n# reels-maker autostart\n[ -x "%s/start.sh" ] && "%s/start.sh" --quiet\n' "$DIR" "$DIR" >> "$HOME/.bashrc"
fi

echo
python3 engine.py --selftest
echo
echo "✅ تمام! هفتحلك التطبيق دلوقتي."
echo "   بعد كده: افتح Termux (بيشغّل المحرّك لوحده) ثم افتح الصفحة، أو اكتب: reels"
sleep 2
bash "$DIR/start.sh"
