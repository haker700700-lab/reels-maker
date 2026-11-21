package com.reelsmaker.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * صانع الريلز (v6): بتلصق الرابط وبس. الشغل كله على سيرفرات GitHub المجانية،
 * ومتابعة العملية والتنزيل بتتم في خدمة خلفية (JobService) بتكمّل حتى لو قفلت الشاشة أو التطبيق أو النت فصل.
 * الصفحة الرئيسية ثابتة على شاشة واحدة (من غير تمرير). كل الخصائص (موسيقى • عكس • ريأكشن • اللقطات...) جوه «استوديو برو».
 */
public class MainActivity extends Activity {
    SharedPreferences sp;
    EditText link, count, minL, maxL, text;
    String resv = Feat.F_RES;
    Button qBtn, go, last, battery;
    Switch rights;
    TextView status, stSvc, stMode, stFeat, stPub, studioSub;
    TextView ttSt, fbSt, igSt, xSt;
    ProgressBar bar;
    final SharedPreferences.OnSharedPreferenceChangeListener listener = (p, k) -> runOnUiThread(this::render);

    // نفس الألوان القديمة بأسمائها (الشاشات التانية بتستخدمها) — بس بالألوان الهادية الجديدة
    static final int BG = Ui.BG, CARD = Ui.CARD, ACC = Ui.ACC, ACC2 = Ui.ACC2, TXT = Ui.TXT, DIM = Ui.DIM;

    int dp(int v) { return Ui.dp(this, v); }

    GradientDrawable shape(int color, int r) { return Ui.shape(this, color, r, 0, 0); }

    TextView cap(String s) {
        TextView t = Ui.text(this, s, 10.5f, DIM, false);
        t.setSingleLine(true);
        t.setPadding(dp(2), 0, dp(2), dp(2));
        return t;
    }

    EditText box(String hint, String val, int type) {
        EditText e = new EditText(this);
        e.setText(val);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextColor(TXT);
        e.setHintTextColor(DIM);
        e.setTextSize(14);
        e.setBackground(Ui.shape(this, Ui.CARD2, 12, 0, 0));
        e.setPadding(dp(12), 0, dp(12), 0);
        return e;
    }

