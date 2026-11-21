package com.reelsmaker.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

/** حسابات التك توك (بيانات مشفّرة) + إعدادات النشر من السيرفر + بديل النشر اليدوي عبر تطبيق التك توك. */
public class TikTokActivity extends Activity {
    static final String[] PKGS = {"com.zhiliaoapp.musically", "com.ss.android.ugc.trill"};
    SharedPreferences sp;
    TextView info;
    EditText caption;
    Button next;
    File pending;
    LinearLayout accList;
    EditText nameBox, keyBox, secretBox, redirBox, codeBox;
    Button addBtn;
    static final String API = "https://open.tiktokapis.com";

    /** حسابات تسجيل الدخول القديمة (كوكيز/باسورد) مابقتش بتشتغل: بنشيلها ونبلّغ. */
    void dropLegacy() {
        try {
            JSONArray a = accounts(), out = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && ("api".equals(o.optString("t")) || "ck".equals(o.optString("t")))) out.put(o);
            }
            if (out.length() != a.length()) {
                sp.edit().putString("tt_accs", out.toString()).apply();
                Toast.makeText(this, "اتشالت الحسابات القديمة (تسجيل الدخول). أضفها بالتوكن.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ignored) { }
    }

    static final int REQ_LOGIN = 77;

    void openLogin() {
        if (!TikCrypto.ready()) { toast("التطبيق ده اتبنى من غير مفتاح التشفير. حدّثه بالأمر الجديد."); return; }
        startActivityForResult(new Intent(this, TikLoginActivity.class), REQ_LOGIN);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_LOGIN || res != RESULT_OK || data == null) return;
        String ck = data.getStringExtra("cookies");
        if (ck == null || ck.isEmpty()) return;
        final String nm = nameBox.getText().toString().trim().replaceAll("[^A-Za-z0-9_-]", "");
        final String cookies = ck;
        new Thread(() -> {
            String msg;
            try {
                String disp = "";
                String n = !nm.isEmpty() ? nm : "c" + Integer.toHexString(cookies.hashCode() & 0xffffff);
                JSONObject blob = new JSONObject().put("cookies", cookies);
                JSONArray cur = accounts(), out = new JSONArray();
                for (int i = 0; i < cur.length(); i++) {
                    JSONObject o = cur.optJSONObject(i);
                    if (o != null && !o.optString("n").equals(n)) out.put(o);
                }
                out.put(new JSONObject().put("n", n).put("d", n + " (كوكيز)").put("t", "ck").put("k", TikCrypto.encBlob(blob.toString())));
                sp.edit().putString("tt_accs", out.toString()).apply();
                msg = "اتضاف الحساب " + n + " بالكوكيز ✓";
            } catch (Exception e) {
                msg = "فشل حفظ الحساب: " + e.getClass().getSimpleName();
            }
            final String m = msg;
            runOnUiThread(() -> { toast(m); refreshAccs(); nameBox.setText(""); });
        }).start();
    }

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    JSONArray accounts() {
        try { return new JSONArray(sp.getString("tt_accs", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    EditText field(LinearLayout p, String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MainActivity.DIM);
        e.setTextColor(MainActivity.TXT);
        e.setSingleLine(true);
        e.setInputType(type);
        p.addView(e);
        return e;
    }

    void refreshAccs() {
        accList.removeAllViews();
        JSONArray a = accounts();
        if (a.length() == 0) {
            TextView t = new TextView(this);
            t.setText("مفيش حسابات لسه.");
            t.setTextColor(MainActivity.DIM);
            accList.addView(t);
        }
        for (int i = 0; i < a.length(); i++) {
            final String n = a.optJSONObject(i) == null ? "" : a.optJSONObject(i).optString("n");
            LinearLayout r = new LinearLayout(this);
            TextView t = new TextView(this);
            t.setText("👤 " + (a.optJSONObject(i) == null ? n : a.optJSONObject(i).optString("d", n)));
            t.setTextColor(MainActivity.TXT);
            Button d = new Button(this);
            d.setText("حذف");
            d.setAllCaps(false);
            d.setTextColor(Color.WHITE);
            d.setBackgroundColor(0xFF3A2A4D);
            d.setOnClickListener(v -> {
                try {
                    JSONArray cur = accounts(), out = new JSONArray();
                    for (int k = 0; k < cur.length(); k++) if (!cur.getJSONObject(k).getString("n").equals(n)) out.put(cur.getJSONObject(k));
                    sp.edit().putString("tt_accs", out.toString()).apply();
                } catch (Exception ignored) { }
                refreshAccs();
            });
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(d, new LinearLayout.LayoutParams(dp(80), dp(40)));
            accList.addView(r);
        }
    }

    // ───────── إضافة حساب من التوكن (Authorization code أو Refresh token) ─────────
    void openAuth() {
        String ck = keyBox.getText().toString().trim(), ru = redirBox.getText().toString().trim();
        if (ck.isEmpty() || ru.isEmpty()) { toast("اكتب Client key و Redirect URI الأول"); return; }
        sp.edit().putString("tt_ckey", ck).putString("tt_redir", ru).apply();
        try {
            String u = "https://www.tiktok.com/v2/auth/authorize/?client_key=" + URLEncoder.encode(ck, "UTF-8")
                    + "&scope=" + URLEncoder.encode("user.info.basic,video.publish,video.upload", "UTF-8")
                    + "&response_type=code&redirect_uri=" + URLEncoder.encode(ru, "UTF-8") + "&state=reels";
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } catch (Exception e) {
            toast("مقدرتش أفتح المتصفح");
        }
    }

    void addFromToken() {
        final String ck = keyBox.getText().toString().trim(), cs = secretBox.getText().toString().trim();
        final String ru = redirBox.getText().toString().trim(), in = codeBox.getText().toString().trim();
        final String nm = nameBox.getText().toString().trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (in.isEmpty()) { toast("الصق الرابط أو الكود الأول"); return; }
        if (!TikCrypto.ready()) { toast("التطبيق ده اتبنى من غير مفتاح التشفير. حدّثه بالأمر الجديد."); return; }
        sp.edit().putString("tt_ckey", ck).putString("tt_redir", ru).apply();
        addBtn.setEnabled(false);
        addBtn.setText("بضيف الحساب...");
        new Thread(() -> {
            String msg;
            try {
                msg = doAdd(ck, cs, ru, in, nm);
            } catch (Exception e) {
                msg = "فشل الاتصال بالتك توك. اتأكد من النت وجرّب تاني.";
            }
            final String m = msg;
            runOnUiThread(() -> {
                addBtn.setEnabled(true);
                addBtn.setText("➕ إضافة الحساب");
                toast(m);
                refreshAccs();
            });
        }).start();
    }

    /** بيرجّع رسالة للمستخدم. التوكن مابيتخزنش غير مشفّر. */
    String doAdd(String ck, String cs, String ru, String in, String nm) throws Exception {
        String access = "", refresh = "", openId = "";
        if (in.startsWith("act.")) {
            access = in;                       // Access token بس (بيعيش 24 ساعة)
        } else {
            if (ck.isEmpty() || cs.isEmpty()) return "اكتب Client key و Client secret الأول.";
            String body;
            if (in.startsWith("rft.")) {
                body = "client_key=" + enc(ck) + "&client_secret=" + enc(cs) + "&grant_type=refresh_token&refresh_token=" + enc(in);
            } else {
                String code = extractCode(in);
                if (code.isEmpty()) return "مالقيتش الكود في اللي لزقته. انسخ رابط الصفحة كله بعد الموافقة.";
                if (ru.isEmpty()) return "اكتب Redirect URI (نفس اللي في التفويض).";
                body = "client_key=" + enc(ck) + "&client_secret=" + enc(cs) + "&code=" + enc(code)
                        + "&grant_type=authorization_code&redirect_uri=" + enc(ru);
            }
            String[] r = http("POST", API + "/v2/oauth/token/", body, null);
            JSONObject j = new JSONObject(r[1].isEmpty() ? "{}" : r[1]);
            if (j.optString("access_token").isEmpty()) {
                String er = j.optString("error_description", j.optString("error", "HTTP " + r[0]));
                return "التك توك رفض: " + er + (in.startsWith("rft.") ? "" : " (الكود بيعيش 30 دقيقة ولمرة واحدة، جيب واحد جديد)");
            }
            access = j.optString("access_token");
            refresh = j.optString("refresh_token");
            openId = j.optString("open_id");
        }
        String disp = "";
        try {
            String[] u = http("GET", API + "/v2/user/info/?fields=open_id,display_name", null, access);
            JSONObject us = new JSONObject(u[1]).optJSONObject("data");
            if (us != null && us.optJSONObject("user") != null) {
                disp = us.getJSONObject("user").optString("display_name");
                if (openId.isEmpty()) openId = us.getJSONObject("user").optString("open_id");
            }
        } catch (Exception ignored) { }
        String n = !nm.isEmpty() ? nm : "t" + (openId.isEmpty() ? String.valueOf(accounts().length() + 1) : openId.replaceAll("[^A-Za-z0-9]", "").substring(0, Math.min(10, openId.replaceAll("[^A-Za-z0-9]", "").length())));
        JSONObject blob = new JSONObject().put("client_key", ck).put("client_secret", cs)
                .put("refresh_token", refresh).put("access_token", refresh.isEmpty() ? access : "").put("open_id", openId);
        JSONArray cur = accounts(), out = new JSONArray();
        for (int i = 0; i < cur.length(); i++) {
            JSONObject o = cur.optJSONObject(i);
            if (o != null && !o.optString("n").equals(n)) out.put(o);
        }
        out.put(new JSONObject().put("n", n).put("d", disp.isEmpty() ? n : disp).put("t", "api").put("k", TikCrypto.encBlob(blob.toString())));
        sp.edit().putString("tt_accs", out.toString()).apply();
        runOnUiThread(() -> { codeBox.setText(""); secretBox.setText(""); nameBox.setText(""); });
        return "اتضاف الحساب " + (disp.isEmpty() ? n : disp) + " ✓" + (refresh.isEmpty() ? " (توكن 24 ساعة بس — الأفضل Refresh token)" : "");
    }

    static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    static String extractCode(String in) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]code=([^&#\\s]+)").matcher(in);
            if (m.find()) return URLDecoder.decode(m.group(1), "UTF-8");
        } catch (Exception ignored) { }
        return in.contains("://") || in.contains("=") ? "" : in;
    }

    /** بيرجّع {كود, جسم}. POST بيبعت form-urlencoded. */
    static String[] http(String method, String url, String form, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        if (form != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream o = c.getOutputStream()) { o.write(form.getBytes("UTF-8")); }
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String ln;
                while ((ln = br.readLine()) != null) sb.append(ln);
            }
        }
        c.disconnect();
        return new String[]{String.valueOf(code), sb.toString()};
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        dropLegacy();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MainActivity.BG);
        root.setPadding(dp(16), dp(24), dp(16), dp(16));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView t = new TextView(this);
        t.setText("🎵 النشر على التك توك");
        t.setTextSize(20);
        t.setTextColor(MainActivity.TXT);
        t.setGravity(Gravity.CENTER);
        root.addView(t);

