package com.reelsmaker.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * صانع الريلز: بتلصق الرابط وبس. الشغل كله على سيرفرات GitHub المجانية،
 * ومتابعة العملية والتنزيل بتتم في خدمة خلفية (JobService) بتكمّل حتى لو قفلت الشاشة أو التطبيق أو النت فصل.
 * الصفحة الرئيسية ثابتة على شاشة واحدة (من غير أي تمرير طلوع أو نزول). الخصائص في شاشة مستقلة (FeaturesActivity).
 */
public class MainActivity extends Activity {
    SharedPreferences sp;
    EditText link, count, minL, maxL, text;
    String resv = Feat.F_RES;
    Button q720, q1080, go, last, battery, feats;
    CheckBox reverse, music, rights;
    TextView status;
    ProgressBar bar;
    final SharedPreferences.OnSharedPreferenceChangeListener listener = (p, k) -> runOnUiThread(this::render);

    static final int BG = 0xFF0E1224, CARD = 0xFF1A2040, ACC = 0xFF7C5CFF, ACC2 = 0xFF2DD4BF, TXT = 0xFFF1F3FF, DIM = 0xFF8E95B8;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable shape(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    TextView label(LinearLayout p, String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(11);
        t.setTextColor(DIM);
        t.setSingleLine(true);
        t.setPadding(dp(4), dp(4), dp(4), dp(2));
        p.addView(t);
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
        e.setBackground(shape(CARD, 12));
        e.setPadding(dp(12), 0, dp(12), 0);
        return e;
    }

    LinearLayout.LayoutParams lp(int w, int h, float wt, int m) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h, wt);
        l.setMargins(dp(m), 0, dp(m), 0);
        return l;
    }

    EditText field(LinearLayout p, String lbl, String val, int type) {
        label(p, lbl);
        EditText e = box("", val, type);
        p.addView(e, lp(-1, dp(40), 0, 0));
        return e;
    }

    CheckBox check(LinearLayout p, String s, boolean v) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setChecked(v);
        c.setTextColor(TXT);
        c.setTextSize(13);
        c.setButtonTintList(android.content.res.ColorStateList.valueOf(ACC2));
        // جوه صف أفقي: بياخد نصيبه بالتساوي. جوه عمود رأسي: مفيش وزن (عشان ما ياخدش من مساحة الحالة)
        p.addView(c, lp(-2, dp(36), p.getOrientation() == LinearLayout.HORIZONTAL ? 1 : 0, 0));
        return c;
    }

    Button pill(String s, boolean on) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TXT);
        b.setTextSize(13);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(shape(on ? ACC : CARD, 12));
        b.setStateListAnimator(null);
        return b;
    }

    void setRes(String r) {
        resv = "720".equals(r) ? "720" : "1080";
        q720.setBackground(shape("720".equals(resv) ? ACC : CARD, 12));
        q1080.setBackground(shape("1080".equals(resv) ? ACC : CARD, 12));
    }

    /** بيحمّل القيم (الحالية، وإلا الافتراضي المحفوظ، وإلا الأصلي) في الخانات. */
    void loadFromPrefs() {
        count.setText(Feat.str(sp, "n", Feat.F_N));
        minL.setText(Feat.str(sp, "min", Feat.F_MIN));
        maxL.setText(Feat.str(sp, "max", Feat.F_MAX));
        setRes(Feat.str(sp, "res", Feat.F_RES));
        text.setText(Feat.str(sp, "text", Feat.F_TEXT));
        reverse.setChecked(Feat.bool(sp, "rev", Feat.F_REV));
        music.setChecked(Feat.bool(sp, "mus", Feat.F_MUS));
    }

    /** بيحفظ اللي على الشاشة كقيم حالية (عشان شاشة الخصائص تقدر تحفظها كافتراضي). */
    void saveCurrent() {
        sp.edit().putString("n", count.getText().toString().trim())
                .putString("min", minL.getText().toString().trim())
                .putString("max", maxL.getText().toString().trim())
                .putString("res", resv)
                .putString("text", text.getText().toString())
                .putBoolean("rev", reverse.isChecked())
                .putBoolean("mus", music.isChecked()).apply();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        if (!sp.contains("v4")) {
            // مرة واحدة بس: الموسيقى بقت اختيارية ومقفولة من الأصل
            sp.edit().remove("mus").remove("d_mus").putBoolean("v4", true).apply();
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(16), dp(12), dp(16), dp(10));

        TextView title = new TextView(this);
        title.setText("🎬 صانع الريلز");
        title.setTextSize(22);
        title.setTextColor(TXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("الصق الرابط — والشغل كله على السيرفر");
        sub.setTextSize(11);
        sub.setTextColor(DIM);
        sub.setGravity(Gravity.CENTER);
        sub.setSingleLine(true);
        root.addView(sub);

        label(root, "🔗 رابط الفيديو");
        LinearLayout lr = new LinearLayout(this);
        link = box("https://...", "", InputType.TYPE_TEXT_VARIATION_URI);
        Button paste = pill("لصق", true);
        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (t != null) link.setText(t.toString().trim());
            }
        });
        lr.addView(link, lp(0, dp(44), 1, 0));
        lr.addView(paste, lp(dp(64), dp(44), 0, 0));
        root.addView(lr);

        LinearLayout nr = new LinearLayout(this);
        String[] lbls = {"عدد الريلز", "أقل مدة (ث)", "أقصى مدة (ث)"};
        EditText[] es = new EditText[3];
        for (int i = 0; i < 3; i++) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            es[i] = field(c, lbls[i], "", InputType.TYPE_CLASS_NUMBER);
            es[i].setGravity(Gravity.CENTER);
            nr.addView(c, lp(0, -2, 1, 3));
        }
        count = es[0];
        minL = es[1];
        maxL = es[2];
        root.addView(nr);

        LinearLayout qr = new LinearLayout(this);
        qr.setGravity(Gravity.CENTER_VERTICAL);
        TextView ql = new TextView(this);
        ql.setText("الجودة");
        ql.setTextSize(12);
        ql.setTextColor(DIM);
        ql.setPadding(dp(4), 0, dp(8), 0);
        qr.addView(ql);
        q1080 = pill("1080 · عالية", true);
        q720 = pill("720 · أسرع", false);
        q1080.setOnClickListener(v -> setRes("1080"));
        q720.setOnClickListener(v -> setRes("720"));
        qr.addView(q1080, lp(0, dp(38), 1, 3));
        qr.addView(q720, lp(0, dp(38), 1, 3));
        LinearLayout.LayoutParams qlp = lp(-1, -2, 0, 0);
        qlp.topMargin = dp(6);
        root.addView(qr, qlp);

        text = field(root, "النص العربي على الفيديو (سطر واحد)", "", InputType.TYPE_CLASS_TEXT);

        LinearLayout cr = new LinearLayout(this);
        reverse = check(cr, "عكس الفيديو", false);
        music = check(cr, "موسيقى خلفية", false);
        root.addView(cr);

        rights = check(root, "أؤكد أني أملك حقوق الفيديو أو مصرّح لي", false);

        feats = pill("✨ الخصائص والإعدادات", false);
        feats.setOnClickListener(v -> startActivity(new Intent(this, FeaturesActivity.class)));
        LinearLayout.LayoutParams fl = lp(-1, dp(42), 0, 0);
        fl.topMargin = dp(4);
        root.addView(feats, fl);

        LinearLayout pubRow = new LinearLayout(this);
        Button tt = pill("🎵 تيك توك", false);
        tt.setOnClickListener(v -> startActivity(new Intent(this, TikTokActivity.class)));
        Button fb = pill("📘 فيسبوك", false);
        fb.setOnClickListener(v -> startActivity(new Intent(this, FbActivity.class)));
        pubRow.addView(tt, lp(0, dp(42), 1, 2));
        pubRow.addView(fb, lp(0, dp(42), 1, 2));
        LinearLayout.LayoutParams tl = lp(-1, dp(42), 0, 0);
        tl.topMargin = dp(4);
        root.addView(pubRow, tl);

        go = new Button(this);
        go.setText("ابدأ الآن ☁️");
        go.setAllCaps(false);
        go.setTextSize(17);
        go.setTextColor(Color.WHITE);
        go.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        go.setMinHeight(0);
        go.setMinimumHeight(0);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{ACC, ACC2});
        gd.setCornerRadius(dp(14));
        go.setBackground(gd);
        go.setStateListAnimator(null);
        LinearLayout.LayoutParams gl = lp(-1, dp(50), 0, 0);
        gl.topMargin = dp(8);
        root.addView(go, gl);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACC2));
        bar.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams bp = lp(-1, dp(4), 0, 0);
        bp.topMargin = dp(4);
        root.addView(bar, bp);

        // الحالة: بتاخد المساحة الباقية (مفيش تمرير). اضغط عليها تشوف الرسالة كاملة.
        status = new TextView(this);
        status.setTextColor(TXT);
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(5);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setPadding(0, dp(4), 0, dp(4));
        status.setText("جاهز. ⏱ الوقت المتوقع من 3 لـ 8 دقايق.");
        status.setOnClickListener(v -> {
            CharSequence t = status.getText();
            if (t == null || t.length() == 0) return;
            new android.app.AlertDialog.Builder(this).setMessage(t).setPositiveButton("تمام", null).show();
        });
        LinearLayout.LayoutParams stp = lp(-1, 0, 1, 0);
        root.addView(status, stp);

        LinearLayout br = new LinearLayout(this);
        last = pill("⬇️ نزّل آخر ملف جاهز", false);
        battery = pill("🔋 اسمح بالخلفية", false);
        br.addView(last, lp(0, dp(40), 1, 2));
        br.addView(battery, lp(0, dp(40), 1, 2));
        root.addView(br);
        battery.setOnClickListener(v -> askBattery());

        go.setOnClickListener(v -> start());
        last.setOnClickListener(v -> downloadLast());
        setContentView(root);

        loadFromPrefs();

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        } else if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFromPrefs();   // لو رجعت من شاشة الخصائص (حفظ/رجوع للافتراضي/الأصلية)
        sp.registerOnSharedPreferenceChangeListener(listener);
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

    void render() {
        boolean run = isRunning();
        go.setEnabled(!run);
        last.setEnabled(!run);
        bar.setVisibility(run ? View.VISIBLE : View.INVISIBLE);
        String m = sp.getString("msg", "");
        if (!m.isEmpty()) status.setText(m);
        feats.setText("✨ الخصائص والإعدادات (" + Feat.enabledCount(sp) + " مفعّلة)");
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
    void start() {
        if (isRunning() || noService()) return;
        final String url = link.getText().toString().trim();
        if (url.isEmpty()) { status.setText("الصق رابط الفيديو الأول."); return; }
        if (!rights.isChecked()) { status.setText("علّم على «أؤكد أني أملك حقوق الفيديو»."); return; }
        int n = num(count, 5, 1, 30), mn = num(minL, 15, 3, 600), mx = num(maxL, 25, 3, 600);
        if (mx < mn) { int t = mn; mn = mx; mx = t; }
        count.setText(String.valueOf(n));
        minL.setText(String.valueOf(mn));
        maxL.setText(String.valueOf(mx));
        saveCurrent();
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
            in.put("reverse", String.valueOf(reverse.isChecked()));
            in.put("text", text.getText().toString());
            in.put("music", String.valueOf(music.isChecked()));
            in.put("feat", Feat.string(sp));
            boolean ttOn = sp.getBoolean("tt_server", false);
            if (ttOn && sp.getString("tt_accs", "[]").length() < 5) {
                status.setText("فعّلت النشر على التك توك بس مفيش حسابات. أضفها من «🎵 النشر على التك توك».");
                return;
            }
            in.put("tiktok", String.valueOf(ttOn));
            in.put("accs", ttOn ? sp.getString("tt_accs", "[]") : "[]");
            in.put("caption", sp.getString("tt_caption", ""));
            boolean fbOn = sp.getBoolean("fb_server", false);
            if (fbOn && sp.getString("fb_accs", "[]").length() < 5) {
                status.setText("فعّلت النشر على فيسبوك بس مفيش صفحات. أضفها من «📘 فيسبوك».");
                return;
            }
            in.put("fb", String.valueOf(fbOn));
            in.put("fb_accs", fbOn ? sp.getString("fb_accs", "[]") : "[]");
            in.put("fb_caption", sp.getString("fb_caption", ""));
            body = new JSONObject().put("ref", "main").put("inputs", in).toString();
        } catch (Exception e) { status.setText("خطأ في الإعدادات."); return; }

        // كل حالة العملية بتتحفظ، فالخدمة تكمّل لو اتقتلت أو الجهاز اتعاد تشغيله
        final boolean ttS = sp.getBoolean("tt_server", false), fbS = sp.getBoolean("fb_server", false);
        sp.edit().putString("job", job).putString("body", body).putString("stage", "NEW")
                .putBoolean("job_nodl", (ttS || fbS) && (!ttS || sp.getBoolean("tt_nodl", false)) && (!fbS || sp.getBoolean("fb_nodl", false)))
                .putBoolean("job_tt", ttS).putBoolean("job_fb", fbS)
                .putBoolean("posted", false).putLong("t0", System.currentTimeMillis())
                .putString("msg", "ببعت الطلب للسيرفر...").putInt("pct", 0).apply();
        link.setText("");   // الرابط الجاي جديد؛ باقي الإعدادات زي ما هي
        startJobService();
        render();
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
