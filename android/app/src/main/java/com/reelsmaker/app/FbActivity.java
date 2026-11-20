package com.reelsmaker.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * صفحات الفيسبوك (توكن كل صفحة بيتشفّر قبل الحفظ، والتطبيق نفسه مش بيقدر يقراه تاني) + إعدادات النشر من السيرفر.
 * بتلزق توكن واحد (بتاع حسابك أو بتاع صفحة) والتطبيق بيجيب كل صفحاتك لوحده من Graph API الرسمي.
 */
public class FbActivity extends Activity {
    static final String GRAPH = "https://graph.facebook.com/v25.0";
    SharedPreferences sp;
    EditText caption, tokenBox;
    LinearLayout accList;
    Button addBtn;

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    JSONArray accounts() {
        try { return new JSONArray(sp.getString("fb_accs", "[]")); } catch (Exception e) { return new JSONArray(); }
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

    CheckBox box(String s, String key, boolean def) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(MainActivity.TXT);
        c.setChecked(sp.getBoolean(key, def));
        c.setOnCheckedChangeListener((v, on) -> sp.edit().putBoolean(key, on).apply());
        return c;
    }

    TextView head(String s, int top) {
        TextView h = new TextView(this);
        h.setText(s);
        h.setTextColor(MainActivity.ACC2);
        h.setTextSize(13);
        h.setPadding(0, dp(top), 0, dp(4));
        return h;
    }

    void refreshAccs() {
        accList.removeAllViews();
        JSONArray a = accounts();
        if (a.length() == 0) {
            TextView t = new TextView(this);
            t.setText("مفيش صفحات لسه.");
            t.setTextColor(MainActivity.DIM);
            accList.addView(t);
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            final String n = o.optString("n");
            LinearLayout r = new LinearLayout(this);
            TextView t = new TextView(this);
            t.setText("📘 " + o.optString("d", n));
            t.setTextColor(MainActivity.TXT);
            Button d = new Button(this);
            d.setText("حذف");
            d.setAllCaps(false);
            d.setTextColor(Color.WHITE);
            d.setBackgroundColor(0xFF3A2A4D);
            d.setOnClickListener(v -> {
                save(remove(accounts(), n));
                refreshAccs();
            });
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(d, new LinearLayout.LayoutParams(dp(80), dp(40)));
            accList.addView(r);
        }
    }

    JSONArray remove(JSONArray cur, String n) {
        JSONArray out = new JSONArray();
        for (int k = 0; k < cur.length(); k++) {
            JSONObject o = cur.optJSONObject(k);
            if (o != null && !o.optString("n").equals(n)) out.put(o);
        }
        return out;
    }

    void save(JSONArray a) { sp.edit().putString("fb_accs", a.toString()).apply(); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MainActivity.BG);
        root.setPadding(dp(16), dp(24), dp(16), dp(16));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView t = new TextView(this);
        t.setText("📘 النشر على فيسبوك");
        t.setTextSize(20);
        t.setTextColor(MainActivity.TXT);
        t.setGravity(Gravity.CENTER);
        root.addView(t);

        root.addView(head("النشر من السيرفر", 12));
        root.addView(box("انشر على كل صفحاتي من السيرفر أول ما الريلز تخلص", "fb_server", false));
        root.addView(box("من غير تنزيل أي ملف على جهازي (يوفّر النت)", "fb_nodl", false));
        caption = new EditText(this);
        caption.setHint("وصف الريل (اختياري — بيتنشر مع كل ريل)");
        caption.setHintTextColor(MainActivity.DIM);
        caption.setTextColor(MainActivity.TXT);
        caption.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        caption.setText(sp.getString("fb_caption", ""));
        root.addView(caption);

        root.addView(head("صفحات الفيسبوك (التوكن بيتشفّر قبل الحفظ، والتطبيق نفسه مش بيقدر يقراه تاني)", 12));
        accList = new LinearLayout(this);
        accList.setOrientation(LinearLayout.VERTICAL);
        root.addView(accList);

