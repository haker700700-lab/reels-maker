package com.reelsmaker.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * الحسابات والنشر: ربط فيسبوك (صفحات) + إنستجرام + تيك توك + يوتيوب، وإعدادات «إيقاف التحميل» و«النشر التلقائي».
 * كل بيانات الحسابات في خزنة مشفّرة خاصة بيك (Vault) — مش في الكود ولا في المستودع العام.
 */
public class SocialActivity extends Activity {
    static final int RC_META = 21, RC_TT = 22;

    SharedPreferences sp;
    JSONObject data;              // الخزنة المفكوكة (في الذاكرة بس)
    LinearLayout body;
    TextView note;
    CheckBox stopDl, autoPost;
    boolean syncing = false;
    String ttState = "";
    volatile boolean ytCancel = false;

    // منصة ← (أيقونة، اسم)
    static final String[] PLAT = {"facebook", "instagram", "tiktok", "youtube"};
    static final String[] ICON = {"📘", "📸", "🎵", "▶️"};
    static final String[] NAME = {"فيسبوك — الصفحات", "إنستجرام — ريلز", "تيك توك", "يوتيوب — Shorts"};

    int dp(int v) { return Ui.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(16));
        sv.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = Ui.text(this, "🔗 الحسابات والنشر", 22, Ui.TXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        note = Ui.text(this, "", 11.5f, Ui.DIM, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(2), 0, dp(8));
        root.addView(note);

        // الإعدادات العامة
        LinearLayout set = Ui.card(this);
        stopDl = Ui.check(this, "⏸ إيقاف تنزيل الريلز على الموبايل", sp.getBoolean("stop_dl", false));
        autoPost = Ui.check(this, "🚀 تشغيل النشر التلقائي عبر الحسابات", sp.getBoolean("auto_post", false));
        set.addView(stopDl);
        set.addView(autoPost);
        TextView hint = Ui.text(this, "تقدر تفعّل الاتنين مع بعض. لما النشر التلقائي شغّال: الريلز بتتجدول وتتنشر على السيرفر لوحدها، حتى لو الموبايل مقفول أو من غير نت.", 11, Ui.DIM, false);
        hint.setPadding(dp(4), dp(2), dp(4), 0);
        set.addView(hint);
        root.addView(set);
        stopDl.setOnCheckedChangeListener((v, on) -> {
            sp.edit().putBoolean("stop_dl", on).apply();
            if (!syncing) edit(d -> Vault.obj(d, "settings").put("stop_dl", on), null);
        });
        autoPost.setOnCheckedChangeListener((v, on) -> {
            sp.edit().putBoolean("auto_post", on).apply();
            if (!syncing) edit(d -> Vault.obj(d, "settings").put("auto_post", on), null);
        });

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body);

        Button sched = Ui.pill(this, "📅 الجدولة والمتابعة", Ui.ACC);
        sched.setTextSize(15);
        sched.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sched.setOnClickListener(v -> startActivity(new Intent(this, ScheduleActivity.class)));
        root.addView(sched, Ui.lp(this, -1, dp(48), 0, 0, 12, 0, 0));

