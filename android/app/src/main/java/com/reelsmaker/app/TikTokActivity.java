package com.reelsmaker.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
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

import java.io.File;
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
    EditText nameBox;

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 31 || res != RESULT_OK || data == null) return;
        String ck = data.getStringExtra("cookies");
        String n = nameBox.getText().toString().trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (ck == null || ck.isEmpty()) return;
        if (n.isEmpty()) n = "acc" + (accounts().length() + 1);
        try {
            JSONArray a = accounts(), out = new JSONArray();
            for (int i = 0; i < a.length(); i++) if (!a.getJSONObject(i).getString("n").equals(n)) out.put(a.getJSONObject(i));
            out.put(new JSONObject().put("n", n).put("k", TikCrypto.encBlob(new JSONObject().put("cookies", ck).toString())));
            sp.edit().putString("tt_accs", out.toString()).apply();
            nameBox.setText("");
            toast("اتضاف الحساب " + n + " ✓");
            refreshAccs();
        } catch (Exception e) {
            toast("فشل حفظ الحساب");
        }
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
            t.setText("👤 " + n);
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

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
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
        ha.setText("حسابات التك توك (كلمة السر بتتشفّر قبل الحفظ، والتطبيق نفسه مش بيقدر يقراها تاني)");
        ha.setTextColor(MainActivity.ACC2);
        ha.setTextSize(13);
        ha.setPadding(0, dp(12), 0, dp(4));
        root.addView(ha);
        accList = new LinearLayout(this);
        accList.setOrientation(LinearLayout.VERTICAL);
        root.addView(accList);
        EditText an = field(root, "اسم للحساب (أي اسم)", InputType.TYPE_CLASS_TEXT);
        nameBox = an;
        Button web = new Button(this);
        web.setAllCaps(false);
        web.setText("🌐 تسجيل الدخول عبر صفحة التك توك (الأفضل)");
        web.setTextColor(Color.WHITE);
        web.setBackgroundColor(MainActivity.ACC);
        web.setOnClickListener(v -> {
            if (!TikCrypto.ready()) { toast("التطبيق ده اتبنى من غير مفتاح التشفير. حدّثه بالأمر الجديد."); return; }
            if (an.getText().toString().trim().isEmpty()) { toast("اكتب اسم للحساب الأول"); return; }
            startActivityForResult(new Intent(this, TikLoginActivity.class), 31);
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, dp(46)));
        TextView or = new TextView(this);
        or.setText("أو بيانات الدخول (ممكن التك توك يطلب تحقق من السيرفر):");
        or.setTextColor(MainActivity.DIM);
        or.setTextSize(12);
        or.setPadding(0, dp(8), 0, 0);
        root.addView(or);
        EditText au = field(root, "اسم المستخدم أو الإيميل", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText ap = field(root, "كلمة السر", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button add = new Button(this);
        add.setAllCaps(false);
        add.setText("➕ إضافة الحساب");
        add.setTextColor(Color.WHITE);
        add.setBackgroundColor(MainActivity.ACC2);
        add.setOnClickListener(v -> {
            String n = an.getText().toString().trim().replaceAll("[^A-Za-z0-9_-]", "");
            String u = au.getText().toString().trim(), p = ap.getText().toString();
            if (n.isEmpty()) n = "acc" + (accounts().length() + 1);
            if (u.isEmpty() || p.isEmpty()) { toast("اكتب اسم المستخدم وكلمة السر"); return; }
            if (!TikCrypto.ready()) { toast("التطبيق ده اتبنى من غير مفتاح التشفير. حدّثه بالأمر الجديد."); return; }
            try {
                JSONArray a = accounts();
                JSONArray out = new JSONArray();
                for (int i = 0; i < a.length(); i++) if (!a.getJSONObject(i).getString("n").equals(n)) out.put(a.getJSONObject(i));
                out.put(new JSONObject().put("n", n).put("e", TikCrypto.enc(u, p)));
                sp.edit().putString("tt_accs", out.toString()).apply();
                an.setText(""); au.setText(""); ap.setText("");
                toast("اتضاف الحساب " + n);
                refreshAccs();
            } catch (IllegalArgumentException e) {
                toast("البيانات طويلة أوي");
            } catch (Exception e) {
                toast("فشل التشفير");
            }
        });
        root.addView(add, new LinearLayout.LayoutParams(-1, dp(44)));

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
        if (getIntent().getBooleanExtra("share_next_now", false)) { getIntent().removeExtra("share_next_now"); next.postDelayed(this::shareNext, 350); }
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
