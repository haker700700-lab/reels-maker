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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * صانع الريلز: بتلصق الرابط وبس. الشغل كله على سيرفرات GitHub المجانية،
 * ومتابعة العملية والتنزيل بتتم في خدمة خلفية (JobService) بتكمّل حتى لو قفلت الشاشة أو التطبيق أو النت فصل.
 */
public class MainActivity extends Activity {
    // الإعدادات الأصلية (المصنع)
    static final String F_N = "5", F_MIN = "15", F_MAX = "25", F_RES = "1080";
    static final String F_TEXT = "لمشاهدة وتحميل كاملا الرابط في البايو الملف الشخصي";
    static final boolean F_REV = false, F_MUS = true;

    SharedPreferences sp;
    EditText link, count, minL, maxL, text;
    String resv = F_RES;
    Button q720, q1080, go, last, battery;
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
        t.setTextSize(12);
        t.setTextColor(DIM);
        t.setPadding(dp(4), dp(8), dp(4), dp(3));
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
        e.setTextSize(15);
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
        p.addView(e, lp(-1, dp(46), 0, 0));
        return e;
    }

    CheckBox check(LinearLayout p, String s, boolean v) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setChecked(v);
        c.setTextColor(TXT);
        c.setTextSize(13);
        c.setButtonTintList(android.content.res.ColorStateList.valueOf(ACC2));
        p.addView(c, lp(-2, -2, 1, 0));
        return c;
    }

    Button pill(String s, boolean on) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TXT);
        b.setTextSize(13);
        b.setBackground(shape(on ? ACC : CARD, 12));
        b.setStateListAnimator(null);
        return b;
    }

    void setRes(String r) {
        resv = r;
        q720.setBackground(shape("720".equals(r) ? ACC : CARD, 12));
        q1080.setBackground(shape("1080".equals(r) ? ACC : CARD, 12));
    }

    /** القيمة الافتراضية المحفوظة (أو الأصلية لو ما حفظتش). */
    String dflt(String k, String factory) { return sp.getString("d_" + k, factory); }

    void applyValues(String n, String mn, String mx, String res, String txt, boolean rev, boolean mus) {
        count.setText(n);
        minL.setText(mn);
        maxL.setText(mx);
        setRes(res);
        text.setText(txt);
        reverse.setChecked(rev);
        music.setChecked(mus);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(18), dp(22), dp(18), dp(14));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("🎬 صانع الريلز");
        title.setTextSize(26);
        title.setTextColor(TXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("الصق أي رابط فيه فيديو — والشغل كله على السيرفر وبيكمّل حتى لو قفلت التطبيق");
        sub.setTextSize(12);
        sub.setTextColor(DIM);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        label(root, "🔗 رابط الفيديو (مباشر • تحميل • توكن • صفحة)");
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
        lr.addView(link, lp(0, dp(46), 1, 0));
        lr.addView(paste, lp(dp(64), dp(46), 0, 0));
        root.addView(lr);

        LinearLayout nr = new LinearLayout(this);
        String[] lbls = {"عدد الريلز", "أقل مدة (ث)", "أقصى مدة (ث)"};
        EditText[] es = new EditText[3];
        String[] vals = {sp.getString("n", dflt("n", F_N)), sp.getString("min", dflt("min", F_MIN)), sp.getString("max", dflt("max", F_MAX))};
        for (int i = 0; i < 3; i++) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            es[i] = field(c, lbls[i], vals[i], InputType.TYPE_CLASS_NUMBER);
            es[i].setGravity(Gravity.CENTER);
            nr.addView(c, lp(0, -2, 1, 3));
        }
        count = es[0];
        minL = es[1];
        maxL = es[2];
        root.addView(nr);

        label(root, "الجودة");
        LinearLayout qr = new LinearLayout(this);
        q1080 = pill("1080 · عالية", true);
        q720 = pill("720 · أسرع", false);
        q1080.setOnClickListener(v -> setRes("1080"));
        q720.setOnClickListener(v -> setRes("720"));
        qr.addView(q1080, lp(0, dp(42), 1, 3));
        qr.addView(q720, lp(0, dp(42), 1, 3));
        root.addView(qr);
        setRes(sp.getString("res", dflt("res", F_RES)));

        text = field(root, "النص العربي على الفيديو (سطر واحد)", sp.getString("text", dflt("text", F_TEXT)), InputType.TYPE_CLASS_TEXT);

        LinearLayout cr = new LinearLayout(this);
        reverse = check(cr, "عكس الفيديو", sp.getBoolean("rev", sp.getBoolean("d_rev", F_REV)));
        music = check(cr, "موسيقى هادئة", sp.getBoolean("mus", sp.getBoolean("d_mus", F_MUS)));
        root.addView(cr);

        // الإعدادات الافتراضية: بتغيّر الرابط بس كل مرة
        label(root, "⚙️ الإعدادات الافتراضية");
        LinearLayout dr = new LinearLayout(this);
        Button saveDef = pill("💾 حفظ كافتراضي", false);
        Button resetDef = pill("↩️ رجوع للافتراضي", false);
        Button factory = pill("🏭 الأصلية", false);
        dr.addView(saveDef, lp(0, dp(42), 1, 2));
        dr.addView(resetDef, lp(0, dp(42), 1, 2));
        dr.addView(factory, lp(0, dp(42), 1, 2));
        root.addView(dr);
        saveDef.setOnClickListener(v -> {
            sp.edit().putString("d_n", count.getText().toString().trim())
                    .putString("d_min", minL.getText().toString().trim())
                    .putString("d_max", maxL.getText().toString().trim())
                    .putString("d_res", resv)
                    .putString("d_text", text.getText().toString())
                    .putBoolean("d_rev", reverse.isChecked())
                    .putBoolean("d_mus", music.isChecked()).apply();
            status.setText("💾 اتحفظت كإعدادات افتراضية. من دلوقتي كل اللي تغيّره هو الرابط بس.");
        });
        resetDef.setOnClickListener(v -> {
            applyValues(dflt("n", F_N), dflt("min", F_MIN), dflt("max", F_MAX), dflt("res", F_RES), dflt("text", F_TEXT),
                    sp.getBoolean("d_rev", F_REV), sp.getBoolean("d_mus", F_MUS));
            status.setText("↩️ رجعت الإعدادات الافتراضية.");
        });
        factory.setOnClickListener(v -> {
            SharedPreferences.Editor e = sp.edit();
            e.remove("d_n").remove("d_min").remove("d_max").remove("d_res").remove("d_text").remove("d_rev").remove("d_mus").apply();
            applyValues(F_N, F_MIN, F_MAX, F_RES, F_TEXT, F_REV, F_MUS);
            status.setText("🏭 رجعت الإعدادات الأصلية.");
        });

        rights = check(root, "أؤكد أني أملك حقوق الفيديو أو مصرّح لي", false);

        go = new Button(this);
        go.setText("ابدأ الآن ☁️");
        go.setAllCaps(false);
        go.setTextSize(17);
        go.setTextColor(Color.WHITE);
        go.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{ACC, ACC2});
        gd.setCornerRadius(dp(14));
        go.setBackground(gd);
        go.setStateListAnimator(null);
        LinearLayout.LayoutParams gl = lp(-1, dp(54), 0, 0);
        gl.topMargin = dp(10);
        root.addView(go, gl);

        TextView eta = new TextView(this);
        eta.setText("⏱ الوقت المتوقع: من 3 لـ 8 دقايق (حتى لو الفيديو ساعتين)");
        eta.setTextSize(11);
        eta.setTextColor(DIM);
        eta.setGravity(Gravity.CENTER);
        root.addView(eta);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACC2));
        bar.setVisibility(View.INVISIBLE);
        root.addView(bar, lp(-1, dp(4), 0, 0));
        status = new TextView(this);
        status.setTextColor(TXT);
        status.setTextSize(12);
        status.setTextIsSelectable(true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(8), 0, dp(8));
        status.setText("جاهز.");
        root.addView(status, lp(-1, -2, 0, 0));

        last = new Button(this);
        last.setText("⬇️ نزّل آخر ملف جاهز");
        last.setAllCaps(false);
        last.setTextColor(TXT);
        last.setTextSize(13);
        last.setBackground(shape(CARD, 12));
        last.setStateListAnimator(null);
        root.addView(last, lp(-1, dp(42), 0, 0));

        battery = pill("🔋 اسمح للتطبيق يشتغل في الخلفية بدون قيود", false);
        LinearLayout.LayoutParams bl = lp(-1, dp(42), 0, 0);
        bl.topMargin = dp(8);
        root.addView(battery, bl);
        battery.setOnClickListener(v -> askBattery());

        go.setOnClickListener(v -> start());
        last.setOnClickListener(v -> downloadLast());
        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        } else if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sp.registerOnSharedPreferenceChangeListener(listener);
        // لو في عملية شغّالة والخدمة اتقتلت (مثلًا من النظام)، نشغّلها تاني
        if (isRunning() && !JobService.alive) startJobService();
        render();
    }

    @Override
    protected void onPause() {
        sp.unregisterOnSharedPreferenceChangeListener(listener);
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
        sp.edit().putString("n", String.valueOf(n)).putString("min", String.valueOf(mn)).putString("max", String.valueOf(mx))
                .putString("res", resv).putString("text", text.getText().toString())
                .putBoolean("rev", reverse.isChecked()).putBoolean("mus", music.isChecked()).apply();
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
            body = new JSONObject().put("ref", "main").put("inputs", in).toString();
        } catch (Exception e) { status.setText("خطأ في الإعدادات."); return; }

        // كل حالة العملية بتتحفظ، فالخدمة تكمّل لو اتقتلت أو الجهاز اتعاد تشغيله
        sp.edit().putString("job", job).putString("body", body).putString("stage", "NEW")
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