        setContentView(sv);
        if (Gh.TOK.isEmpty() || Gh.REPO.isEmpty()) {
            note.setText("التطبيق اتبنى من غير بيانات الخدمة.");
            return;
        }
        reload();
    }

    // ───────── تحميل وحفظ الخزنة ─────────
    void say(String s) {
        runOnUiThread(() -> note.setText(s));
    }

    void reload() {
        say("⏳ بحمّل حساباتك...");
        new Thread(() -> {
            try {
                JSONObject d = Vault.load();
                runOnUiThread(() -> {
                    data = d;
                    note.setText("حساباتك محفوظة مشفّرة — محدش غيرك يقدر يفتحها.");
                    syncSettings();
                    render();
                });
            } catch (Throwable e) {
                say("❌ " + msg(e));
            }
        }).start();
    }

    static String msg(Throwable e) {
        String m = e.getMessage();
        return (m == null || m.isEmpty()) ? e.getClass().getSimpleName() : m;
    }

    void syncSettings() {
        JSONObject s = data == null ? null : data.optJSONObject("settings");
        if (s == null) return;
        syncing = true;
        stopDl.setChecked(s.optBoolean("stop_dl", stopDl.isChecked()));
        autoPost.setChecked(s.optBoolean("auto_post", autoPost.isChecked()));
        sp.edit().putBoolean("stop_dl", stopDl.isChecked()).putBoolean("auto_post", autoPost.isChecked()).apply();
        syncing = false;
    }

    /** تعديل على الخزنة في الخلفية، وبعدها إعادة رسم الشاشة. */
    void edit(Vault.Mut m, Runnable after) {
        new Thread(() -> {
            try {
                JSONObject d = Vault.mutate(m);
                runOnUiThread(() -> {
                    data = d;
                    render();
                    if (after != null) after.run();
                });
            } catch (Throwable e) {
                say("❌ " + msg(e));
            }
        }).start();
    }

    // ───────── العرض ─────────
    void render() {
        body.removeAllViews();
        if (data == null) return;
        JSONArray accs = data.optJSONArray("accounts");
        for (int p = 0; p < PLAT.length; p++) {
            LinearLayout card = Ui.card(this);
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(Ui.text(this, ICON[p] + "  " + NAME[p], 15, Ui.TXT, true), Ui.lp(0, -2, 1f));
            final int pi = p;
            Button keys = Ui.pill(this, "🔑", Ui.BG);
            keys.setOnClickListener(v -> keysDialog(keyName(pi), null));
            head.addView(keys, Ui.lp(this, dp(40), dp(34), 0, 0, 0, 6, 0));
            Button add = Ui.pill(this, "+ ربط", Ui.ACC);
            add.setOnClickListener(v -> connect(pi));
            head.addView(add, Ui.lp(this, -2, dp(34), 0, 0, 0, 0, 0));
            card.addView(head);
            if (p == 0) {
                card.addView(Ui.text(this, "الدخول بفيسبوك بيجيب الصفحات وحسابات إنستجرام المربوطة بيها. الملف الشخصي والجروبات مش مدعومين رسميًا.", 10.5f, Ui.DIM, false));
            } else if (p == 1) {
                card.addView(Ui.text(this, "لازم الحساب Business أو Creator ومربوط بصفحة فيسبوك. بيتربط من نفس زر فيسبوك.", 10.5f, Ui.DIM, false));
            }
            int n = 0;
            if (accs != null) {
                for (int i = 0; i < accs.length(); i++) {
                    JSONObject a = accs.optJSONObject(i);
                    if (a == null || !PLAT[p].equals(a.optString("platform"))) continue;
                    card.addView(row(a), Ui.lp(this, -1, -2, 0, 0, 6, 0, 0));
                    n++;
                }
            }
            if (n == 0) {
                TextView e = Ui.text(this, "مفيش حسابات مربوطة لسه.", 12, Ui.DIM, false);
                e.setPadding(0, dp(6), 0, 0);
                card.addView(e);
            }
            body.addView(card, Ui.lp(this, -1, -2, 0, 0, 8, 0, 0));
        }
        // أماكن جاهزة لمنصات تانية
        LinearLayout more = Ui.card(this);
        more.addView(Ui.text(this, "➕ منصات تانية (قريبًا)", 15, Ui.TXT, true));
        more.addView(Ui.text(this, "𝕏 إكس  •  👻 سناب شات  •  📌 بنترست  •  💼 لينكدإن — أماكنها جاهزة، وهتتفعّل أول ما تتاح لها واجهة نشر رسمية.", 11, Ui.DIM, false));
        body.addView(more, Ui.lp(this, -1, -2, 0, 0, 8, 0, 0));
    }

    String keyName(int p) {
        return p <= 1 ? "meta" : (p == 2 ? "tiktok" : "google");
    }

    View row(JSONObject a) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Ui.shape(this, Ui.BG, 10, 0, 0));
        r.setPadding(dp(10), dp(8), dp(6), dp(8));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, a.optString("name", "—"), 14, Ui.TXT, true));
        String d = a.optString("desc", "").trim();
        String sub = "أول ريل بعد " + fmt(a.optDouble("start_h", 0)) + " س • كل " + fmt(a.optDouble("step_h", 1)) + " س • "
                + (d.isEmpty() ? "من غير وصف" : "وصف ثابت ✓");
        TextView st = Ui.text(this, sub, 10.5f, Ui.DIM, false);
        col.addView(st);
        r.addView(col, Ui.lp(0, -2, 1f));
        CheckBox on = Ui.check(this, "", a.optBoolean("enabled", true));
        r.addView(on);
        final String id = a.optString("id");
        on.setOnCheckedChangeListener((v, c) -> edit(dd -> {
            JSONObject o = findAcc(dd, id);
            if (o != null) o.put("enabled", c);
        }, null));
        r.setOnClickListener(v -> accountDialog(a));
        return r;
    }

    static String fmt(double h) {
        if (h == Math.rint(h)) return String.valueOf((long) h);
        return String.valueOf(Math.round(h * 100) / 100.0);
    }

    static JSONObject findAcc(JSONObject d, String id) {
        JSONArray a = d.optJSONArray("accounts");
        if (a == null) return null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    // ───────── إعدادات حساب (وصف ثابت + جدول) ─────────
    void accountDialog(JSONObject a) {
        final String id = a.optString("id");
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(18), dp(8), dp(18), dp(4));
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        v.addView(Ui.text(this, "📝 وصف ثابت للفيديو (سيبه فاضي = ينشر من غير وصف)", 12, Ui.DIM, false));
        final EditText desc = Ui.edit(this, "مثال: شاهد المزيد في البايو #ريلز", a.optString("desc", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        desc.setMinLines(2);
        desc.setMaxLines(5);
        v.addView(desc);
        LinearLayout nr = new LinearLayout(this);
        LinearLayout c1 = new LinearLayout(this);
        c1.setOrientation(LinearLayout.VERTICAL);
        c1.addView(Ui.text(this, "أول ريل بعد (ساعات)", 11, Ui.DIM, false));
        final EditText st = Ui.edit(this, "0", fmt(a.optDouble("start_h", 0)), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        c1.addView(st);
        LinearLayout c2 = new LinearLayout(this);
        c2.setOrientation(LinearLayout.VERTICAL);
        c2.addView(Ui.text(this, "كل ريل بعد اللي قبله (ساعات)", 11, Ui.DIM, false));
        final EditText sp2 = Ui.edit(this, "1", fmt(a.optDouble("step_h", 1)), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        c2.addView(sp2);
        nr.addView(c1, Ui.lp(this, 0, -2, 1, 0, 0, 4, 0));
        nr.addView(c2, Ui.lp(this, 0, -2, 1, 4, 0, 0, 0));
        v.addView(nr, Ui.lp(this, -1, -2, 0, 0, 10, 0, 0));
        v.addView(Ui.text(this, "مثال: 0 و 1 ← أول ريل فورًا، والتاني بعد ساعة، والتالت بعد ساعتين... (تقدر تكتب 0.5 = نص ساعة)", 10.5f, Ui.DIM, false));

        Ui.dialog(this).setTitle(a.optString("name", "الحساب")).setView(v)
                .setPositiveButton("حفظ", (dg, w) -> {
                    final String ds = desc.getText().toString().trim();
                    final double s0 = num(st.getText().toString(), 0, 0, 24 * 60);
                    final double s1 = num(sp2.getText().toString(), 1, 0.05, 24 * 30);
                    edit(d -> {
                        JSONObject o = findAcc(d, id);
                        if (o != null) o.put("desc", ds).put("start_h", s0).put("step_h", s1);
                    }, null);
                })
                .setNeutralButton("🗑 حذف", (dg, w) -> Ui.dialog(this).setMessage("تحذف الحساب ده؟ (والمنشورات المجدولة عليه هتتلغي)")
                        .setPositiveButton("احذف", (d2, w2) -> edit(d -> {
                            JSONArray arr = Vault.arr(d, "accounts");
                            JSONArray out = new JSONArray();
                            for (int i = 0; i < arr.length(); i++) {
                                if (!id.equals(arr.getJSONObject(i).optString("id"))) out.put(arr.getJSONObject(i));
                            }
                            d.put("accounts", out);
                            JSONArray q = Vault.arr(d, "queue");
                            JSONArray q2 = new JSONArray();
                            for (int i = 0; i < q.length(); i++) {
                                JSONObject it = q.getJSONObject(i);
                                boolean mine = id.equals(it.optString("acc"));
                                if (!(mine && "pending".equals(it.optString("state")))) q2.put(it);
                            }
                            d.put("queue", q2);
                        }, null))
                        .setNegativeButton("لأ", null).show())
                .setNegativeButton("إلغاء", null).show();
    }

    static double num(String s, double d, double lo, double hi) {
        try {
            double v = Double.parseDouble(s.trim().replace(',', '.'));
            return Math.max(lo, Math.min(hi, v));
        } catch (Exception e) {
            return d;
        }
    }

    // ───────── مفاتيح المطوّر (مرة واحدة لكل منصة) ─────────
    void keysDialog(final String plat, final Runnable then) {
        if (data == null) { Toast.makeText(this, "استنى لحظة...", Toast.LENGTH_SHORT).show(); return; }
        String info, l1, l2, redirect;
        if ("meta".equals(plat)) {
            l1 = "App ID";
            l2 = "App Secret";
            redirect = Social.REDIR_META;
            info = "1) developers.facebook.com ← Create App (نوع Business).\n2) أضف Facebook Login، وفعّل «Client OAuth Login».\n3) في Valid OAuth Redirect URIs حط الرابط ده بالظبط:\n" + redirect
                    + "\n4) خلّي حسابك Admin أو Tester على التطبيق (عشان تنشر على صفحاتك من غير مراجعة).";
        } else if ("tiktok".equals(plat)) {
            l1 = "Client Key";
            l2 = "Client Secret";
            redirect = Social.redirGeneric();
            info = "1) developers.tiktok.com ← Manage apps ← أضف Login Kit و Content Posting API.\n2) في Redirect URI حط الرابط ده بالظبط:\n" + redirect
                    + "\n3) لحد ما تيك توك يراجع تطبيقك، المنشورات بتتنشر «أنا فقط» (خاصة).";
        } else {
            l1 = "Client ID";
            l2 = "Client Secret";
            redirect = "";
            info = "1) console.cloud.google.com ← فعّل YouTube Data API v3.\n2) Credentials ← OAuth client ID من نوع «TVs and Limited Input devices».\n3) في OAuth consent screen ضيف حسابك كـ Test user.";
        }
        JSONObject k;
        try {
            k = Vault.obj(Vault.obj(data, "keys"), plat);
        } catch (Exception e) {
            return;
        }
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(18), dp(8), dp(18), dp(4));
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView t = Ui.text(this, info, 11, Ui.DIM, false);
        t.setTextIsSelectable(true);
        v.addView(t);
        final EditText e1 = Ui.edit(this, l1, k.optString("id"), InputType.TYPE_CLASS_TEXT);
        final EditText e2 = Ui.edit(this, l2, k.optString("secret"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        v.addView(e1, Ui.lp(this, -1, -2, 0, 0, 8, 0, 0));
        v.addView(e2, Ui.lp(this, -1, -2, 0, 0, 6, 0, 0));
        v.addView(Ui.text(this, "🔒 المفاتيح دي بتتخزن مشفّرة في خزنتك بس.", 10.5f, Ui.DIM, false));
        AlertDialog.Builder bd = Ui.dialog(this).setTitle("🔑 مفاتيح الربط (مرة واحدة)").setView(v)
                .setPositiveButton("حفظ", (dg, w) -> {
                    final String a = e1.getText().toString().trim(), s = e2.getText().toString().trim();
                    edit(d -> Vault.obj(Vault.obj(d, "keys"), plat).put("id", a).put("secret", s),
                            () -> { if (then != null && !a.isEmpty() && !s.isEmpty()) then.run(); });
                })
                .setNegativeButton("إلغاء", null);
        if (!redirect.isEmpty()) {
            final String rd = redirect;
            bd.setNeutralButton("📋 نسخ رابط الرجوع", (dg, w) -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("redirect", rd));
                Toast.makeText(this, "اتنسخ ✓", Toast.LENGTH_SHORT).show();
            });
        }
        bd.show();
    }

    boolean haveKeys(String plat) {
        JSONObject k = data == null ? null : (data.optJSONObject("keys") == null ? null : data.optJSONObject("keys").optJSONObject(plat));
        return k != null && !k.optString("id").isEmpty() && !k.optString("secret").isEmpty();
    }

    JSONObject keys(String plat) {
        return data.optJSONObject("keys").optJSONObject(plat);
    }

    // ───────── الربط ─────────
    void connect(int p) {
        if (data == null) { Toast.makeText(this, "استنى لحظة...", Toast.LENGTH_SHORT).show(); return; }
        if (p <= 1) connectMeta();
        else if (p == 2) connectTikTok();
        else connectYouTube();
    }

    void connectMeta() {
        if (!haveKeys("meta")) { keysDialog("meta", this::connectMeta); return; }
        Intent i = new Intent(this, OAuthActivity.class);
        i.putExtra("url", Social.metaAuthUrl(keys("meta").optString("id")));
        i.putExtra("redirect", Social.REDIR_META);
        startActivityForResult(i, RC_META);
    }

    void connectTikTok() {
        if (!haveKeys("tiktok")) { keysDialog("tiktok", this::connectTikTok); return; }
        ttState = Vault.newId();
        Intent i = new Intent(this, OAuthActivity.class);
        i.putExtra("url", Social.tiktokAuthUrl(keys("tiktok").optString("id"), ttState));
        i.putExtra("redirect", Social.redirGeneric());
        startActivityForResult(i, RC_TT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent in) {
        super.onActivityResult(req, res, in);
        if (req != RC_META && req != RC_TT) return;
        if (res != RESULT_OK || in == null || in.getStringExtra("url") == null) {
            say("اتلغى الربط.");
            return;
        }
        String url = in.getStringExtra("url");
        Map<String, String> pr = Social.params(url);
        if (pr.containsKey("error") || pr.containsKey("error_code")) {
            String why = pr.containsKey("error_description") ? pr.get("error_description") : pr.get("error");
            say("❌ المنصة رفضت الدخول: " + why);
            return;
        }
        if (req == RC_META) finishMeta(pr.get("access_token"));
        else finishTikTok(pr.get("code"), pr.get("state"));
    }

    void finishMeta(final String shortTok) {
        if (shortTok == null || shortTok.isEmpty()) { say("❌ مرجعش توكن من فيسبوك. اتأكد من رابط الرجوع في إعدادات التطبيق."); return; }
        say("⏳ بجيب صفحاتك...");
        new Thread(() -> {
            try {
                JSONObject k = keys("meta");
                String lt = Social.metaLongLived(k.optString("id"), k.optString("secret"), shortTok);
                JSONArray pages = Social.metaPages(lt);
                final List<JSONObject> cands = new ArrayList<>();
                final List<String> labels = new ArrayList<>();
                for (int i = 0; i < pages.length(); i++) {
                    JSONObject pg = pages.getJSONObject(i);
                    String tok = pg.optString("access_token");
                    if (tok.isEmpty()) continue;
                    cands.add(new JSONObject().put("platform", "facebook").put("ext_id", pg.getString("id"))
                            .put("name", pg.optString("name", "صفحة")).put("token", tok));
                    labels.add("📘 " + pg.optString("name", "صفحة"));
                    JSONObject ig = pg.optJSONObject("instagram_business_account");
                    if (ig != null && !ig.optString("id").isEmpty()) {
                        String un = ig.optString("username", "");
                        cands.add(new JSONObject().put("platform", "instagram").put("ext_id", ig.getString("id"))
                                .put("name", un.isEmpty() ? "إنستجرام" : "@" + un).put("token", tok));
                        labels.add("📸 " + (un.isEmpty() ? "إنستجرام" : "@" + un) + "  (من صفحة " + pg.optString("name", "") + ")");
                    }
                }
                runOnUiThread(() -> pickAccounts(cands, labels));
            } catch (Throwable e) {
                say("❌ " + msg(e));
            }
        }).start();
    }

    /** نافذة التأكيد: تختار الصفحات/الحسابات اللي هتنشر عليها (واحدة أو أكتر). */
    void pickAccounts(final List<JSONObject> cands, List<String> labels) {
        if (cands.isEmpty()) {
            note.setText("مفيش صفحات.");
            Ui.dialog(this).setTitle("✅ دخلت بنجاح — بس مفيش صفحات")
                    .setMessage("لازم تكون Admin على صفحة فيسبوك، وتختارها في نافذة فيسبوك وقت الدخول. جرّب تاني واضغط «تعديل» وفعّل الصفحات.")
                    .setPositiveButton("تمام", null).show();
            return;
        }
        final boolean[] on = new boolean[cands.size()];
        for (int i = 0; i < on.length; i++) on[i] = true;
        Ui.dialog(this).setTitle("✅ تم الدخول — اختار فين تنشر")
                .setMultiChoiceItems(labels.toArray(new String[0]), on, (dg, w, c) -> on[w] = c)
                .setPositiveButton("ربط المحدد", (dg, w) -> {
                    final List<JSONObject> chosen = new ArrayList<>();
                    for (int i = 0; i < on.length; i++) if (on[i]) chosen.add(cands.get(i));
                    if (chosen.isEmpty()) return;
                    edit(d -> {
                        for (JSONObject c : chosen) Vault.upsert(d, c);
                    }, () -> note.setText("✅ اتربط " + chosen.size() + " حساب."));
                })
                .setNegativeButton("إلغاء", null).show();
    }

    void finishTikTok(final String code, String state) {
        if (code == null || code.isEmpty() || !ttState.equals(state)) { say("❌ رد تيك توك مش مظبوط. جرّب تاني."); return; }
        say("⏳ بكمّل الربط مع تيك توك...");
        new Thread(() -> {
            try {
                JSONObject k = keys("tiktok");
                JSONObject t = Social.tiktokExchange(k.optString("id"), k.optString("secret"), code);
                String acc = t.getString("access_token");
                String name = Social.tiktokName(acc);
                JSONObject a = new JSONObject().put("platform", "tiktok").put("ext_id", t.optString("open_id", Vault.newId()))
                        .put("name", name).put("token", acc).put("refresh", t.optString("refresh_token", ""))
                        .put("expires", System.currentTimeMillis() / 1000 + t.optLong("expires_in", 86400));
                saveOne(a, name);
            } catch (Throwable e) {
                say("❌ " + msg(e));
            }
        }).start();
    }

    /** بيحفظ حساب واحد وبيطلّع نافذة تأكيد صغيرة «تم الدخول». */
    void saveOne(final JSONObject a, final String name) {
        edit(d -> Vault.upsert(d, a), () -> {
            note.setText("✅ اتربط: " + name);
            Ui.dialog(this).setTitle("✅ تم تأكيد الدخول").setMessage(name + "\nالحساب اتربط وجاهز للنشر.")
                    .setPositiveButton("تمام", null).show();
        });
    }

    // ───────── يوتيوب ─────────
    void connectYouTube() {
        if (!haveKeys("google")) { keysDialog("google", this::connectYouTube); return; }
        say("⏳ بطلب كود الدخول من جوجل...");
        ytCancel = false;
        new Thread(() -> {
            try {
                JSONObject k = keys("google");
                final String cid = k.optString("id"), sec = k.optString("secret");
                final JSONObject dv = Social.googleDeviceStart(cid);
                final String code = dv.getString("user_code"), dev = dv.getString("device_code");
                final String url = dv.optString("verification_url", "https://www.google.com/device");
                final int every = Math.max(3, dv.optInt("interval", 5));
                final long until = System.currentTimeMillis() + dv.optLong("expires_in", 600) * 1000L;
                runOnUiThread(() -> {
                    note.setText("استنى تأكيدك على صفحة جوجل...");
                    final AlertDialog dg = Ui.dialog(this).setCancelable(false).setTitle("▶️ ربط يوتيوب")
                            .setMessage("1) اضغط «افتح الصفحة»\n2) اكتب الكود ده وسجّل دخول بحساب القناة:\n\n        " + code + "\n\n(الكود اتنسخ. الصفحة دي هتتقفل لوحدها لما تأكد.)")
                            .setPositiveButton("افتح الصفحة", null)
                            .setNegativeButton("إلغاء", (d, w) -> ytCancel = true).create();
                    dg.show();
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("code", code));
                    dg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        } catch (Throwable ignored) { }
                    });
                    pollGoogle(dg, cid, sec, dev, every, until);
                });
            } catch (Throwable e) {
                say("❌ " + msg(e));
            }
        }).start();
    }

    void pollGoogle(final AlertDialog dg, final String cid, final String sec, final String dev, final int every, final long until) {
        new Thread(() -> {
            int wait = every;
            try {
                while (!ytCancel && System.currentTimeMillis() < until) {
                    Thread.sleep(wait * 1000L);
                    JSONObject r;
                    try {
                        r = Social.googlePoll(cid, sec, dev);
                    } catch (java.io.IOException e) {
                        continue;   // النت فصل لحظة، نكمّل
                    }
                    if (r.has("access_token")) {
                        String at = r.getString("access_token");
                        String[] ch = Social.googleChannel(at);
                        JSONObject a = new JSONObject().put("platform", "youtube").put("ext_id", ch[0]).put("name", ch[1])
                                .put("token", at).put("refresh", r.optString("refresh_token", ""))
                                .put("expires", System.currentTimeMillis() / 1000 + r.optLong("expires_in", 3600));
                        runOnUiThread(dg::dismiss);
                        saveOne(a, ch[1]);
                        return;
                    }
                    String er = r.optString("error", "");
                    if ("slow_down".equals(er)) wait += 5;
                    else if (!"authorization_pending".equals(er)) throw new Gh.Fatal("جوجل: " + r.optString("error_description", er));
                }
                runOnUiThread(dg::dismiss);
                say(ytCancel ? "اتلغى الربط." : "⌛ الكود انتهى. جرّب تاني.");
            } catch (InterruptedException e) {
                runOnUiThread(dg::dismiss);
            } catch (Throwable e) {
                runOnUiThread(dg::dismiss);
                say("❌ " + msg(e));
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        ytCancel = true;
        super.onDestroy();
    }
}
