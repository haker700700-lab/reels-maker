#!/data/data/com.termux/files/usr/bin/bash
# فحص سريع: ليه التطبيق مش بيشتغل؟ (التوكن، الصلاحيات، المستودع، المفاتيح، آخر تشغيلات). مابيشغّلش أي ريلز.
LOGIN=$(gh api user --jq .login 2>/dev/null) || { echo "❌ مش مسجّل دخول GitHub. اكتب: gh auth login"; exit 1; }
REPO="${REELS_REPO:-$LOGIN/reels-maker}"
T=$(cat "$HOME/.reels-app-token" 2>/dev/null)
TMPF=$(mktemp 2>/dev/null || echo "$HOME/.w.json")
echo "المستودع: $REPO"
gh repo view "$REPO" --json visibility,defaultBranchRef --jq '"الظهور: \(.visibility) — الفرع: \(.defaultBranchRef.name)"' 2>/dev/null || echo "❌ المستودع مش موجود"
[ -n "$T" ] || { echo "❌ مفيش توكن للتطبيق (~/.reels-app-token). شغّل: RESET_TOKEN=1 bash ~/reels-maker-main/termux-update.sh"; exit 1; }
H=(-H "Authorization: Bearer $T" -H "Accept: application/vnd.github+json")
R=$(curl -s -o /dev/null -w '%{http_code}' "${H[@]}" "https://api.github.com/repos/$REPO/actions/workflows/reels.yml/runs?per_page=1")
W=$(curl -s -o "$TMPF" -w '%{http_code}' -X POST "${H[@]}" "https://api.github.com/repos/$REPO/actions/workflows/reels.yml/dispatches" -d '{"ref":"__probe__"}')
echo "قراءة Actions : $R  (المطلوب 200)"
echo "كتابة Actions : $W  (المطلوب 422 = سليم)"
case "$W" in
  422) echo "✅ التوكن سليم (يقرا ويكتب).";;
  403) echo "❌ التوكن ناقص «Actions: Read and write» — ده سبب «مفيش اتصال». اعمل توكن جديد: RESET_TOKEN=1 bash ~/reels-maker-main/termux-update.sh"; head -c 200 "$TMPF"; echo;;
  401) echo "❌ التوكن منتهي/اتلغى. RESET_TOKEN=1 bash ~/reels-maker-main/termux-update.sh";;
  404) echo "⚠️ 404: المستودع أو reels.yml مش ظاهرين للتوكن (اتأكد إنه مختار reels-maker).";;
  *)   echo "؟ رد غريب:"; head -c 200 "$TMPF"; echo;;
esac
echo "— مفاتيح المستودع —"
gh secret list -R "$REPO" 2>/dev/null | awk '{print "سر: "$1}'
gh variable list -R "$REPO" 2>/dev/null | awk '{print "متغير: "$1}'
echo "— آخر 3 تشغيلات —"
gh run list -R "$REPO" --limit 3 2>/dev/null || echo "(مفيش)"
