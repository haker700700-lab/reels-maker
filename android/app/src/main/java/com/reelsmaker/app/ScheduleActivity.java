package com.reelsmaker.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * الجدولة والمتابعة: لكل حساب بيبيّن كام ريل متجدول، كام اتنشر، كام فشل، وآخر ميعاد في جدوله.
 * عشان لما تعمل رابط تاني تعرف الريلز الجديدة هتتجدول بعد إيه (بتكمّل تلقائي بعد آخر ميعاد لكل حساب).
 */
public class ScheduleActivity extends Activity {
    JSONObject data;
    LinearLayout body;
    TextView note;
    final SimpleDateFormat fmt = new SimpleDateFormat("d/M  HH:mm", Locale.US);

    int dp(int v) { return Ui.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(16));
        sv.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = Ui.text(this, "📅 الجدولة والمتابعة", 22, Ui.TXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        note = Ui.text(this, "السيرفر بيفحص كل 5 دقايق وينشر اللي معاده جه — حتى لو موبايلك مقفول.", 11.5f, Ui.DIM, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(2), 0, dp(8));
        root.addView(note);

        LinearLayout bar = new LinearLayout(this);
        Button refresh = Ui.pill(this, "🔄 تحديث", Ui.CARD);
        Button run = Ui.pill(this, "⚡ شغّل الناشر الآن", Ui.ACC);
        bar.addView(refresh, Ui.lp(this, 0, dp(42), 1, 3, 0, 3, 0));
        bar.addView(run, Ui.lp(this, 0, dp(42), 1, 3, 0, 3, 0));
        root.addView(bar);
        refresh.setOnClickListener(v -> reload());
        run.setOnClickListener(v -> runPublisher());

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body, Ui.lp(this, -1, -2, 0, 0, 8, 0, 0));
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    void say(String s) {
        runOnUiThread(() -> note.setText(s));
    }

    void reload() {
        if (Gh.TOK.isEmpty() || Gh.REPO.isEmpty()) {
            note.setText("التطبيق اتبنى من غير بيانات الخدمة.");
            return;
        }
        new Thread(() -> {
            try {
                final JSONObject d = Vault.load();
                runOnUiThread(() -> {
                    data = d;
                    render();
                });
            } catch (Throwable e) {
                say("❌ " + SocialActivity.msg(e));
            }
        }).start();
    }

    void mutate(final Vault.Mut m, final String ok) {
        new Thread(() -> {
            try {
                final JSONObject d = Vault.mutate(m);
                runOnUiThread(() -> {
                    data = d;
                    render();
                    note.setText(ok);
                });
            } catch (Throwable e) {
                say("❌ " + SocialActivity.msg(e));
            }
        }).start();
    }

    /** بيشغّل ناشر السيرفر فورًا (من غير ما تستنى الـ 5 دقايق). */
    void runPublisher() {
        say("⏳ بشغّل الناشر...");
        new Thread(() -> {
            try {
                String[] x = Gh.http("POST", Gh.API + "/repos/" + Gh.REPO + "/actions/workflows/publisher.yml/dispatches",
                        new JSONObject().put("ref", "main").toString());
                int code = Integer.parseInt(x[0]);
                if (code == 204) say("✅ الناشر اشتغل — النتيجة بتظهر هنا بعد دقيقة (اضغط تحديث).");
                else say("❌ مقدرتش أشغّل الناشر (" + code + "). اتأكد إن التطبيق اتحدّث وإن Actions شغّال في المستودع.");
            } catch (Throwable e) {
                say("❌ " + SocialActivity.msg(e));
            }
        }).start();
    }

    String when(long due) {
        long diff = due - System.currentTimeMillis() / 1000;
        String rel;
        if (diff <= 0) rel = "دلوقتي";
        else if (diff < 3600) rel = "بعد " + (diff / 60 + 1) + " د";
        else if (diff < 86400) rel = "بعد " + SocialActivity.fmt(Math.round(diff / 360.0) / 10.0) + " س";
        else rel = "بعد " + SocialActivity.fmt(Math.round(diff / 8640.0) / 10.0) + " يوم";
        return fmt.format(new Date(due * 1000L)) + " (" + rel + ")";
    }

