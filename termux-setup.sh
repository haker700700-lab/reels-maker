#!/data/data/com.termux/files/usr/bin/bash
# تجهيز كامل: دخول GitHub + مستودع عام (بلا حدود) + بناء تطبيق APK (من غير أي إعدادات عند المستخدم) + تنزيله في «التنزيلات»
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"
export BROWSER=termux-open-url

# مجلد التنزيلات
D=""
for c in "$HOME/storage/downloads" "/sdcard/Download" "/storage/emulated/0/Download"; do
  [ -d "$c" ] && [ -w "$c" ] && { D="$c"; break; }
done
[ -n "$D" ] || { echo "❌ مفيش صلاحية للتخزين. اكتب: termux-setup-storage  واضغط سماح، وبعدين شغّل الأمر تاني."; exit 1; }

echo "📦 تثبيت الأدوات..."
pkg install -y git gh curl termux-tools >/dev/null 2>&1 || pkg install -y git gh curl

if ! gh auth status >/dev/null 2>&1; then
  echo
  echo "🔐 هيفتح المتصفح — الصق الكود اللي هيظهر هنا في صفحة GitHub واضغط Authorize"
  gh auth login --hostname github.com --git-protocol https --web --scopes repo,workflow
fi
gh auth setup-git
LOGIN=$(gh api user --jq .login)
REPO="$LOGIN/reels-maker"
git config --global user.name "$LOGIN"
git config --global user.email "$LOGIN@users.noreply.github.com"

echo "☁️ رفع المشروع (مستودع عام: reels-maker)..."
rm -rf .git; git init -q -b main; git add -A; git commit -q -m "reels maker cloud + android app"
if gh repo view "$REPO" >/dev/null 2>&1; then
  git remote add origin "https://github.com/$REPO.git"
  git push -u -f origin main
  gh repo edit "$REPO" --visibility public --accept-visibility-change-consequences >/dev/null 2>&1 || true
else
  gh repo create reels-maker --public --source=. --remote=origin --push
fi
VIS=$(gh repo view "$REPO" --json visibility --jq .visibility 2>/dev/null || echo "?")
echo "   حالة المستودع: $VIS"
gh api -X PUT "repos/$REPO/actions/permissions/workflow" \
  -f default_workflow_permissions=write >/dev/null || echo "⚠️ اضبط Settings ← Actions ← Read and write permissions يدوي"

# ───── التوكن (مرة واحدة، عند صاحب التطبيق بس — المستخدمين مش هيشوفوا حاجة) ─────
echo
echo "🔑 دلوقتي هنعمل مفتاح صغير بيخلّي التطبيق يشغّل الخدمة عند أي حد (إنت بس اللي بتعمله مرة واحدة):"
echo "   هيفتح صفحة GitHub. اعمل:"
echo "     • Repository access  ←  Only select repositories  ←  reels-maker"
echo "     • Permissions  ←  Actions: Read and write   (الصلاحية دي بس)"
echo "     • اختار أطول مدة صلاحية، واضغط Generate token وانسخ التوكن (بيبدأ بـ github_pat_)"
sleep 3
termux-open-url "https://github.com/settings/personal-access-tokens/new?name=reels-app&description=ReelsMaker+app&expires_in=365&actions=write" || true

TOKEN=""
for try in 1 2 3; do
  printf "الصق التوكن هنا (مش هيظهر وإنت بتلصق) ثم Enter: "
  read -rs TOKEN; echo
  code=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" \
    -H "Accept: application/vnd.github+json" "https://api.github.com/repos/$REPO/actions/workflows" || true)
  [ "$code" = "200" ] && break
  echo "❌ التوكن مش شغّال (كود $code). اتأكد من الصلاحيتين ومن اختيار reels-maker."
  TOKEN=""
done
[ -n "$TOKEN" ] || { echo "وقفنا. اعمل التوكن صح وشغّل الأمر تاني."; exit 1; }
gh secret set APP_TOKEN -R "$REPO" --body "$TOKEN" >/dev/null
unset TOKEN

echo
echo "🔨 بناء التطبيق على سيرفر GitHub (حوالي 4 دقايق)..."
gh workflow run apk.yml -R "$REPO"
sleep 10
RID=""
for _ in $(seq 1 12); do
  RID=$(gh run list -R "$REPO" --workflow=apk.yml --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || true)
  [ -n "$RID" ] && break; sleep 5
done
[ -n "$RID" ] || { echo "❌ مقدرتش ألاقي عملية البناء. افتح تبويب Actions في المستودع."; exit 1; }
gh run watch "$RID" -R "$REPO" --exit-status || { echo "❌ البناء فشل. شوف السبب: gh run view $RID -R $REPO --log-failed"; exit 1; }

gh release download apk-latest -R "$REPO" -p ReelsMaker.apk -D "$D" --clobber
echo
echo "✅ التطبيق جاهز في التنزيلات: ReelsMaker.apk"
echo "📲 ثبّته (لو طلب «السماح بالتثبيت من هذا المصدر» وافق). تقدر تبعته لأي حد ويشتغل عنده من غير أي إعداد."
termux-open "$D/ReelsMaker.apk" || true
