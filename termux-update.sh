#!/data/data/com.termux/files/usr/bin/bash
# تحديث كامل بأمر واحد: يرفع النسخة المطوّرة لمستودعك، يبني التطبيق على سيرفر GitHub المجاني،
# وينزّل ReelsMaker.apk جاهز في «التنزيلات». (التوكن بيتسأل عنه مرة واحدة بس لو مش موجود.)
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE" || exit 1
export BROWSER=termux-open-url
START=$(date +%s)
die() { echo; echo "❌ $*"; exit 1; }

echo "🎬 تحديث صانع الريلز — حوالي 5 لـ 8 دقايق (أغلبها بناء التطبيق على السيرفر)"
echo

# 1) مجلد التنزيلات
D=""
for c in "$HOME/storage/downloads" "/sdcard/Download" "/storage/emulated/0/Download"; do
  [ -d "$c" ] && [ -w "$c" ] && { D="$c"; break; }
done
if [ -z "$D" ]; then
  echo "1/6 صلاحية التخزين (اضغط «سماح» لو ظهرت نافذة)..."
  termux-setup-storage; sleep 6
  for c in "$HOME/storage/downloads" "/sdcard/Download" "/storage/emulated/0/Download"; do
    [ -d "$c" ] && [ -w "$c" ] && { D="$c"; break; }
  done
fi
[ -n "$D" ] || die "مفيش صلاحية للتخزين. اكتب: termux-setup-storage  واضغط سماح، وبعدين شغّل الأمر تاني."

# 2) الأدوات
echo "2/6 تجهيز الأدوات..."
pkg install -y git gh curl termux-tools >/dev/null 2>&1 || pkg install -y git gh curl || die "فشل تثبيت الأدوات. جرّب: termux-change-repo ثم شغّل الأمر تاني."

# 3) الدخول لـ GitHub
if ! gh auth status >/dev/null 2>&1; then
  echo
  echo "🔐 هيفتح المتصفح — الصق الكود اللي هيظهر هنا في صفحة GitHub واضغط Authorize"
  gh auth login --hostname github.com --git-protocol https --web --scopes repo,workflow || die "فشل تسجيل الدخول."
fi
gh auth setup-git >/dev/null 2>&1
LOGIN=$(gh api user --jq .login 2>/dev/null) || die "مقدرتش أقرا حسابك. شغّل: gh auth login"
REPO="${REELS_REPO:-$LOGIN/reels-maker}"
git config --global user.name "$LOGIN"
git config --global user.email "$LOGIN@users.noreply.github.com"
echo "3/6 الحساب: $LOGIN — المستودع: $REPO"

# 4) رفع النسخة المطوّرة (بيحافظ على تاريخ المستودع)
echo "4/6 رفع التحديث..."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
if gh repo view "$REPO" >/dev/null 2>&1; then
  gh repo clone "$REPO" "$TMP/r" -- --depth 1 -q || die "فشل تنزيل المستودع."
  ( cd "$TMP/r" && git rm -rq --ignore-unmatch . >/dev/null 2>&1; find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} + )
else
  mkdir -p "$TMP/r" && ( cd "$TMP/r" && git init -q -b main )
fi
tar -C "$HERE" --exclude=./.git --exclude=./output --exclude=./work --exclude=./cache --exclude=./uploads \
    --exclude=./server.log --exclude='*.log' -cf - . | tar -C "$TMP/r" -xf -
(
  cd "$TMP/r" || exit 1
  git add -A
  if git diff --cached --quiet; then
    echo "   (مفيش تغييرات جديدة للرفع — هكمّل للبناء)"
  else
    git commit -q -m "reels maker v4.5: facebook pages posting + tiktok" || exit 1
    if gh repo view "$REPO" >/dev/null 2>&1; then
      git remote get-url origin >/dev/null 2>&1 || git remote add origin "https://github.com/$REPO.git"
      git push -q origin HEAD:main || exit 1
    else
      gh repo create "${REPO##*/}" --public --source=. --remote=origin --push || exit 1
    fi
  fi
) || die "فشل رفع التحديث للمستودع."
[ "${REELS_PUBLIC:-0}" = 1 ] && gh repo edit "$REPO" --visibility public --accept-visibility-change-consequences >/dev/null 2>&1
gh api -X PUT "repos/$REPO/actions/permissions/workflow" -f default_workflow_permissions=write >/dev/null 2>&1 \
  || echo "⚠️ اضبط يدويًا: Settings ← Actions ← General ← Workflow permissions ← Read and write"

# 5) مفتاح التطبيق: بيتحفظ على موبايلك ويتفحص كل مرة. لو اتمسح أو انتهى من GitHub بيطلب واحد جديد لوحده.
KF="$HOME/.reels-app-token"
tok_ok() { # $1 = توكن
  [ -n "$1" ] && [ "$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $1" \
    -H "Accept: application/vnd.github+json" "https://api.github.com/repos/$REPO/actions/workflows" || true)" = "200" ]
}
TOKEN=""
[ -f "$KF" ] && [ "${RESET_TOKEN:-0}" != 1 ] && TOKEN=$(cat "$KF")
if tok_ok "$TOKEN"; then
  echo "5/6 مفتاح التطبيق شغّال ✓"