        tokenBox = field(root, "الصق التوكن هنا", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addBtn = new Button(this);
        addBtn.setAllCaps(false);
        addBtn.setText("➕ إضافة صفحاتي من التوكن");
        addBtn.setTextColor(Color.WHITE);
        addBtn.setBackgroundColor(MainActivity.ACC);
        addBtn.setOnClickListener(v -> addFromToken());
        root.addView(addBtn, new LinearLayout.LayoutParams(-1, dp(46)));

        Button open = new Button(this);
        open.setAllCaps(false);
        open.setText("🌐 افتح Graph API Explorer عشان تجيب التوكن");
        open.setTextColor(Color.WHITE);
        open.setBackgroundColor(MainActivity.ACC2);
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.facebook.com/tools/explorer/")));
            } catch (Exception e) {
                toast("مقدرتش أفتح المتصفح");
            }
        });
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1, dp(44));
        ol.topMargin = dp(6);
        root.addView(open, ol);

        TextView how = new TextView(this);
        how.setTextColor(MainActivity.DIM);
        how.setTextSize(12);
        how.setPadding(0, dp(12), 0, 0);
        how.setText("إزاي تجيب التوكن (مرة واحدة):\n"
                + "1) من developers.facebook.com اعمل تطبيق (نوع Business) وسيبه في وضع Development — كفاية لصفحاتك أنت.\n"
                + "2) افتح Graph API Explorer، اختار تطبيقك، واطلب الصلاحيات: pages_show_list و pages_manage_posts و pages_read_engagement.\n"
                + "3) اضغط Generate Access Token وسجّل دخول بحسابك (لازم تكون أدمن على الصفحات).\n"
                + "4) الصق التوكن فوق واضغط «إضافة». التطبيق هيجيب كل صفحاتك لوحده.\n"
                + "التوكن اللي بيتولّد بيبقى قصير العمر؛ لو عايز توكن الصفحات ما ينتهيش، حوّله لتوكن طويل من Access Token Debugger (Extend Access Token) قبل ما تلزقه.\n"
                + "حد فيسبوك: 30 ريل في اليوم لكل صفحة بالنشر عن طريق الـ API.");
        root.addView(how);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(MainActivity.BG);
        sv.addView(root);
        setContentView(sv);
        refreshAccs();
    }

    @Override protected void onPause() {
        super.onPause();
        sp.edit().putString("fb_caption", caption.getText().toString()).apply();
    }

    // ───────── إضافة الصفحات من التوكن ─────────
    void addFromToken() {
        final String tok = tokenBox.getText().toString().trim();
        if (tok.isEmpty()) { toast("الصق التوكن الأول"); return; }
        if (!TikCrypto.ready()) { toast("التطبيق ده اتبنى من غير مفتاح التشفير. حدّثه بالأمر الجديد."); return; }
        addBtn.setEnabled(false);
        addBtn.setText("بجيب الصفحات...");
        new Thread(() -> {
            String msg;
            try {
                msg = doAdd(tok);
            } catch (Exception e) {
                msg = "فشل الاتصال بفيسبوك. اتأكد من النت وجرّب تاني.";
            }
            final String m = msg;
            runOnUiThread(() -> {
                addBtn.setEnabled(true);
                addBtn.setText("➕ إضافة صفحاتي من التوكن");
                toast(m);
                refreshAccs();
            });
        }).start();
    }

    /** بيرجّع رسالة للمستخدم. التوكن مابيتخزنش غير مشفّر. */
    String doAdd(String tok) throws Exception {
        String[] r = get("/me/accounts?fields=id,name,access_token&limit=100", tok);
        JSONArray pages = new JSONArray();
        if ("200".equals(r[0])) {
            JSONArray d = new JSONObject(r[1]).optJSONArray("data");
            if (d != null) for (int i = 0; i < d.length(); i++) {
                JSONObject o = d.optJSONObject(i);
                if (o != null && !o.optString("access_token").isEmpty()) {
                    pages.put(new JSONObject().put("id", o.optString("id")).put("name", o.optString("name")).put("token", o.optString("access_token")));
                }
            }
        }
        if (pages.length() == 0) {
            // يمكن يكون توكن صفحة مباشرة: الصفحة بس هي اللي ليها حقل category
            String[] p = get("/me?fields=id,name,category", tok);
            if ("200".equals(p[0])) {
                JSONObject o = new JSONObject(p[1]);
                if (o.has("category") && !o.optString("id").isEmpty()) {
                    pages.put(new JSONObject().put("id", o.optString("id")).put("name", o.optString("name")).put("token", tok));
                }
            }
        }
        if (pages.length() == 0) {
            return "مالقيتش صفحات بالتوكن ده. اتأكد إنه ما انتهاش، وإن الصلاحيات (pages_show_list) متوافق عليها، وإنك أدمن على الصفحة. "
                    + "لو الصفحة تابعة لـ Business، استخدم توكن الصفحة نفسها أو أضف صلاحية business_management.";
        }
        JSONArray cur = accounts();
        for (int i = 0; i < pages.length(); i++) {
            JSONObject pg = pages.getJSONObject(i);
            String n = "p" + pg.getString("id").replaceAll("[^0-9]", "");
            JSONObject blob = new JSONObject().put("page_id", pg.getString("id")).put("token", pg.getString("token"));
            cur = remove(cur, n);
            cur.put(new JSONObject().put("n", n).put("d", pg.optString("name", n)).put("k", TikCrypto.encBlob(blob.toString())));
        }
        save(cur);
        runOnUiThread(() -> tokenBox.setText(""));
        return "اتضاف " + pages.length() + " صفحة ✓";
    }

    /** GET بسيط على Graph API؛ التوكن بيتبعت في الهيدر مش في الرابط. بيرجّع {كود, جسم}. */
    static String[] get(String path, String tok) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(GRAPH + path).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Authorization", "Bearer " + tok);
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                String ln;
                while ((ln = br.readLine()) != null) sb.append(ln);
            }
        }
        c.disconnect();
        return new String[]{String.valueOf(code), sb.toString()};
    }
}