        TextView h = new TextView(this);
        h.setText("النشر من السيرفر");
        h.setTextColor(MainActivity.ACC2);
        h.setTextSize(13);
        h.setPadding(0, dp(12), 0, dp(4));
        root.addView(h);
        root.addView(box("انشر على كل حساباتي من السيرفر أول ما الريلز تخلص", "tt_server", false));
        root.addView(box("من غير تنزيل أي ملف على جهازي (يوفّر النت)", "tt_nodl", false));
        caption = new EditText(this);
        caption.setHint("وصف الفيديو (اختياري — بيتنشر مع كل فيديو)");
        caption.setHintTextColor(MainActivity.DIM);
        caption.setTextColor(MainActivity.TXT);
        caption.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        caption.setText(sp.getString("tt_caption", ""));
        root.addView(caption);


        TextView ha = new TextView(this);
        ha.setText("حسابات التك توك (الكوكيز/التوكن بيتشفّروا قبل الحفظ، والتطبيق نفسه مش بيقدر يقراهم تاني)");
        ha.setTextColor(MainActivity.ACC2);
        ha.setTextSize(13);
        ha.setPadding(0, dp(12), 0, dp(4));
        root.addView(ha);
        accList = new LinearLayout(this);
        accList.setOrientation(LinearLayout.VERTICAL);
        root.addView(accList);

