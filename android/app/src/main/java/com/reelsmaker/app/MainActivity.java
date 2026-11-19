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
import android.widget.ScrollView;
import android.widget.Spinner;
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
    Spinner res;
    CheckBox reverse, music, rights;
    TextView status;
    ProgressBar bar;
    Button go, last;
    volatile boolean busy = false;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView label(LinearLayout p, String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setPadding(0, dp(12), 0, dp(2));
        p.addView(t);
        return t;
    }

    EditText field(LinearLayout p, String lbl, String val, int type) {
        label(p, lbl);
        EditText e = new EditText(this);
        e.setText(val);
        e.setInputType(type);
        e.setSingleLine(type != InputType.TYPE_CLASS_TEXT);
        p.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    CheckBox check(LinearLayout p, String s, boolean v) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setChecked(v);
        p.addView(c);
        return c;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        ScrollView sv = new ScrollView(this);
        sv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(32));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("🎬 صانع الريلز");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("الصق الرابط وخلاص — الشغل كله على سيرفر، مش على موبايلك ولا نتك");
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        link = field(root, "🔗 رابط الفيديو", "", InputType.TYPE_TEXT_VARIATION_URI);
        count = field(root, "عدد الريلز", sp.getString("n", "5"), InputType.TYPE_CLASS_NUMBER);
        minL = field(root, "أقل مدة (ثواني)", sp.getString("min", "15"), InputType.TYPE_CLASS_NUMBER);
        maxL = field(root, "أقصى مدة (ثواني)", sp.getString("max", "25"), InputType.TYPE_CLASS_NUMBER);
        label(root, "الجودة");
        res = new Spinner(this);
        res.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"1080", "720"}));
        res.setSelection("720".equals(sp.getString("res", "1080")) ? 1 : 0);
        root.addView(res);
        text = field(root, "النص العربي على الفيديو",
                sp.getString("text", "لمشاهدة وتحميل كاملا الرابط في البايو الملف الشخصي"), InputType.TYPE_CLASS_TEXT);
        reverse = check(root, "عكس الفيديو (Mirror)", sp.getBoolean("rev", false));
        music = check(root, "موسيقى هادئة في الخلفية", sp.getBoolean("mus", true));
        rights = check(root, "أؤكد أني أملك حقوق الفيديو أو مصرّح لي", false);

        go = new Button(this);
        go.setText("ابدأ ☁️");
        root.addView(go);
        last = new Button(this);
        last.setText("⬇️ نزّل آخر ملف جاهز");
        root.addView(last);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setVisibility(View.GONE);
        root.addView(bar);
        status = new TextView(this);
        status.setPadding(0, dp(12), 0, dp(12));
        status.setText("جاهز.");
        root.addView(status);

        go.setOnClickListener(v -> start());
        last.setOnClickListener(v -> downloadLast());
        setContentView(sv);
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
                .putString("res", res.getSelectedItem().toString())
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
            in.put("res", res.getSelectedItem().toString());
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
        for (int i = 0; i < 60 && id == 0; i++) {
            id = findRun(job);
            if (id == 0) Thread.sleep(4000);
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
                throw new Exception("الشغل فشل (" + con + "). غالبًا الرابط مش مدعوم أو الموقع منع التحميل. جرّب رابط تاني.");
            }
            ui("☁️ السيرفر شغّال... (" + mins + " د) — تقدر تسيب التطبيق وترجع تدوس «نزّل آخر ملف»");
            if (mins > 100) throw new Exception("الشغل طوّل أوي.");
            Thread.sleep(15000);
        }
        downloadTag("reels-" + job);
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
