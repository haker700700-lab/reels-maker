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
 * حسابات إنستجرام (توكن كل حساب بيتشفّر قبل الحفظ، والتطبيق نفسه مش بيقدر يقراه تاني) + إعدادات النشر من السيرفر.
 * بتلزق توكن واحد (بتاع حسابك أو بتاع صفحة) والتطبيق بيجيب كل صفحاتك لوحده من Graph API الرسمي.
 */
public class IgActivity extends Activity {
    static final String GRAPH = "https://graph.facebook.com/v25.0";
    SharedPreferences sp;
    EditText caption, tokenBox;
    LinearLayout accList;
    Button addBtn;

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    JSONArray accounts() {
        try { return new JSONArray(sp.getString("ig_accs", "[]")); } catch (Exception e) { return new JSONArray(); }
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
            t.setText("مفيش حسابات لسه.");
            t.setTextColor(MainActivity.DIM);
            accList.addView(t);
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            final String n = o.optString("n");
            LinearLayout r = new LinearLayout(this);
            TextView t = new TextView(this);
            t.setText("📷 " + o.optString("d", n));
            t.setTextColor(MainActivity.TXT);
            Button d = new Button(this);
            d.setText("حذف");
            d.setAllCaps(false);
            d.setTextColor(Color.WHITE);
            d.setBackgroundColor(Ui.DANGER);
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

    void save(JSONArray a) { sp.edit().putString("ig_accs", a.toString()).apply(); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MainActivity.BG);
        root.setPadding(dp(16), dp(24), dp(16), dp(16));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView t = new TextView(this);
        t.setText("📷 النشر على إنستجرام");
        t.setTextSize(20);
        t.setTextColor(MainActivity.TXT);
        t.setGravity(Gravity.CENTER);
        root.addView(t);

        root.addView(head("النشر من السيرفر", 12));
        root.addView(box("انشر على كل حسابات إنستجرام من السيرفر أول ما الريلز تخلص", "ig_server", false));
        root.addView(box("من غير تنزيل أي ملف على جهازي (يوفّر النت)", "ig_nodl", false));
        caption = new EditText(this);
        caption.setHint("وصف الريل + هاشتاجات (اختياري — بيتنشر مع كل ريل)");
        caption.setHintTextColor(MainActivity.DIM);
        caption.setTextColor(MainActivity.TXT);
        caption.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        caption.setText(sp.getString("ig_caption", ""));
        root.addView(caption);

        root.addView(head("حسابات إنستجرام (التوكن بيتشفّر قبل الحفظ، والتطبيق نفسه مش بيقدر يقراه تاني)", 12));
        accList = new LinearLayout(this);
        accList.setOrientation(LinearLayout.VERTICAL);
        root.addView(accList);

        tokenBox = field(root, "الصق التوكن هنا", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addBtn = new Button(this);
        addBtn.setAllCaps(false);
        addBtn.setText("➕ إضافة حساباتي من التوكن");
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
        how.setText("إزاي تربط حساب إنستجرام (مرة واحدة):\n"
                + "1) الحساب لازم يكون Business أو Creator (من إنستجرام: الإعدادات ← نوع الحساب ← التحويل لحساب احترافي).\n"
                + "2) اربطه بصفحة فيسبوك أنت أدمن عليها (إنستجرام: تعديل الملف الشخصي ← الصفحة).\n"
                + "3) من developers.facebook.com اعمل تطبيق (نوع Business) وسيبه Development — كفاية لحساباتك أنت.\n"
                + "4) افتح Graph API Explorer، اختار تطبيقك، واطلب الصلاحيات: instagram_basic و instagram_content_publish و pages_show_list و pages_read_engagement.\n"
                + "5) اضغط Generate Access Token وسجّل دخول بحساب فيسبوك اللي أدمن على الصفحات، واختار كل الصفحات/الحسابات اللي عايزها.\n"
                + "6) الصق التوكن فوق واضغط «إضافة»: التطبيق هيجيب كل حسابات إنستجرام المربوطة لوحده.\n\n"
                + "إزاي تضيف أكتر من حساب:\n"
                + "• لو الحسابات كلها على نفس حساب فيسبوك: توكن واحد بيضيفهم كلهم مرة واحدة (اختار كل الصفحات في خطوة 5).\n"
                + "• لو حساب فيسبوك تاني: ولّد توكن بيه والصقه وضغط «إضافة» تاني، وبيتضاف فوق اللي موجود.\n"
                + "• لتحديث حساب (توكن اتغيّر): أضفه تاني وبيستبدل القديم. تقدر تحذف أي حساب بزر «حذف».\n\n"
                + "ملاحظات: نفس توكن فيسبوك بيشتغل هنا لو فيه الصلاحيات دي. التوكن القصير بيعيش ساعات؛ لو عايز يعيش 60 يوم حوّله لطويل من Access Token Debugger (Extend Access Token) قبل ما تلزقه "
                + "— توكن الصفحة اللي بيطلع منه بيبقى بدون تاريخ انتهاء.\n"
                + "الحد: 100 منشور بالـ API لكل حساب في 24 ساعة. الفيديو بيترفع من السيرفر مباشرة (الريلز 9:16، مناسبة لإنستجرام).");
        root.addView(how);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(MainActivity.BG);
        sv.addView(root);
        setContentView(sv);
        refreshAccs();
    }

    @Override protected void onPause() {
        super.onPause();
        sp.edit().putString("ig_caption", caption.getText().toString()).apply();
    }

    // ───────── إضافة الصفحات من التوكن ─────────
    void addFromToken() {
        final String tok = tokenBox.getText().toString().trim();
        if (tok.isEmpty()) { toast("الصق التوكن الأول"); return; }
        if (!TikCrypto.ready()) { toast("التطبيق ده اتبنى من غير مفتاح التشفير. حدّثه بالأمر الجديد."); return; }
        addBtn.setEnabled(false);
        addBtn.setText("بجيب الحسابات...");
        new Thread(() -> {
            String msg;
            try {
                msg = doAdd(tok);
            } catch (Exception e) {
                msg = "فشل الاتصال بفيسبوك/إنستجرام. اتأكد من النت وجرّب تاني.";
            }
            final String m = msg;
            runOnUiThread(() -> {
                addBtn.setEnabled(true);
                addBtn.setText("➕ إضافة حساباتي من التوكن");
                toast(m);
                refreshAccs();
            });
        }).start();
    }

    /** بيرجّع رسالة للمستخدم. التوكن مابيتخزنش غير مشفّر. بيجيب كل حسابات إنستجرام (Business/Creator) المربوطة بصفحاتك. */
    String doAdd(String tok) throws Exception {
        JSONArray found = new JSONArray();
        String[] r = get("/me/accounts?fields=id,name,access_token,instagram_business_account%7Bid,username%7D&limit=100", tok);
        String errMsg = "";
        if ("200".equals(r[0])) {
            JSONArray d = new JSONObject(r[1]).optJSONArray("data");
            if (d != null) for (int i = 0; i < d.length(); i++) {
                JSONObject o = d.optJSONObject(i);
                if (o == null) continue;
                JSONObject ig = o.optJSONObject("instagram_business_account");
                if (ig != null && !ig.optString("id").isEmpty() && !o.optString("access_token").isEmpty()) {
                    found.put(new JSONObject().put("id", ig.optString("id")).put("user", ig.optString("username"))
                            .put("token", o.optString("access_token")));
                }
            }
        } else {
            errMsg = apiError(r[1]);
        }
        if (found.length() == 0) {
            // يمكن يكون توكن صفحة مباشرة
            String[] p = get("/me?fields=id,name,instagram_business_account%7Bid,username%7D", tok);
            if ("200".equals(p[0])) {
                JSONObject ig = new JSONObject(p[1]).optJSONObject("instagram_business_account");
                if (ig != null && !ig.optString("id").isEmpty()) {
                    found.put(new JSONObject().put("id", ig.optString("id")).put("user", ig.optString("username")).put("token", tok));
                }
            }
        }
        if (found.length() == 0) {
            return "مالقيتش حسابات إنستجرام بالتوكن ده" + (errMsg.isEmpty() ? "" : " (" + errMsg + ")") + ". اتأكد إن: الحساب Business أو Creator، ومربوط بصفحة فيسبوك، "
                    + "والتوكن فيه صلاحيات instagram_basic و instagram_content_publish و pages_show_list و pages_read_engagement، وإنك أدمن على الصفحة.";
        }
        JSONArray cur = accounts();
        for (int i = 0; i < found.length(); i++) {
            JSONObject a = found.getJSONObject(i);
            String n = "i" + a.getString("id").replaceAll("[^0-9]", "");
            String disp = a.optString("user").isEmpty() ? n : "@" + a.optString("user");
            JSONObject blob = new JSONObject().put("ig_id", a.getString("id")).put("token", a.getString("token"));
            cur = remove(cur, n);
            cur.put(new JSONObject().put("n", n).put("d", disp).put("k", TikCrypto.encBlob(blob.toString())));
        }
        save(cur);
        runOnUiThread(() -> tokenBox.setText(""));
        return "اتضاف " + found.length() + " حساب إنستجرام ✓ (الإجمالي " + cur.length() + ")";
    }

    static String apiError(String body) {
        try {
            JSONObject e = new JSONObject(body).optJSONObject("error");
            return e == null ? "" : e.optString("message", "").replaceAll("\\s+", " ");
        } catch (Exception x) {
            return "";
        }
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
