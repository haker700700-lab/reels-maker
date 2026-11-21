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

/**
 * 𝕏 النشر على X (تويتر سابقًا) من السيرفر عن طريق X API v2 الرسمي.
 * كل حساب بياخد ٤ مفاتيح من developer.x.com (OAuth 1.0a)، وبيتشفّروا قبل الحفظ (RSA + AES-GCM) —
 * التطبيق نفسه مش بيقدر يقراهم تاني، والسيرفر بس هو اللي بيفكّهم بالمفتاح الخاص (Secret TT_PRIV).
 */
public class XActivity extends Activity {
    SharedPreferences sp;
    EditText caption, nameBox, ck, cs, at, as;
    LinearLayout accList;
    Button addBtn;

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    JSONArray accounts() {
        try { return new JSONArray(sp.getString("x_accs", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    EditText field(LinearLayout p, String hint, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MainActivity.DIM);
        e.setTextColor(MainActivity.TXT);
        e.setSingleLine(true);
        e.setInputType(secret ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) : InputType.TYPE_CLASS_TEXT);
        p.addView(e);
        return e;
    }

    CheckBox box(String s, final String key, boolean def) {
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

    JSONArray remove(JSONArray cur, String n) {
        JSONArray out = new JSONArray();
        for (int k = 0; k < cur.length(); k++) {
            JSONObject o = cur.optJSONObject(k);
            if (o != null && !o.optString("n").equals(n)) out.put(o);
        }
        return out;
    }

    void save(JSONArray a) { sp.edit().putString("x_accs", a.toString()).apply(); }

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
            t.setText("𝕏 " + o.optString("d", n));
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

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MainActivity.BG);
        root.setPadding(dp(16), dp(24), dp(16), dp(16));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView t = new TextView(this);
        t.setText("𝕏 النشر على X (تويتر)");
        t.setTextSize(20);
        t.setTextColor(MainActivity.TXT);
        t.setGravity(Gravity.CENTER);
        root.addView(t);

        root.addView(head("النشر من السيرفر", 12));
        root.addView(box("انشر على كل حساباتي من السيرفر أول ما الريلز تخلص", "x_server", false));
        root.addView(box("من غير تنزيل أي ملف على جهازي (يوفّر النت)", "x_nodl", false));
        caption = new EditText(this);
        caption.setHint("نص التغريدة (اختياري — بيتنشر مع كل فيديو، لحد 280 حرف)");
        caption.setHintTextColor(MainActivity.DIM);
        caption.setTextColor(MainActivity.TXT);
        caption.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        caption.setText(sp.getString("x_caption", ""));
        root.addView(caption);

        root.addView(head("حسابات X (المفاتيح بتتشفّر قبل الحفظ، والتطبيق نفسه مش بيقدر يقراها تاني)", 12));
        accList = new LinearLayout(this);
        accList.setOrientation(LinearLayout.VERTICAL);
        root.addView(accList);

        nameBox = field(root, "اسم للحساب (للعرض بس، مثلًا @اسمك)", false);
        ck = field(root, "API Key (Consumer Key)", true);
        cs = field(root, "API Key Secret (Consumer Secret)", true);
        at = field(root, "Access Token", true);
        as = field(root, "Access Token Secret", true);
        addBtn = new Button(this);
        addBtn.setAllCaps(false);
        addBtn.setText("➕ إضافة الحساب");
        addBtn.setTextColor(Color.WHITE);
        addBtn.setBackgroundColor(MainActivity.ACC);
        addBtn.setOnClickListener(v -> addAccount());
        root.addView(addBtn, new LinearLayout.LayoutParams(-1, dp(46)));

        Button open = new Button(this);
        open.setAllCaps(false);
        open.setText("🌐 افتح developer.x.com عشان تجيب المفاتيح");
        open.setTextColor(Color.WHITE);
        open.setBackgroundColor(MainActivity.ACC2);
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.x.com/en/portal/dashboard")));
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
        how.setText("إزاي تجيب المفاتيح (مرة واحدة):\n"
                + "1) سجّل في developer.x.com واعمل Project وApp.\n"
                + "2) من App → Settings → User authentication settings: فعّلها واختار الصلاحية «Read and write» (مهم: لازم قبل ما تولّد التوكن).\n"
                + "3) من Keys and tokens: انسخ API Key وAPI Key Secret، وولّد Access Token وAccess Token Secret (لازم يظهر جنبهم Read and Write).\n"
                + "4) الصقهم فوق واضغط «إضافة الحساب». كل حساب ليه مفاتيحه الخاصة، وتقدر تضيف أكتر من حساب.\n"
                + "ملاحظة: حدود النشر ورفع الفيديو بتتحدد من باقة حسابك المطوّر عند X وبتتغيّر من وقت للتاني (الباقة المجانية محدودة جدًا) — "
                + "لو X رفض، السبب هيظهر لك في التقرير بعد الشغل. الفيديو لازم يكون أقل من 140 ثانية على الباقات العادية.");
        root.addView(how);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(MainActivity.BG);
        sv.addView(root);
        setContentView(sv);
        refreshAccs();
    }

    @Override protected void onPause() {
        super.onPause();
        sp.edit().putString("x_caption", caption.getText().toString()).apply();
    }

    void addAccount() {
        final String k1 = ck.getText().toString().trim(), k2 = cs.getText().toString().trim();
        final String k3 = at.getText().toString().trim(), k4 = as.getText().toString().trim();
        if (k1.isEmpty() || k2.isEmpty() || k3.isEmpty() || k4.isEmpty()) { toast("املأ المفاتيح الأربعة"); return; }
        if (!TikCrypto.ready()) { toast("التطبيق ده اتبنى من غير مفتاح التشفير. حدّثه بالأمر الجديد."); return; }
        try {
            // رقم الحساب من أول جزء من الـ Access Token (قبل الشرطة)
            String uid = k3.contains("-") ? k3.substring(0, k3.indexOf('-')) : k3.substring(0, Math.min(8, k3.length()));
            String n = "x" + uid.replaceAll("[^A-Za-z0-9]", "");
            String label = nameBox.getText().toString().trim();
            if (label.isEmpty()) label = "حساب X " + (accounts().length() + 1);
            JSONObject blob = new JSONObject().put("ck", k1).put("cs", k2).put("at", k3).put("as", k4);
            JSONArray cur = remove(accounts(), n);
            cur.put(new JSONObject().put("n", n).put("d", label).put("k", TikCrypto.encBlob(blob.toString())));
            save(cur);
            nameBox.setText("");
            ck.setText("");
            cs.setText("");
            at.setText("");
            as.setText("");
            refreshAccs();
            toast("اتضاف الحساب ✓ (السيرفر هيتأكد من المفاتيح أول ما تنشر)");
        } catch (Exception e) {
            toast("تعذّر تشفير المفاتيح: " + e.getClass().getSimpleName());
        }
    }
}