    LinearLayout.LayoutParams lp(int w, int h, float wt, int m) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h, wt);
        l.setMargins(dp(m), 0, dp(m), 0);
        return l;
    }

    LinearLayout.LayoutParams rowLp(int h, int top) {
        LinearLayout.LayoutParams l = lp(-1, h, 0, 0);
        l.topMargin = dp(top);
        return l;
    }

    void setRes(String r) {
        resv = "720".equals(r) ? "720" : "1080";
        qBtn.setText("1080".equals(resv) ? "1080 · عالية" : "720 · أسرع");
    }

    void loadFromPrefs() {
        count.setText(Feat.str(sp, "n", Feat.F_N));
        minL.setText(Feat.str(sp, "min", Feat.F_MIN));
        maxL.setText(Feat.str(sp, "max", Feat.F_MAX));
        setRes(Feat.str(sp, "res", Feat.F_RES));
        text.setText(Feat.str(sp, "text", Feat.F_TEXT));
    }

    void saveCurrent() {
        sp.edit().putString("n", count.getText().toString().trim())
                .putString("min", minL.getText().toString().trim())
                .putString("max", maxL.getText().toString().trim())
                .putString("res", resv)
                .putString("text", text.getText().toString()).apply();
    }

    /** خانة حالة صغيرة فوق: قيمة + اسم. */
    TextView statBox(LinearLayout row, String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        TextView v = Ui.text(this, "—", 13, TXT, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setGravity(Gravity.CENTER);
        TextView l = Ui.text(this, label, 9.5f, DIM, false);
        l.setGravity(Gravity.CENTER);
        c.addView(v);
        c.addView(l);
        row.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
        return v;
    }

    /** مربع منصة نشر: أيقونة + اسم + حالة (شغّال/موقوف). */
    TextView platTile(LinearLayout row, String icon, String name, final Class<?> cls) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(2), dp(6), dp(2), dp(6));
        t.setBackground(Ui.shape(this, Ui.CARD, 14, Ui.STROKE, 1));
        TextView ic = Ui.text(this, icon, 19, TXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setIncludeFontPadding(false);
        TextView nm = Ui.text(this, name, 11.5f, TXT, true);
        nm.setGravity(Gravity.CENTER);
        nm.setSingleLine(true);
        TextView st = Ui.text(this, "موقوف", 9.5f, DIM, false);
        st.setGravity(Gravity.CENTER);
        t.addView(ic);
        t.addView(nm);
        t.addView(st);
        t.setOnClickListener(v -> startActivity(new Intent(this, cls)));
        row.addView(t, lp(0, -2, 1, 3));
        return st;
    }

    CharSequence credit() {
        SpannableStringBuilder b = new SpannableStringBuilder();
        b.append("صنع بكل حب  ·  ");
        int s0 = b.length();
        b.append("adel mohamed");
        b.setSpan(new ForegroundColorSpan(Ui.ACC2), s0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new StyleSpan(Typeface.BOLD), s0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.append("  ");
        int h0 = b.length();
        b.append("❤");
        b.setSpan(new ForegroundColorSpan(Ui.HEART), h0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        if (!sp.contains("v4")) {
            sp.edit().remove("mus").remove("d_mus").putBoolean("v4", true).apply();
        }
        Feat.ensureInit(sp);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));

        // ───── الهيدر: الشعار + الاسم + «صنع بكل حب» + مساعدة ─────
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.addView(new Ui.LogoView(this), new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(10), 0, dp(10), 0);
        tt.addView(Ui.text(this, "صانع الريلز", 20, TXT, true));
        TextView cr = Ui.text(this, "", 11.5f, DIM, false);
        cr.setText(credit());
        cr.setSingleLine(true);
        tt.addView(cr);
        hd.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView help = Ui.text(this, "؟", 17, ACC, true);
        help.setGravity(Gravity.CENTER);
        help.setBackground(Ui.shape(this, Ui.CARD, 18, Ui.STROKE, 1));
        help.setOnClickListener(v -> showHelp());
        hd.addView(help, new LinearLayout.LayoutParams(dp(36), dp(36)));
        root.addView(hd);

        // ───── شريط الحالة العلوي: بيوريك الدنيا كلها (اضغط عليه للتفاصيل) ─────
        LinearLayout strip = new LinearLayout(this);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(6), dp(6), dp(6), dp(6));
        strip.setBackground(Ui.shape(this, Ui.CARD, 14, Ui.STROKE, 1));
        stSvc = statBox(strip, "الخدمة");
        stMode = statBox(strip, "الوضع");
        stFeat = statBox(strip, "الخصائص");
        stPub = statBox(strip, "النشر");
        strip.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("📊 حالة كل حاجة").setMessage(Feat.summary(sp)).setPositiveButton("تمام", null).show());
        root.addView(strip, rowLp(-2, 8));

        // ───── كارت الرابط ─────
        LinearLayout lc = Ui.card(this);
        lc.addView(cap("🔗 رابط الفيديو — يوتيوب • تيك توك • فيسبوك • رابط مباشر"));
        LinearLayout lr = new LinearLayout(this);
        link = box("الصق الرابط هنا — https://...", "", InputType.TYPE_TEXT_VARIATION_URI);
        link.setTextSize(13);
        Button paste = Ui.pill(this, "📋 لصق", Ui.ACC);
        paste.setTextColor(Color.WHITE);
        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (t != null) link.setText(t.toString().trim());
            }
        });
        lr.addView(link, lp(0, dp(42), 1, 2));
        lr.addView(paste, lp(dp(74), dp(42), 0, 2));
        lc.addView(lr);
        root.addView(lc, rowLp(-2, 8));

        // ───── كارت الإعدادات الأساسية ─────
        LinearLayout sc = Ui.card(this);
        LinearLayout nr = new LinearLayout(this);
        String[] caps = {"عدد الريلز", "أقل مدة (ث)", "أقصى مدة (ث)", "الجودة"};
        EditText[] es = new EditText[3];
        for (int i = 0; i < 4; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(cap(caps[i]));
            if (i < 3) {
                es[i] = box("", "", InputType.TYPE_CLASS_NUMBER);
                es[i].setGravity(Gravity.CENTER);
                es[i].setTextSize(14);
                col.addView(es[i], new LinearLayout.LayoutParams(-1, dp(40)));
            } else {
                qBtn = Ui.pill(this, "1080 · عالية", Ui.ACC);
                qBtn.setTextColor(Color.WHITE);
                qBtn.setTextSize(12);
                qBtn.setSingleLine(true);
                qBtn.setOnClickListener(v -> setRes("1080".equals(resv) ? "720" : "1080"));
                col.addView(qBtn, new LinearLayout.LayoutParams(-1, dp(40)));
            }
            nr.addView(col, lp(0, -2, i == 3 ? 1.35f : 1f, 3));
        }
        count = es[0];
        minL = es[1];
        maxL = es[2];
        sc.addView(nr);
        TextView tc = cap("📝 نص عربي على الفيديو (اختياري) — شكله ومكانه من «استوديو برو»");
        tc.setPadding(dp(2), dp(6), dp(2), dp(2));
        sc.addView(tc);
        text = box("اكتب سطر واحد يظهر على كل ريل", "", InputType.TYPE_CLASS_TEXT);
        text.setTextSize(13);
        sc.addView(text, new LinearLayout.LayoutParams(-1, dp(40)));
        root.addView(sc, rowLp(-2, 8));

        // ───── الحقوق ─────
        LinearLayout rr = Ui.toggleRow(this, "📜", "أؤكد أني أملك حقوق الفيديو أو مصرّح لي",
                "لازم تعلّمها قبل ما تبدأ", false, (v, on) -> { });
        rights = (Switch) rr.getTag();
        rr.setPadding(dp(4), dp(2), dp(4), dp(2));
        root.addView(rr, rowLp(-2, 4));

        // ───── استوديو برو + منصات النشر ─────
        LinearLayout studio = new LinearLayout(this);
        studio.setGravity(Gravity.CENTER_VERTICAL);
        studio.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{0xFF1E5E63, 0xFF27406B});
        sg.setCornerRadius(dp(16));
        studio.setBackground(sg);
        studio.addView(Ui.iconChip(this, "🎛️", 38, Color.WHITE));
        LinearLayout sCol = new LinearLayout(this);
        sCol.setOrientation(LinearLayout.VERTICAL);
        sCol.setPadding(dp(10), 0, dp(6), 0);
        sCol.addView(Ui.text(this, "استوديو برو", 15, Color.WHITE, true));
        studioSub = Ui.text(this, "", 10.5f, 0xFFC5D6DC, false);
        studioSub.setSingleLine(true);
        studioSub.setEllipsize(TextUtils.TruncateAt.END);
        sCol.addView(studioSub);
        studio.addView(sCol, new LinearLayout.LayoutParams(0, -2, 1f));
        studio.addView(Ui.text(this, "‹", 24, Color.WHITE, true));
        studio.setOnClickListener(v -> startActivity(new Intent(this, StudioActivity.class)));
        root.addView(studio, rowLp(-2, 8));

        LinearLayout pubRow = new LinearLayout(this);
        ttSt = platTile(pubRow, "🎵", "تيك توك", TikTokActivity.class);
        fbSt = platTile(pubRow, "📘", "فيسبوك", FbActivity.class);
        igSt = platTile(pubRow, "📷", "إنستا", IgActivity.class);
        xSt = platTile(pubRow, "𝕏", "إكس", XActivity.class);
        root.addView(pubRow, rowLp(-2, 6));

        // ───── زر البدء + التقدّم + الحالة ─────
        go = new Button(this);
        go.setText("ابدأ الآن ☁️");
        go.setAllCaps(false);
        go.setTextSize(17);
        go.setTextColor(Color.WHITE);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setMinHeight(0);
        go.setMinimumHeight(0);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{0xFF2E9C93, 0xFF5B8DD9});
        gd.setCornerRadius(dp(16));
        go.setBackground(gd);
        go.setStateListAnimator(null);
        root.addView(go, rowLp(dp(50), 8));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACC));
        bar.setVisibility(View.INVISIBLE);
        root.addView(bar, rowLp(dp(4), 4));

        status = new TextView(this);
        status.setTextColor(TXT);
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(5);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setPadding(0, dp(4), 0, dp(4));
        status.setText("جاهز ✨ الصق الرابط واضغط «ابدأ». الشغل كله على السيرفر ⏱ من 3 لـ 8 دقايق.");
        status.setOnClickListener(v -> {
            CharSequence t = status.getText();
            if (t == null || t.length() == 0) return;
            new android.app.AlertDialog.Builder(this).setMessage(t).setPositiveButton("تمام", null).show();
        });
        root.addView(status, lp(-1, 0, 1, 0));

        LinearLayout br = new LinearLayout(this);
        last = Ui.pill(this, "⬇️ نزّل آخر ملف", Ui.CARD2);
        last.setTextSize(12);
        battery = Ui.pill(this, "🔋 اسمح بالخلفية", Ui.CARD2);
        battery.setTextSize(12);
        br.addView(last, lp(0, dp(38), 1, 3));
        br.addView(battery, lp(0, dp(38), 1, 3));
        root.addView(br);
        battery.setOnClickListener(v -> askBattery());

        go.setOnClickListener(v -> start());
        last.setOnClickListener(v -> downloadLast());

        // الصفحة بتفضل ثابتة على شاشة واحدة؛ التمرير ده بس احتياطي لو الموبايل صغير جدًا (من غير أي أثر ارتداد)
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        sv.setBackgroundColor(BG);
        sv.addView(root);
        setContentView(sv);

        loadFromPrefs();

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        } else if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
        }
    }

    void showHelp() {
        new android.app.AlertDialog.Builder(this).setTitle("❓ إزاي أستخدم التطبيق؟")
                .setMessage("1) الصق رابط الفيديو.\n"
                        + "2) حدّد عدد الريلز وأقل/أقصى مدة للريل والجودة.\n"
                        + "3) علّم على «أؤكد أني أملك الحقوق».\n"
                        + "4) اضغط «ابدأ الآن» — السيرفر بيشتغل ومتابعتك بتكمّل حتى لو قفلت التطبيق.\n\n"
                        + "🎛️ «استوديو برو»: كل الخصائص هناك — اللقطات الذكية (أكشن/كوميدي/حب ودراما)، الموسيقى والأغاني، عكس الفيديو، الريأكشن، الفلاتر، اللوجو، النص، الصوت. كل خاصية جنبها مفتاح تشغيل/إيقاف.\n\n"
                        + "📤 المنصات (تيك توك/فيسبوك/إنستا/إكس): افتح المنصة وفعّل النشر التلقائي وضيف حساباتك.\n\n"
                        + "📊 الشريط اللي فوق بيوريك حالة كل حاجة — اضغط عليه للتفاصيل.")
                .setPositiveButton("تمام", null).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFromPrefs();
        sp.registerOnSharedPreferenceChangeListener(listener);
        if (sp.getBoolean("run_demo", false)) {    // الاستوديو طلب فيديو تجريبي
            sp.edit().putBoolean("run_demo", false).apply();
            start(true);
            return;
        }
        // لو في عملية شغّالة والخدمة اتقتلت (مثلًا من النظام)، نشغّلها تاني
        if (isRunning() && !JobService.alive) startJobService();
        render();
    }

    @Override
    protected void onPause() {
        sp.unregisterOnSharedPreferenceChangeListener(listener);
        saveCurrent();
        super.onPause();
    }

    // ───────── العرض ─────────
    boolean isRunning() {
        String st = sp.getString("stage", "");
        return "NEW".equals(st) || "RUNNING".equals(st);
    }

    void plat(TextView t, boolean on) {
        t.setText(on ? "● شغّال" : "موقوف");
        t.setTextColor(on ? Ui.OK : DIM);
    }

    void render() {
        boolean run = isRunning();
        go.setEnabled(!run);
        go.setAlpha(run ? 0.55f : 1f);
        last.setEnabled(!run);
        bar.setVisibility(run ? View.VISIBLE : View.INVISIBLE);
        String m = sp.getString("msg", "");
        if (!m.isEmpty()) status.setText(m);

        boolean svc = !(Gh.TOK.isEmpty() || Gh.REPO.isEmpty());
        stSvc.setText(svc ? (run ? "⏳ شغّال" : "✅ جاهز") : "⚠️ ناقص");
        stSvc.setTextColor(svc ? Ui.OK : Ui.WARM);
        stMode.setText(Feat.modeName(sp));
        stFeat.setText(String.valueOf(Feat.enabledCount(sp)));
        boolean tt = sp.getBoolean("tt_server", false), fb = sp.getBoolean("fb_server", false),
                ig = sp.getBoolean("ig_server", false), x = sp.getBoolean("x_server", false);
        int pubN = (tt ? 1 : 0) + (fb ? 1 : 0) + (ig ? 1 : 0) + (x ? 1 : 0);
        stPub.setText(pubN == 0 ? "مقفول" : pubN + " منصات");
        plat(ttSt, tt);
        plat(fbSt, fb);
        plat(igSt, ig);
        plat(xSt, x);
        String w = Feat.warnings(sp);
        studioSub.setText("الوضع: " + Feat.modeName(sp) + (w.isEmpty() ? " • كل الخصائص هنا" : " • فيه تنبيه ⚠️"));

        boolean ignoring = true;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            ignoring = pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        battery.setVisibility(ignoring ? View.GONE : View.VISIBLE);
    }

    void askBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable ignored) { }
        }
    }

    boolean noService() {
        if (Gh.TOK.isEmpty() || Gh.REPO.isEmpty()) {
            status.setText("التطبيق اتبنى من غير بيانات الخدمة. اعمل بناء جديد من صاحب التطبيق.");
            return true;
        }
        return false;
    }

    int num(EditText e, int d, int lo, int hi) {
        try {
            int v = Integer.parseInt(e.getText().toString().trim());
            return Math.max(lo, Math.min(hi, v));
        } catch (Exception x) {
            return d;
        }
    }

    void startJobService() {
        try {
            Intent i = new Intent(this, JobService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Throwable e) {
            status.setText("❌ مقدرتش أشغّل الخدمة الخلفية: " + e.getClass().getSimpleName());
        }
    }

    // ───────── التشغيل ─────────
    void start() { start(false); }

    /** demo = فيديو تجريبي ١٠ ثواني بيتولّد على السيرفر (من غير رابط ولا إقرار حقوق، ومن غير نشر على أي منصة). */
    void start(boolean demo) {
        if (isRunning() || noService()) return;
        final String url = demo ? "demo" : link.getText().toString().trim();
        if (url.isEmpty()) { status.setText("الصق رابط الفيديو الأول."); return; }
        if (!demo && !rights.isChecked()) { status.setText("علّم على «أؤكد أني أملك حقوق الفيديو»."); return; }
        int n = num(count, 5, 1, 30), mn = num(minL, 15, 3, 600), mx = num(maxL, 25, 3, 600);
        if (mx < mn) { int t = mn; mn = mx; mx = t; }
        count.setText(String.valueOf(n));
        minL.setText(String.valueOf(mn));
        maxL.setText(String.valueOf(mx));
        saveCurrent();
        String fixed = Feat.fix(sp);   // تعارضات واضحة بتتصلّح لوحدها (مثلًا الوجوه محتاجة القص بالطول)
        final String job = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        final String body;
        try {
            JSONObject in = new JSONObject();
            in.put("job", job);
            in.put("url", url);
            in.put("rights", "true");
            in.put("n", String.valueOf(n));
            in.put("min_l", String.valueOf(mn));
            in.put("max_l", String.valueOf(mx));
            in.put("res", resv);
            in.put("reverse", String.valueOf(Feat.bool(sp, "rev", Feat.F_REV)));
            in.put("text", text.getText().toString());
            in.put("music", String.valueOf(Feat.bool(sp, "mus", Feat.F_MUS)));
            in.put("mvol", Feat.str(sp, "mvol", Feat.F_MVOL));
            in.put("music_url", Feat.str(sp, "murl", Feat.F_MURL).trim());
            in.put("rsong", String.valueOf(Feat.bool(sp, "rsong", Feat.F_RSONG)));
            in.put("react", String.valueOf(Feat.bool(sp, "react", Feat.F_REACT)));
            in.put("feat", Feat.string(sp));
            boolean ttOn = !demo && sp.getBoolean("tt_server", false);
            if (ttOn && sp.getString("tt_accs", "[]").length() < 5) {
                status.setText("فعّلت النشر على التك توك بس مفيش حسابات. أضفها من «🎵 تيك توك».");
                return;
            }
            in.put("tiktok", String.valueOf(ttOn));
            in.put("accs", ttOn ? sp.getString("tt_accs", "[]") : "[]");
            in.put("caption", sp.getString("tt_caption", ""));
            boolean fbOn = !demo && sp.getBoolean("fb_server", false);
            if (fbOn && sp.getString("fb_accs", "[]").length() < 5) {
                status.setText("فعّلت النشر على فيسبوك بس مفيش صفحات. أضفها من «📘 فيسبوك».");
                return;
            }
            in.put("fb", String.valueOf(fbOn));
            in.put("fb_accs", fbOn ? sp.getString("fb_accs", "[]") : "[]");
            in.put("fb_caption", sp.getString("fb_caption", ""));
            boolean igOn = !demo && sp.getBoolean("ig_server", false);
            if (igOn && sp.getString("ig_accs", "[]").length() < 5) {
                status.setText("فعّلت النشر على إنستجرام بس مفيش حسابات. أضفها من «📷 إنستا».");
                return;
            }
            in.put("ig", String.valueOf(igOn));
            in.put("ig_accs", igOn ? sp.getString("ig_accs", "[]") : "[]");
            in.put("ig_caption", sp.getString("ig_caption", ""));
            boolean xOn = !demo && sp.getBoolean("x_server", false);
            if (xOn && sp.getString("x_accs", "[]").length() < 5) {
                status.setText("فعّلت النشر على X بس مفيش حسابات. أضفها من «𝕏 إكس».");
                return;
            }
            in.put("adv", Feat.adv(sp, demo, xOn));
            body = new JSONObject().put("ref", "main").put("inputs", in).toString();
        } catch (Exception e) { status.setText("خطأ في الإعدادات."); return; }

        // كل حالة العملية بتتحفظ، فالخدمة تكمّل لو اتقتلت أو الجهاز اتعاد تشغيله
        final boolean ttS = !demo && sp.getBoolean("tt_server", false), fbS = !demo && sp.getBoolean("fb_server", false), igS = !demo && sp.getBoolean("ig_server", false), xS = !demo && sp.getBoolean("x_server", false);
        sp.edit().putString("job", job).putString("body", body).putString("stage", "NEW")
                .putBoolean("job_nodl", (ttS || fbS || igS || xS) && (!ttS || sp.getBoolean("tt_nodl", false)) && (!fbS || sp.getBoolean("fb_nodl", false)) && (!igS || sp.getBoolean("ig_nodl", false)) && (!xS || sp.getBoolean("x_nodl", false)))
                .putBoolean("job_tt", ttS).putBoolean("job_fb", fbS).putBoolean("job_ig", igS).putBoolean("job_x", xS)
                .putBoolean("posted", false).putLong("t0", System.currentTimeMillis())
                .putString("msg", fixed.isEmpty() ? "ببعت الطلب للسيرفر..." : "ببعت الطلب للسيرفر...\n" + fixed).putInt("pct", 0).apply();
        if (!demo) link.setText("");   // الرابط الجاي جديد؛ باقي الإعدادات زي ما هي
        startJobService();
        render();
    }

    void toast(String t) {
        android.widget.Toast.makeText(this, t, android.widget.Toast.LENGTH_SHORT).show();
    }

    void downloadLast() {
        if (isRunning() || noService()) return;
        if (sp.getString("job", "").isEmpty()) { status.setText("لسه معملتش أي عملية."); return; }
        sp.edit().putString("stage", "RUNNING").putString("msg", "بتأكد من آخر عملية...").putInt("pct", 0)
                .putLong("t0", System.currentTimeMillis()).apply();
        startJobService();
        render();
    }
}
