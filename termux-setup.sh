#!/data/data/com.termux/files/usr/bin/bash
# أول مرة على ترمكس جديد: يجهّز كل حاجة (تخزين + أدوات + GitHub + بناء التطبيق) بأمر واحد.
set -u
export DEBIAN_FRONTEND=noninteractive
HERE="$(cd "$(dirname "$0")" && pwd)"
die() { echo; echo "❌ $*"; exit 1; }
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock   # عشان الموبايل ما ينامش وإحنا شغّالين
trap 'command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock' EXIT

echo "🎬 تجهيز صانع الريلز لأول مرة — حوالي 10 دقايق (أغلبها انتظار بناء التطبيق)"
echo

echo "1/3 صلاحية التخزين (اضغط «سماح» لو ظهرت نافذة)..."
if [ ! -d "$HOME/storage/downloads" ]; then
  termux-setup-storage
  for _ in $(seq 1 30); do [ -d "$HOME/storage/downloads" ] && break; sleep 2; done
fi
[ -d "$HOME/storage/downloads" ] || die "مفيش صلاحية للتخزين. اكتب: termux-setup-storage  واضغط سماح، وشغّل الأمر تاني."

echo "2/3 تحديث الحزم وتثبيت الأدوات (ممكن ياخد كام دقيقة)..."
pkg update -y -o Dpkg::Options::=--force-confnew >/dev/null 2>&1 || pkg update -y || die "فشل تحديث الحزم. جرّب: termux-change-repo ثم شغّل الأمر تاني."
pkg install -y unzip git gh curl openssl-tool termux-tools >/dev/null 2>&1 || pkg install -y unzip git gh curl openssl-tool termux-tools || die "فشل تثبيت الأدوات."

echo "3/3 حسابك على GitHub والبناء..."
exec bash "$HERE/termux-update.sh" "$@"
