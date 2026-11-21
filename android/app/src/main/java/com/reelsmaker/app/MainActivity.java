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
import android.provider.OpenableColumns;
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
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

/**
 * صانع الريلز: بتلصق الرابط وبس. الشغل كله على سيرفرات GitHub المجانية،
 * ومتابعة العملية والتنزيل بتتم في خدمة خلفية (JobService) بتكمّل حتى لو قفلت الشاشة أو التطبيق أو النت فصل.
 * الصفحة الرئيسية ثابتة على شاشة واحدة (من غير أي تمرير طلوع أو نزول). الخصائص في شاشة مستقلة (FeaturesActivity).
 */
public class MainActivity extends Activity {
    SharedPreferences sp;
    EditText link, count, minL, maxL, text, musicUrl;
    String resv = Feat.F_RES, mvolv = Feat.F_MVOL;
    Button qBtn, mvBtn, go, last, battery, feats, pickMusic, ttBtn, fbBtn, igBtn;
    CheckBox reverse, music, rights, rsong, react;
    static final int REQ_MUSIC = 21;
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
        qBtn.setText("1080".equals(resv) ? "1080 · عالية" : "720 · أسرع");
    }

    /** مستوى صوت الموسيقى: 8 = قياسية • 15 = وسط • 25 = عالية (زر واحد بيلف عليهم). */
    void setMvol(String v) {
        mvolv = "15".equals(v) ? "15" : "25".equals(v) ? "25" : "8";
        mvBtn.setText("🔊 " + ("15".equals(mvolv) ? "وسط" : "25".equals(mvolv) ? "عالية" : "قياسية"));
    }

    LinearLayout.LayoutParams rowLp(int h) {
        LinearLayout.LayoutParams l = lp(-1, h, 0, 0);
        l.topMargin = dp(4);
        return l;
    }

    Button tiny(String s) {
        Button b = pill(s, false);
        b.setTextSize(12);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(dp(2), 0, dp(2), 0);
        return b;
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
        setMvol(Feat.str(sp, "mvol", Feat.F_MVOL));
        musicUrl.setText(Feat.str(sp, "murl", Feat.F_MURL));
        rsong.setChecked(Feat.bool(sp, "rsong", Feat.F_RSONG));
        react.setChecked(Feat.bool(sp, "react", Feat.F_REACT));
    }

    /** بيحفظ اللي على الشاشة كقيم حالية (عشان شاشة الخصائص تقدر تحفظها كافتراضي). */
    void saveCurrent() {
        sp.edit().putString("n", count.getText().toString().trim())
                .putString("min", minL.getText().toString().trim())
                .putString("max", maxL.getText().toString().trim())
                .putString("res", resv)
                .putString("text", text.getText().toString())
                .putBoolean("rev", reverse.isChecked())
                .putBoolean("mus", music.isChecked())
                .putString("mvol", mvolv)
                .putString("murl", musicUrl.getText().toString().trim())
                .putBoolean("rsong", rsong.isChecked())
                .putBoolean("react", react.isChecked()).apply();
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
        root.setPadding(dp(14), dp(8), dp(14), dp(8));

        TextView title = new TextView(this);
        title.setText("🎬 صانع الريلز");
        title.setTextSize(20);
        title.setTextColor(TXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        LinearLayout lr = new LinearLayout(this);
        link = box("🔗 الصق رابط الفيديو هنا — https://...", "", InputType.TYPE_TEXT_VARIATION_URI);
        link.setTextSize(13);
        Button paste = pill("لصق", true);
        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (t != null) link.setText(t.toString().trim());
            }
        });
        lr.addView(link, lp(0, dp(42), 1, 0));
        lr.addView(paste, lp(dp(60), dp(42), 0, 0));
        root.addView(lr, rowLp(-2));

        // عدد + أقل + أقصى + الجودة: كلهم في صف واحد (الأسماء في الخانات نفسها)
        LinearLayout nr = new LinearLayout(this);
        String[] hints = {"عدد", "أقل (ث)", "أقصى (ث)"};
        EditText[] es = new EditText[3];
        for (int i = 0; i < 3; i++) {
            es[i] = box(hints[i], "", InputType.TYPE_CLASS_NUMBER);
            es[i].setGravity(Gravity.CENTER);
            es[i].setTextSize(13);
            nr.addView(es[i], lp(0, dp(40), 1, 2));
        }
        count = es[0];
        minL = es[1];
        maxL = es[2];
        qBtn = tiny("1080 · عالية");
        qBtn.setBackground(shape(ACC, 12));
        qBtn.setOnClickListener(v -> setRes("1080".equals(resv) ? "720" : "1080"));
        nr.addView(qBtn, lp(0, dp(40), 1.3f, 2));
        root.addView(nr, rowLp(-2));

        text = box("النص العربي على الفيديو (سطر واحد)", "", InputType.TYPE_CLASS_TEXT);
        text.setTextSize(13);
        root.addView(text, rowLp(dp(40)));

        // الخيارات: صفّين من خيارين + الحقوق
        LinearLayout cr = new LinearLayout(this);
        reverse = check(cr, "عكس الفيديو", false);
        music = check(cr, "موسيقى خلفية", false);
        root.addView(cr);

        LinearLayout cr2 = new LinearLayout(this);
        rsong = check(cr2, "🎶 أغاني تلقائي", true);
        rsong.setOnClickListener(v -> toast(rsong.isChecked()
                ? "لو الفيديو فيه أغنية، الموسيقى الأساسية هتشتغل مكانها تلقائيًا."
                : "هيفضل صوت الفيديو الأصلي زي ما هو."));
        react = check(cr2, "🎭 ريأكشن", false);
        react.setOnClickListener(v -> toast(react.isChecked()
                ? "تكبير بسيط + لمسة ألوان + سرعة 1.03. وده مش بيضمن تفادي أي مطالبة حقوق."
                : "وضع الريأكشن اتقفل."));
        root.addView(cr2);

        rights = check(root, "أؤكد أني أملك حقوق الفيديو أو مصرّح لي", false);

        // الموسيقى في صف واحد: مستوى الصوت (ضغطة بتلف) + رابط/ملف (فاضي = المدمجة)
        LinearLayout mr = new LinearLayout(this);
        mvBtn = tiny("🔊 قياسية");
        mvBtn.setOnClickListener(v -> setMvol("8".equals(mvolv) ? "15" : "15".equals(mvolv) ? "25" : "8"));
        musicUrl = box("🎵 رابط موسيقى أو ملف (فاضي = المدمجة)", "", InputType.TYPE_TEXT_VARIATION_URI);
        musicUrl.setTextSize(12);
        pickMusic = tiny("📁 رفع");
        pickMusic.setBackground(shape(ACC, 12));
        pickMusic.setOnClickListener(v -> pickMusicFile());
        mr.addView(mvBtn, lp(dp(84), dp(40), 0, 2));
        mr.addView(musicUrl, lp(0, dp(40), 1, 2));
        mr.addView(pickMusic, lp(dp(64), dp(40), 0, 2));
        root.addView(mr, rowLp(-2));

        // الخصائص + منصات النشر في صف واحد (✓ = مفعّل للنشر من السيرفر)
        LinearLayout pubRow = new LinearLayout(this);
        feats = tiny("✨ الخصائص");
        feats.setOnClickListener(v -> startActivity(new Intent(this, FeaturesActivity.class)));
        ttBtn = tiny("🎵 تيك توك");
        ttBtn.setOnClickListener(v -> startActivity(new Intent(this, TikTokActivity.class)));
        fbBtn = tiny("📘 فيسبوك");
        fbBtn.setOnClickListener(v -> startActivity(new Intent(this, FbActivity.class)));
        igBtn = tiny("📷 إنستا");
        igBtn.setOnClickListener(v -> startActivity(new Intent(this, IgActivity.class)));
        pubRow.addView(feats, lp(0, dp(42), 1.25f, 2));
        pubRow.addView(ttBtn, lp(0, dp(42), 1, 2));
        pubRow.addView(fbBtn, lp(0, dp(42), 1, 2));
        pubRow.addView(igBtn, lp(0, dp(42), 1, 2));
        root.addView(pubRow, rowLp(-2));

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
        LinearLayout.LayoutParams gl = lp(-1, dp(48), 0, 0);
        gl.topMargin = dp(6);
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
        status.setText("جاهز. الصق الرابط والشغل كله على السيرفر ⏱ من 3 لـ 8 دقايق.");
        status.setOnClickListener(v -> {
            CharSequence t = status.getText();
            if (t == null || t.length() == 0) return;
            new android.app.AlertDialog.Builder(this).setMessage(t).setPositiveButton("تمام", null).show();
        });
        LinearLayout.LayoutParams stp = lp(-1, 0, 1, 0);
        root.addView(status, stp);

        LinearLayout br = new LinearLayout(this);
        last = tiny("⬇️ نزّل آخر ملف");
        battery = tiny("🔋 اسمح بالخلفية");
        br.addView(last, lp(0, dp(38), 1, 2));
        br.addView(battery, lp(0, dp(38), 1, 2));
        root.addView(br);
        battery.setOnClickListener(v -> askBattery());

        go.setOnClickListener(v -> start());
        last.setOnClickListener(v -> downloadLast());
        // الصفحة بتفضل على شاشة واحدة؛ ولو الموبايل صغير والصف الإضافي مش مكفّي، بيتمرّر بدل ما الحالة تتقلّص
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
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
        feats.setText("✨ الخصائص (" + Feat.enabledCount(sp) + ")");
        ttBtn.setText("🎵 تيك توك" + (sp.getBoolean("tt_server", false) ? " ✓" : ""));
        fbBtn.setText("📘 فيسبوك" + (sp.getBoolean("fb_server", false) ? " ✓" : ""));
        igBtn.setText("📷 إنستا" + (sp.getBoolean("ig_server", false) ? " ✓" : ""));
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
            in.put("mvol", mvolv);
            in.put("music_url", musicUrl.getText().toString().trim());
            in.put("rsong", String.valueOf(rsong.isChecked()));
            in.put("react", String.valueOf(react.isChecked()));
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
            boolean igOn = sp.getBoolean("ig_server", false);
            if (igOn && sp.getString("ig_accs", "[]").length() < 5) {
                status.setText("فعّلت النشر على إنستجرام بس مفيش حسابات. أضفها من «📷 إنستا».");
                return;
            }
            in.put("ig", String.valueOf(igOn));
            in.put("ig_accs", igOn ? sp.getString("ig_accs", "[]") : "[]");
            in.put("ig_caption", sp.getString("ig_caption", ""));
            body = new JSONObject().put("ref", "main").put("inputs", in).toString();
        } catch (Exception e) { status.setText("خطأ في الإعدادات."); return; }

        // كل حالة العملية بتتحفظ، فالخدمة تكمّل لو اتقتلت أو الجهاز اتعاد تشغيله
        final boolean ttS = sp.getBoolean("tt_server", false), fbS = sp.getBoolean("fb_server", false), igS = sp.getBoolean("ig_server", false);
        sp.edit().putString("job", job).putString("body", body).putString("stage", "NEW")
                .putBoolean("job_nodl", (ttS || fbS || igS) && (!ttS || sp.getBoolean("tt_nodl", false)) && (!fbS || sp.getBoolean("fb_nodl", false)) && (!igS || sp.getBoolean("ig_nodl", false)))
                .putBoolean("job_tt", ttS).putBoolean("job_fb", fbS).putBoolean("job_ig", igS)
                .putBoolean("posted", false).putLong("t0", System.currentTimeMillis())
                .putString("msg", "ببعت الطلب للسيرفر...").putInt("pct", 0).apply();
        link.setText("");   // الرابط الجاي جديد؛ باقي الإعدادات زي ما هي
        startJobService();
        render();
    }

    // ───────── رفع ملف موسيقى من الجهاز (بيترفع كملف إصدار على الريبو، ويتبعت رابطه للسيرفر) ─────────
    void toast(String t) {
        android.widget.Toast.makeText(this, t, android.widget.Toast.LENGTH_SHORT).show();
    }

    void pickMusicFile() {
        if (noService()) return;
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("audio/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "اختار ملف موسيقى"), REQ_MUSIC);
        } catch (Throwable e) {
            status.setText("مفيش تطبيق ملفات يقدر يفتح الاختيار.");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_MUSIC && res == RESULT_OK && data != null && data.getData() != null) uploadMusic(data.getData());
    }

    void uploadMusic(final Uri uri) {
        status.setText("⏳ برفع الموسيقى...");
        pickMusic.setEnabled(false);
        new Thread(() -> {
            String url = null, err = null;
            try {
                url = doUpload(uri);
            } catch (Throwable e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String fu = url, fe = err;
            runOnUiThread(() -> {
                pickMusic.setEnabled(true);
                if (fu != null) {
                    musicUrl.setText(fu);
                    saveCurrent();
                    status.setText("✅ اترفعت الموسيقى. هتتستخدم في الريل الجاي.");
                } else {
                    status.setText("❌ فشل رفع الموسيقى: " + fe + "\nتقدر تلزق رابط مباشر بدل الرفع.");
                }
            });
        }).start();
    }

    String doUpload(Uri uri) throws Exception {
        String name = "music.mp3";
        long size = -1;
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME), si = c.getColumnIndex(OpenableColumns.SIZE);
                if (ni >= 0 && c.getString(ni) != null) name = c.getString(ni);
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
            }
        } catch (Throwable ignored) { }
        if (size > 25L * 1024 * 1024) throw new Exception("الملف أكبر من 25 م.ب");
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "mp3";
        if (!ext.matches("mp3|m4a|aac|wav|ogg|oga|opus|flac")) ext = "mp3";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("تعذّرت قراءة الملف");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 25 * 1024 * 1024) throw new Exception("الملف أكبر من 25 م.ب");
            }
        }
        byte[] data = bo.toByteArray();
        if (data.length < 2048) throw new Exception("الملف فاضي أو صغير جدًا");

        // امسح موسيقى اتـرفعت قبل كده (عشان الريبو ما يتملاش)
        String old = sp.getString("music_tag", "");
        if (!old.isEmpty()) {
            try {
                String[] r = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO + "/releases/tags/" + old, null);
                if (r[0].startsWith("2")) {
                    Gh.http("DELETE", Gh.API + "/repos/" + Gh.REPO + "/releases/" + new JSONObject(r[1]).getLong("id"), null);
                }
                Gh.http("DELETE", Gh.API + "/repos/" + Gh.REPO + "/git/refs/tags/" + old, null);
            } catch (Throwable ignored) { }
        }
        String tag = "music-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String body = new JSONObject().put("tag_name", tag).put("name", "music").put("body", "music").put("prerelease", true).toString();
        String[] cr = Gh.http("POST", Gh.API + "/repos/" + Gh.REPO + "/releases", body);
        int code = Integer.parseInt(cr[0]);
        if (code == 401 || code == 403 || code == 404) throw new Exception("التوكن مالوش صلاحية رفع ملفات على الريبو (محتاج Contents: write)");
        if (code < 200 || code >= 300) throw new Exception("خطأ " + code);
        long rid = new JSONObject(cr[1]).getLong("id");

        String mime = "wav".equals(ext) ? "audio/wav" : "ogg".equals(ext) || "oga".equals(ext) || "opus".equals(ext) ? "audio/ogg"
                : "m4a".equals(ext) || "aac".equals(ext) ? "audio/mp4" : "flac".equals(ext) ? "audio/flac" : "audio/mpeg";
        String aname = "music." + ext;
        HttpURLConnection c = Gh.open("POST", "https://uploads.github.com/repos/" + Gh.REPO + "/releases/" + rid + "/assets?name="
                + URLEncoder.encode(aname, "UTF-8"), true);
        try {
            c.setReadTimeout(120000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", mime);
            c.setFixedLengthStreamingMode(data.length);
            try (OutputStream o = c.getOutputStream()) {
                o.write(data);
            }
            int rc = c.getResponseCode();
            if (rc < 200 || rc >= 300) throw new Exception("خطأ رفع " + rc);
        } finally {
            c.disconnect();
        }
        sp.edit().putString("music_tag", tag).apply();
        return "https://github.com/" + Gh.REPO + "/releases/download/" + tag + "/" + aname;
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