        nameBox = field(root, "اسم للحساب (اختياري)", InputType.TYPE_CLASS_TEXT);

        Button lg = new Button(this);
        lg.setAllCaps(false);
        lg.setText("🍪 تسجيل الدخول عبر صفحة التك توك (كوكيز)");
        lg.setTextColor(Color.WHITE);
        lg.setBackgroundColor(MainActivity.ACC);
        lg.setOnClickListener(v -> openLogin());
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, dp(46));
        ll.topMargin = dp(6);
        root.addView(lg, ll);

        TextView or = new TextView(this);
        or.setText("— أو بالتوكن (API الرسمي) —");
        or.setTextColor(MainActivity.DIM);
        or.setGravity(Gravity.CENTER);
        or.setPadding(0, dp(10), 0, dp(2));
        root.addView(or);
        keyBox = field(root, "Client key (من تطبيقك في TikTok for Developers)", InputType.TYPE_CLASS_TEXT);
        keyBox.setText(sp.getString("tt_ckey", ""));
        secretBox = field(root, "Client secret (مش بيتحفظ في التطبيق)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        redirBox = field(root, "Redirect URI (نفس اللي سجّلته في التطبيق)", InputType.TYPE_TEXT_VARIATION_URI);
        redirBox.setText(sp.getString("tt_redir", ""));

        Button auth = new Button(this);
        auth.setAllCaps(false);
        auth.setText("🌐 افتح صفحة تفويض التك توك");
        auth.setTextColor(Color.WHITE);
        auth.setBackgroundColor(MainActivity.ACC2);
        auth.setOnClickListener(v -> openAuth());
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, dp(44));
        al.topMargin = dp(6);
        root.addView(auth, al);

        codeBox = field(root, "الصق الرابط اللي اتحوّلت له (أو الكود، أو Refresh token)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addBtn = new Button(this);
        addBtn.setAllCaps(false);
        addBtn.setText("➕ إضافة الحساب");
        addBtn.setTextColor(Color.WHITE);
        addBtn.setBackgroundColor(MainActivity.ACC);
        addBtn.setOnClickListener(v -> addFromToken());
        root.addView(addBtn, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView tip = new TextView(this);
        tip.setTextColor(MainActivity.DIM);
        tip.setTextSize(12);
        tip.setPadding(0, dp(12), 0, 0);
        tip.setText("إزاي تجيب التوكن (مرة واحدة للتطبيق، وبعدها ضغطتين لكل حساب):\n"
                + "1) من developers.tiktok.com اعمل تطبيق (Manage apps ← Connect an app).\n"
                + "2) ضيف المنتجين: Login Kit و Content Posting API (وفعّل Direct Post)، وفي Login Kit سجّل Redirect URI (أي رابط https تملكه، مش لازم يفتح صفحة شغّالة).\n"
                + "3) لو التطبيق في Sandbox ضيف حسابك في Target users.\n"
                + "4) هنا اكتب Client key و Client secret و Redirect URI، واضغط «افتح صفحة تفويض التك توك» ووافق بالحساب اللي عاوزه.\n"
                + "5) هتتحوّل لرابط الـ Redirect: انسخ الرابط كله من شريط العنوان والصقه في الخانة الأخيرة واضغط «إضافة». الكود بيعيش 30 دقيقة ولمرة واحدة.\n"
                + "6) لحساب تاني: سجّل خروج ودخول بالحساب التاني في المتصفح وكرر من 4.\n"
                + "السيرفر بيجدّد الـ Access token لوحده (الـ Refresh token بيعيش سنة).\n"
                + "مهم: التطبيق اللي لسه ما عدّاش مراجعة التك توك (Audit) بينشر بخصوصية «أنا بس» لحد ما يتوافق عليه.");
        root.addView(tip);

        TextView h2 = new TextView(this);
        h2.setText("بديل: النشر اليدوي عبر تطبيق التك توك");
        h2.setTextColor(MainActivity.ACC2);
        h2.setTextSize(13);
        h2.setPadding(0, dp(16), 0, dp(4));
        root.addView(h2);

        CheckBox on = box("جهّز الفيديوهات للنشر تلقائيًا بعد ما الريلز تخلص", "tt_on", false);
        CheckBox keep = box("احتفظ بنسخة zip في «التنزيلات» (لو قفلتها، الفيديوهات بتفضل جوه التطبيق بس)", "tt_keep", true);
        root.addView(on);
        root.addView(keep);

        info = new TextView(this);
        info.setTextColor(MainActivity.DIM);
        info.setTextSize(13);
        info.setPadding(0, dp(12), 0, dp(8));
        root.addView(info);

        next = new Button(this);
        next.setAllCaps(false);
        next.setTextColor(Color.WHITE);
        next.setBackgroundColor(MainActivity.ACC);
        next.setOnClickListener(v -> shareNext());
        root.addView(next, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView how = new TextView(this);
        how.setTextColor(MainActivity.DIM);
        how.setTextSize(12);
        how.setPadding(0, dp(12), 0, 0);
        how.setText("إزاي: اضغط الزر ← يفتح التك توك على الفيديو ← اختار الحساب اللي عاوزه (من داخل التك توك) واضغط نشر ← ارجع هنا والزر هيجيب الفيديو اللي بعده.\nالوصف بيتنسخ تلقائيًا، الصقه في خانة الوصف.");
        root.addView(how);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(MainActivity.BG);
        sv.addView(root);
        setContentView(sv);
        refreshAccs();
    }

    CheckBox box(String s, String key, boolean def) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(MainActivity.TXT);
        c.setChecked(sp.getBoolean(key, def));
        c.setOnCheckedChangeListener((v, on) -> sp.edit().putBoolean(key, on).apply());
        return c;
    }

    @Override protected void onResume() {
        super.onResume();
        // رجعنا من التك توك: اعتبر الفيديو اتنشر وشيله من الطابور
        if (pending != null) {
            pending.delete();
            pending = null;
        }
        refresh();
    }

    @Override protected void onPause() {
        super.onPause();
        sp.edit().putString("tt_caption", caption.getText().toString()).apply();
    }

    void refresh() {
        int n = TikTokQ.list(this).size();
        info.setText(n == 0 ? "مفيش فيديوهات في الطابور. فعّل الخيار الأول وشغّل ريلز جديدة." : "فيديوهات جاهزة للنشر: " + n);
        next.setEnabled(n > 0);
        next.setText(n > 0 ? "انشر الفيديو التالي (" + n + " متبقي)" : "لا يوجد فيديو");
    }

    void shareNext() {
        List<File> q = TikTokQ.list(this);
        if (q.isEmpty()) return;
        File f = q.get(0);
        String cap = caption.getText().toString().trim();
        if (!cap.isEmpty()) {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("caption", cap));
            Toast.makeText(this, "الوصف اتنسخ — الصقه في التك توك", Toast.LENGTH_LONG).show();
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("video/mp4");
        i.putExtra(Intent.EXTRA_STREAM, VideoProvider.uriFor(f));
        if (!cap.isEmpty()) i.putExtra(Intent.EXTRA_TEXT, cap);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri("video", VideoProvider.uriFor(f)));
        for (String p : PKGS) {
            try {
                getPackageManager().getPackageInfo(p, 0);
                i.setPackage(p);
                break;
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        pending = f;
        try {
            startActivity(i.getPackage() != null ? i : Intent.createChooser(i, "نشر على"));
        } catch (Exception e) {
            pending = null;
            Toast.makeText(this, "مقدرتش أفتح التك توك. اتأكد إنه متثبت.", Toast.LENGTH_LONG).show();
        }
    }
}
