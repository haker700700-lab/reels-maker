package com.reelsmaker.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** صانع الريلز: بيبعت الرابط لـ GitHub Actions، يستنى، وينزّل reels.zip في التنزيلات. مفيش أي مكتبات خارجية. */
public class MainActivity extends Activity {
    static final String API = "https://api.github.com";
    static final String REPO = BuildConfig.APP_REPO;
    static final String TOK = BuildConfig.APP_TOKEN;
    SharedPreferences sp;
    EditText link, count, minL, maxL, text;
    String resv = "1080";
    Button q720, q1080;
    CheckBox reverse, music, rights;
    TextView status;
    ProgressBar bar;
    Button go, last;
    volatile boolean busy = false;

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
        b.setTextSize(14);
        b.setBackground(shape(on ? ACC : CARD, 12));
        b.setStateListAnimator(null);
        return b;
    }

    void setRes(String r) {
        resv = r;
        q720.setBackground(shape("720".equals(r) ? ACC : CARD, 12));
        q1080.setBackground(shape("1080".equals(r) ? ACC : CARD, 12));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(18), dp(22), dp(18), dp(14));

        TextView title = new TextView(this);
        title.setText("🎬 صانع الريلز");
        title.setTextSize(26);
        title.setTextColor(TXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("الصق أي رابط فيه فيديو — والشغل كله على السيرفر");
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
        String[] vals = {sp.getString("n", "5"), sp.getString("min", "15"), sp.getString("max", "25")};
        for (int i = 0; i < 3; i++) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            es[i] = field(c, lbls[i], vals[i], InputType.TYPE_CLASS_NUMBER);
            es[i].setGravity(Gravity.CENTER);
            nr.addView(c, lp(0, -2, 1, 3));
        }
        count = es[0]; minL = es[1]; maxL = es[2];
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
        setRes(sp.getString("res", "1080"));

        text = field(root, "النص العربي على الفيديو",
                sp.getString("text", "لمشاهدة وتحميل كاملا الرابط في البايو الملف الشخصي"), InputType.TYPE_CLASS_TEXT);

        LinearLayout cr = new LinearLayout(this);
        reverse = check(cr, "عكس الفيديو", sp.getBoolean("rev", false));
        music = check(cr, "موسيقى هادئة", sp.getBoolean("mus", true));
        root.addView(cr);
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
        status.setMaxLines(14);
        status.setText("جاهز.");
        root.addView(status, lp(-1, 0, 1, 0));
        last = new Button(this);
        last.setText("⬇️ نزّل آخر ملف جاهز");
        last.setAllCaps(false);
        last.setTextColor(TXT);
        last.setTextSize(13);
        last.setBackground(shape(CARD, 12));
        last.setStateListAnimator(null);
        root.addView(last, lp(-1, dp(42), 0, 0));

        go.setOnClickListener(v -> start());
        last.setOnClickListener(v -> downloadLast());
        setContentView(root);
    }

    // ───────── أدوات ─────────
    void ui(String s) { runOnUiThread(() -> status.setText(s)); }

    void busy(boolean on) {
        busy = on;
        runOnUiThread(() -> {
            bar.setVisibility(on ? View.VISIBLE : View.GONE);
            go.setEnabled(!on);
            last.setEnabled(!on);
            if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    void saveSettings() {
        sp.edit().putString("n", count.getText().toString().trim())
                .putString("min", minL.getText().toString().trim())
                .putString("max", maxL.getText().toString().trim())
                .putString("res", resv)
                .putString("text", text.getText().toString())
                .putBoolean("rev", reverse.isChecked())
                .putBoolean("mus", music.isChecked()).apply();
    }

    /** returns {code, body} */
    String[] http(String method, String url, String body, String accept) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Authorization", "Bearer " + TOK);
        c.setRequestProperty("Accept", accept == null ? "application/vnd.github+json" : accept);
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        c.setRequestProperty("User-Agent", "ReelsMaker-App");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String ln;
                while ((ln = r.readLine()) != null) sb.append(ln).append('\n');
            }
        }
        return new String[]{String.valueOf(code), sb.toString()};
    }

    String explain(String[] x) {
        String c = x[0];
        if ("401".equals(c)) return "الخدمة مش متاحة حاليًا (التوكن منتهي). كلّم صاحب التطبيق.";
        if ("403".equals(c)) return "الخدمة مشغولة أو متوقفة مؤقتًا — جرّب بعد شوية.";
        if ("404".equals(c)) return "الخدمة مش موجودة (المستودع أو الملف).";
        return "خطأ " + c + ": " + x[1].replace('\n', ' ');
    }

    boolean noService() {
        if (TOK.isEmpty() || REPO.isEmpty()) {
            ui("التطبيق اتبنى من غير بيانات الخدمة. اعمل بناء جديد من صاحب التطبيق.");
            return true;
        }
        return false;
    }

    /** بيدوّر على تشغيل بنفس رقم العملية بتاعنا (كل مستخدم له رقم مختلف، فمفيش خلط). */
    long findRun(String job) throws Exception {
        String[] x = http("GET", API + "/repos/" + REPO + "/actions/workflows/reels.yml/runs?per_page=30", null, null);
        if (!"200".equals(x[0])) throw new Exception(explain(x));
        JSONArray a = new JSONObject(x[1]).getJSONArray("workflow_runs");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            if (("reels " + job).equals(o.optString("display_title"))) return o.getLong("id");
        }
        return 0;
    }

    // ───────── التشغيل ─────────
    void start() {
        if (busy || noService()) return;
        final String url = link.getText().toString().trim();
        if (url.isEmpty()) { ui("الصق رابط الفيديو الأول."); return; }
        if (!rights.isChecked()) { ui("علّم على «أؤكد أني أملك حقوق الفيديو»."); return; }
        saveSettings();
        final String job = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        final String body;
        try {
            JSONObject in = new JSONObject();
            in.put("job", job);
            in.put("url", url);
            in.put("rights", "true");
            in.put("n", count.getText().toString().trim());
            in.put("min_l", minL.getText().toString().trim());
            in.put("max_l", maxL.getText().toString().trim());
            in.put("res", resv);
            in.put("reverse", String.valueOf(reverse.isChecked()));
            in.put("text", text.getText().toString());
            in.put("music", String.valueOf(music.isChecked()));
            body = new JSONObject().put("ref", "main").put("inputs", in).toString();
        } catch (Exception e) { ui("خطأ في الإعدادات."); return; }

        busy(true);
        new Thread(() -> {
            try {
                ui("ببعت الطلب للسيرفر...");
                String[] d = http("POST", API + "/repos/" + REPO + "/actions/workflows/reels.yml/dispatches", body, null);
                if (!"204".equals(d[0])) throw new Exception(explain(d));
                sp.edit().putString("job", job).apply();
                track(job);
            } catch (Exception e) {
                ui("❌ " + e.getMessage());
            } finally { busy(false); }
        }).start();
    }

    /** بيتابع العملية لحد ما تخلص وبعدين ينزّل الملف. */
    void track(String job) throws Exception {
        long id = 0;
        for (int i = 0; i < 90 && id == 0; i++) {
            id = findRun(job);
            if (id == 0) Thread.sleep(2000);
        }
        if (id == 0) throw new Exception("السيرفر ما بدأش الشغل — جرّب تاني.");
        long t0 = System.currentTimeMillis();
        while (true) {
            String[] s = http("GET", API + "/repos/" + REPO + "/actions/runs/" + id, null, null);
            if (!"200".equals(s[0])) throw new Exception(explain(s));
            JSONObject j = new JSONObject(s[1]);
            String st = j.getString("status");
            long mins = (System.currentTimeMillis() - t0) / 60000;
            if ("completed".equals(st)) {
                String con = j.optString("conclusion");
                if ("success".equals(con)) break;
                throw new Exception(failReason(job, con));
            }
            ui("☁️ السيرفر شغّال... (" + mins + " د) — تقدر تسيب التطبيق وترجع تدوس «نزّل آخر ملف»");
            if (mins > 100) throw new Exception("الشغل طوّل أوي.");
            Thread.sleep(5000);
        }
        downloadTag("reels-" + job);
    }

    /** بيجيب سبب الفشل الحقيقي اللي السيرفر سجّله. */
    String failReason(String job, String con) {
        try {
            String[] x = http("GET", API + "/repos/" + REPO + "/releases/tags/err-" + job, null, null);
            if ("200".equals(x[0])) {
                String b = new JSONObject(x[1]).optString("body", "").trim();
                if (!b.isEmpty()) return b.length() > 700 ? b.substring(0, 700) : b;
            }
        } catch (Exception ignored) { }
        return "الشغل فشل (" + con + "). جرّب رابط تاني.";
    }

    void downloadLast() {
        if (busy || noService()) return;
        final String job = sp.getString("job", "");
        if (job.isEmpty()) { ui("لسه معملتش أي عملية."); return; }
        busy(true);
        new Thread(() -> {
            try {
                ui("بتأكد من آخر عملية...");
                track(job);
            } catch (Exception e) {
                ui("❌ " + e.getMessage());
            } finally { busy(false); }
        }).start();
    }

    void downloadTag(String tag) throws Exception {
        String r = REPO;
        String[] x = http("GET", API + "/repos/" + r + "/releases/tags/" + tag, null, null);
        if (!"200".equals(x[0])) throw new Exception(explain(x));
        JSONArray assets = new JSONObject(x[1]).getJSONArray("assets");
        long aid = 0;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject o = assets.getJSONObject(i);
            if ("reels.zip".equals(o.getString("name"))) aid = o.getLong("id");
        }
        if (aid == 0) throw new Exception("ملف reels.zip مش موجود في الإصدار.");
        save(r, aid, tag + ".zip");
    }

    /** بينزّل الملف (من غير ما نبعت التوكن للسيرفر التاني بتاع التخزين) ويحفظه في التنزيلات. */
    void save(String r, long assetId, String name) throws Exception {
        ui("⬇️ بنزّل " + name + " ...");
        HttpURLConnection c = (HttpURLConnection) new URL(API + "/repos/" + r + "/releases/assets/" + assetId).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Authorization", "Bearer " + TOK);
        c.setRequestProperty("Accept", "application/octet-stream");
        c.setRequestProperty("User-Agent", "ReelsMaker-App");
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        int code = c.getResponseCode();
        HttpURLConnection d = c;
        if (code >= 300 && code < 400) {
            String loc = c.getHeaderField("Location");
            d = (HttpURLConnection) new URL(loc).openConnection();
            d.setConnectTimeout(20000);
            d.setReadTimeout(60000);
            d.setRequestProperty("User-Agent", "ReelsMaker-App");
            code = d.getResponseCode();
        }
        if (code != 200) throw new Exception("فشل التنزيل (" + code + ").");
        long total = d.getContentLength();

        if (Build.VERSION.SDK_INT >= 29) {
            storeMediaStore(d, name, total);
        } else {
            storeLegacy(d, name, total);
        }
        ui("✅ خلصنا! الملف في «التنزيلات»: " + name);
    }

    void progress(long done, long total) {
        ui("⬇️ " + (done >> 20) + (total > 0 ? " / " + (total >> 20) : "") + " MB");
    }

    /** أندرويد 10 وما فوق: المكان العام «التنزيلات» من غير صلاحيات. */
    @android.annotation.TargetApi(29)
    void storeMediaStore(HttpURLConnection d, String name, long total) throws Exception {
        ContentResolver cr = getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
        v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        v.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new Exception("مقدرتش أكتب في التنزيلات.");
        try (InputStream in = d.getInputStream(); OutputStream out = cr.openOutputStream(uri)) {
            copy(in, out, total);
        } catch (Exception e) {
            cr.delete(uri, null, null);
            throw e;
        }
        v.clear();
        v.put(MediaStore.Downloads.IS_PENDING, 0);
        cr.update(uri, v, null, null);
    }

    /** أندرويد 6 لـ 9: بنكتب في مجلد التنزيلات مباشرة بعد إذن التخزين. */
    void storeLegacy(HttpURLConnection d, String name, long total) throws Exception {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7));
            throw new Exception("اسمح للتطبيق بالتخزين (النافذة اللي ظهرت)، وبعدين دوس «نزّل آخر ملف جاهز».");
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("مقدرتش أعمل مجلد التنزيلات.");
        File f = new File(dir, name);
        try (InputStream in = d.getInputStream(); OutputStream out = new FileOutputStream(f)) {
            copy(in, out, total);
        } catch (Exception e) {
            f.delete();
            throw e;
        }
        MediaScannerConnection.scanFile(this, new String[]{f.getAbsolutePath()}, new String[]{"application/zip"}, null);
    }

    void copy(InputStream in, OutputStream out, long total) throws Exception {
        byte[] buf = new byte[1 << 16];
        long done = 0, lastShown = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            done += n;
            if (done - lastShown > (1 << 20)) {
                lastShown = done;
                progress(done, total);
            }
        }
    }
}