    void render() {
        body.removeAllViews();
        if (data == null) return;
        JSONArray accs = data.optJSONArray("accounts");
        JSONArray q = data.optJSONArray("queue");
        if (accs == null || accs.length() == 0) {
            body.addView(Ui.text(this, "مفيش حسابات مربوطة لسه. اربط حساب من «الحسابات والنشر».", 13, Ui.DIM, false));
            return;
        }
        boolean auto = data.optJSONObject("settings") != null && data.optJSONObject("settings").optBoolean("auto_post", false);
        if (!auto) {
            TextView w = Ui.text(this, "⚠️ النشر التلقائي مقفول — الريلز الجديدة مش هتتجدول. فعّله من «الحسابات والنشر».", 12, Ui.WARN, false);
            w.setPadding(0, 0, 0, dp(8));
            body.addView(w);
        }
        for (int i = 0; i < accs.length(); i++) {
            JSONObject a = accs.optJSONObject(i);
            if (a == null) continue;
            final String id = a.optString("id");
            int pend = 0, done = 0, fail = 0;
            long last = 0;
            List<JSONObject> up = new ArrayList<>();
            List<String> errs = new ArrayList<>();
            if (q != null) {
                for (int j = 0; j < q.length(); j++) {
                    JSONObject it = q.optJSONObject(j);
                    if (it == null || !id.equals(it.optString("acc"))) continue;
                    String st = it.optString("state");
                    if ("pending".equals(st) || "publishing".equals(st)) {
                        pend++;
                        up.add(it);
                        last = Math.max(last, it.optLong("due"));
                    } else if ("done".equals(st)) {
                        done++;
                    } else {
                        fail++;
                        if (errs.size() < 2) errs.add(it.optString("err", ""));
                    }
                }
            }
            Collections.sort(up, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject x, JSONObject y) {
                    return Long.compare(x.optLong("due"), y.optLong("due"));
                }
            });
            String plat = a.optString("platform");
            String ic = "facebook".equals(plat) ? "📘" : "instagram".equals(plat) ? "📸" : "tiktok".equals(plat) ? "🎵" : "▶️";
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, ic + "  " + a.optString("name", "—") + (a.optBoolean("enabled", true) ? "" : "  (متوقف)"), 15, Ui.TXT, true));
            card.addView(Ui.text(this, "⏳ متجدول: " + pend + "   ✅ اتنشر: " + done + "   ❌ فشل: " + fail, 12.5f, Ui.TXT, false));
            if (pend > 0) {
                card.addView(Ui.text(this, "آخر ميعاد في الجدول: " + when(last), 12, Ui.ACC2, false));
                for (int k = 0; k < up.size() && k < 4; k++) {
                    JSONObject it = up.get(k);
                    String tag = "publishing".equals(it.optString("state")) ? "  ⏫ بينشر..." : "";
                    card.addView(Ui.text(this, "• ريل " + it.optInt("idx", 0) + "/" + it.optInt("of", 0) + " — " + when(it.optLong("due")) + tag, 11, Ui.DIM, false));
                }
                if (pend > 4) card.addView(Ui.text(this, "• ... و" + (pend - 4) + " كمان", 11, Ui.DIM, false));
                card.addView(Ui.text(this, "الريلز الجديدة من أي رابط تاني هتتجدول بعد " + when(last).split(" \\(")[0] + " تلقائيًا.", 10.5f, Ui.DIM, false));
            } else {
                card.addView(Ui.text(this, "مفيش حاجة متجدولة — الريلز الجاية هتتجدول من أول جدول الحساب.", 11, Ui.DIM, false));
            }
            for (String e : errs) {
                if (!e.isEmpty()) card.addView(Ui.text(this, "⚠️ " + e, 10.5f, Ui.BAD, false));
            }
            LinearLayout btns = new LinearLayout(this);
            if (pend > 0) {
                Button now = Ui.pill(this, "⚡ انشر دلوقتي", Ui.BG);
                Button cancel = Ui.pill(this, "🗑 ألغِ المجدول", Ui.BG);
                btns.addView(now, Ui.lp(this, 0, dp(36), 1, 2, 0, 2, 0));
                btns.addView(cancel, Ui.lp(this, 0, dp(36), 1, 2, 0, 2, 0));
                now.setOnClickListener(v -> mutate(d -> {
                    long t = System.currentTimeMillis() / 1000;
                    JSONArray qq = Vault.arr(d, "queue");
                    for (int j = 0; j < qq.length(); j++) {
                        JSONObject it = qq.getJSONObject(j);
                        if (id.equals(it.optString("acc")) && "pending".equals(it.optString("state"))) it.put("due", t - 1);
                    }
                }, "✅ هتتنشر على الدور — اضغط «شغّل الناشر الآن»."));
                cancel.setOnClickListener(v -> mutate(d -> dropPending(d, id), "🗑 اتلغى المجدول لحساب ده."));
            }
            if (fail > 0) {
                Button retry = Ui.pill(this, "↻ أعد الفاشل", Ui.BG);
                btns.addView(retry, Ui.lp(this, 0, dp(36), 1, 2, 0, 2, 0));
                retry.setOnClickListener(v -> mutate(d -> {
                    long t = System.currentTimeMillis() / 1000;
                    JSONArray qq = Vault.arr(d, "queue");
                    for (int j = 0; j < qq.length(); j++) {
                        JSONObject it = qq.getJSONObject(j);
                        if (id.equals(it.optString("acc")) && "failed".equals(it.optString("state"))) {
                            it.put("state", "pending").put("tries", 0).put("err", "").put("due", t);
                        }
                    }
                }, "↻ الفاشل رجع للجدول."));
            }
            if (btns.getChildCount() > 0) card.addView(btns, Ui.lp(this, -1, -2, 0, 0, 8, 0, 0));
            body.addView(card, Ui.lp(this, -1, -2, 0, 0, 8, 0, 0));
        }
    }

    static void dropPending(JSONObject d, String id) throws Exception {
        JSONArray qq = Vault.arr(d, "queue");
        JSONArray out = new JSONArray();
        for (int j = 0; j < qq.length(); j++) {
            JSONObject it = qq.getJSONObject(j);
            if (!(id.equals(it.optString("acc")) && "pending".equals(it.optString("state")))) out.put(it);
        }
        d.put("queue", out);
    }
}
