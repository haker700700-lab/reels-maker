#!/data/data/com.termux/files/usr/bin/bash
# أمر واحد يعمل كل حاجة: يلاقي آخر reels-maker*.zip في التنزيلات، يفكّه، يرفعه لـ GitHub،
# يبني التطبيق، وينزّل ReelsMaker.apk جاهز في «التنزيلات».
set -u
D=""
for c in "$HOME/storage/downloads" "/sdcard/Download" "/storage/emulated/0/Download"; do
  [ -d "$c" ] && [ -r "$c" ] && { D="$c"; break; }
done
if [ -z "$D" ]; then
  termux-setup-storage; sleep 6
  for c in "$HOME/storage/downloads" "/sdcard/Download" "/storage/emulated/0/Download"; do
    [ -d "$c" ] && [ -r "$c" ] && { D="$c"; break; }
  done
fi
[ -n "$D" ] || { echo "❌ مفيش صلاحية للتخزين. اضغط «سماح» وشغّل الأمر تاني."; exit 1; }
pkg install -y unzip >/dev/null 2>&1 || true
ZIP=$(ls -t "$D"/reels-maker*.zip 2>/dev/null | head -n1)
if [ -n "$ZIP" ]; then
  echo "📦 هستخدم: $ZIP"
  unzip -oq "$ZIP" -d "$HOME" || { echo "❌ فشل فك الضغط"; exit 1; }
fi
[ -f "$HOME/reels-maker-main/termux-update.sh" ] || { echo "❌ حط ملف reels-maker*.zip في التنزيلات وشغّل تاني."; exit 1; }
exec bash "$HOME/reels-maker-main/termux-update.sh"