else
  TOKEN=""
  echo
  echo "🔑 5/6 محتاجين مفتاح جديد (مرة واحدة بس، وبعدها بيتحفظ ويتجدد بنفسه لما يلزم):"
  echo "   هيفتح صفحة GitHub. اعمل:"
  echo "     • Repository access  ←  Only select repositories  ←  reels-maker"
  echo "     • Permissions  ←  Actions: Read and write   (الصلاحية دي بس)"
  echo "     • اختار أطول مدة صلاحية، واضغط Generate token وانسخ التوكن (بيبدأ بـ github_pat_)"
  sleep 3
  termux-open-url "https://github.com/settings/personal-access-tokens/new?name=reels-app&description=ReelsMaker+app&expires_in=365&actions=write" || true
  for try in 1 2 3; do
    printf "الصق التوكن هنا (مش هيظهر وإنت بتلصق) ثم Enter: "
    read -rs TOKEN; echo
    tok_ok "$TOKEN" && break
    echo "❌ التوكن مش شغّال. اتأكد من الصلاحيتين ومن اختيار reels-maker."
    TOKEN=""
  done
  [ -n "$TOKEN" ] || die "وقفنا. اعمل التوكن صح وشغّل الأمر تاني."
  ( umask 077; printf '%s' "$TOKEN" > "$KF" )
fi
# نحدّث الـ Secret دايمًا بالتوكن السليم (عشان لو التوكن القديم اتحذف من GitHub، التطبيق الجديد ياخد الجديد)
gh secret set APP_TOKEN -R "$REPO" --body "$TOKEN" >/dev/null || die "فشل حفظ التوكن."
unset TOKEN

# 5.5) مفتاح تشفير حسابات التك توك (بيتعمل مرة واحدة: العام جوه التطبيق، والخاص عند GitHub بس)
if [ -z "$(gh variable get TT_PUB -R "$REPO" 2>/dev/null)" ] || [ "${RESET_KEYS:-0}" = 1 ]; then
  echo "🔐 بعمل مفتاح تشفير لكلمات سر التك توك (مرة واحدة)..."
  pkg install -y openssl-tool >/dev/null 2>&1 || true
  KD="$(mktemp -d)"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KD/priv.pem" >/dev/null 2>&1 || die "فشل توليد المفتاح (جرّب: pkg install openssl-tool)"
  PUB=$(openssl pkey -in "$KD/priv.pem" -pubout -outform DER 2>/dev/null | base64 -w0)
  [ -n "$PUB" ] || die "فشل استخراج المفتاح العام."
  gh secret set TT_PRIV -R "$REPO" < "$KD/priv.pem" >/dev/null || die "فشل حفظ المفتاح الخاص."
  gh variable set TT_PUB -R "$REPO" --body "$PUB" >/dev/null || die "فشل حفظ المفتاح العام."
  rm -rf "$KD"
  [ "${RESET_KEYS:-0}" = 1 ] && echo "⚠️ المفتاح اتغيّر: أعد إضافة حسابات التك توك من التطبيق."
else
  echo "🔐 مفتاح التشفير موجود ✓"
fi

# 6) بناء التطبيق على السيرفر وتنزيله
echo
echo "6/6 🔨 بناء التطبيق على سيرفر GitHub (حوالي 4 دقايق)..."
BEFORE=$(gh run list -R "$REPO" --workflow=apk.yml --limit 1 --json databaseId --jq '.[0].databaseId // 0' 2>/dev/null || echo 0)
OKD=0
for _ in $(seq 1 12); do
  gh workflow run apk.yml -R "$REPO" >/dev/null 2>&1 && { OKD=1; break; }
  sleep 5
done
[ "$OKD" = 1 ] || die "مقدرتش أبدأ البناء. افتح تبويب Actions في المستودع وشغّل «بناء تطبيق APK» يدويًا."
RID=""
for _ in $(seq 1 24); do
  sleep 5
  RID=$(gh run list -R "$REPO" --workflow=apk.yml --limit 1 --json databaseId --jq '.[0].databaseId // 0' 2>/dev/null || echo 0)
  [ -n "$RID" ] && [ "$RID" != "0" ] && [ "$RID" != "$BEFORE" ] && break
  RID=""
done
[ -n "$RID" ] || die "مقدرتش ألاقي عملية البناء. افتح تبويب Actions في المستودع."
ST=""; CON=""
for i in $(seq 1 150); do
  J=$(gh run view "$RID" -R "$REPO" --json status,conclusion --jq '.status+" "+(.conclusion // "")' 2>/dev/null || echo "")
  ST=${J%% *}; CON=${J#* }
  [ "$ST" = "completed" ] && break
  printf "."; sleep 10
done
echo
[ "$ST" = "completed" ] || die "البناء طوّل أكتر من المتوقع. شوفه من تبويب Actions."
if [ "$CON" != "success" ]; then
  echo "سبب الفشل:"; gh run view "$RID" -R "$REPO" --log-failed 2>/dev/null | tail -n 25
  die "البناء فشل. ابعت لي الأسطر اللي فوق."
fi

gh release download apk-latest -R "$REPO" -p ReelsMaker.apk -D "$D" --clobber >/dev/null 2>&1 || die "فشل تنزيل الـ APK."
SZ=$(stat -c %s "$D/ReelsMaker.apk" 2>/dev/null || echo 0)
[ "$SZ" -gt 100000 ] || die "ملف الـ APK ناقص أو تالف."
echo
echo "✅ خلصنا في $(( ($(date +%s) - START) / 60 )) دقيقة. التطبيق جاهز في التنزيلات: ReelsMaker.apk ($((SZ/1048576)) MB)"
echo "📲 ثبّته فوق النسخة القديمة (نفس التوقيع، فهيتحدّث من غير ما يمسح حاجة)."
echo "🔋 أول مرة افتح التطبيق: اسمح بالإشعارات واضغط «اسمح للتطبيق يشتغل في الخلفية»."
termux-open "$D/ReelsMaker.apk" || true
