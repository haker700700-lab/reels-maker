package com.reelsmaker.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

/**
 * 🎛️ الاستوديو (v5): الوضع الجاهز • الفلاتر • اللوجو • شكل ومكان النص • الحركة • الصوت • سجل الحقوق • فيديو تجريبي.
 * كل قسم فيه شرح صغير + «مكانه» على الشاشة، وفوق معاينة (٩:١٦) بتوضّح مكان اللوجو والنص وشريط التقدّم لحظيًا.
 * كل ضغطة بتتحفظ فورًا.
 */
public class StudioActivity extends Activity {
    static final int REQ_LOGO = 31;

    static final String[] MODE_ID = {"best", "pro", "custom"};
    static final String[] MODE_NAME = {"🏆 الأفضل (الافتراضي)", "🔥 احترافي Pro", "🛠️ مخصّص (أنا أختار)"};

    static final String[] LOOK_ID = {"none", "golden", "sunset", "cinematic", "clarendon", "juno", "lark", "valencia",
            "vintage", "noir", "moon", "vivid", "matte", "cool", "drama", "glow"};
    static final String[] LOOK_NAME = {"بدون فلتر", "ذهبي ✨", "غروب", "سينمائي", "كلارندون", "جونو", "لارك", "فالنسيا",
            "فينتاج", "أبيض وأسود", "مون", "زاهي", "مات", "بارد", "دراما", "وهج ناعم"};
    static final int[] LOOK_TINT = {0, 0x66FFB300, 0x66FF7043, 0x5500A3B5, 0x33FFFFFF, 0x55FF8A00, 0x4480DEEA, 0x55FFAB40,
            0x66A1887F, 0x99555555, 0x77666E7A, 0x44FF00AA, 0x44888888, 0x552196F3, 0x77000000, 0x33FFFFFF};

    static final String[] EX_ID = {"vignette", "grain", "sharp", "clarity", "stabilize", "denoise_v"};
    static final String[] EX_NAME = {"تعتيم الأطراف", "حبيبات فيلم", "حدّة (Sharpen)", "وضوح (Clarity)", "تثبيت خفيف", "تنظيف الصورة"};

    static final String[] POS_ID = {"tl", "tr", "bl", "br", "c"};
    static final String[] POS_NAME = {"أعلى يسار", "أعلى يمين", "أسفل يسار", "أسفل يمين", "المنتصف"};

    static final String[] TPOS = {"أعلى", "وسط", "أسفل"};
    static final String[] TSTYLE = {"صندوق داكن شفاف", "صندوق أبيض", "صندوق ذهبي", "بدون صندوق (حدود وظل)", "ساده (نص بس)",
            "صندوق أسود", "صندوق بنفسجي", "نيون سماوي", "أصفر بحدود"};
    static final String[] TSIZE = {"صغير", "متوسط", "كبير", "ضخم"};
    static final String[] TFONT = {"Tajawal", "Almarai", "Lalezar", "Amiri"};
    static final String[] TCOL_ID = {"", "FFFFFF", "FACC15", "2DD4BF", "F43F5E", "60A5FA"};
    static final String[] TCOL_NAME = {"حسب النمط", "أبيض", "ذهبي", "سماوي", "أحمر", "أزرق"};
    static final String[] ANIM_ID = {"none", "fade", "slide", "pop"};
    static final String[] ANIM_NAME = {"ثابت", "ظهور ناعم", "ينزلق من تحت", "نبضة"};

    SharedPreferences sp;
    Preview preview;
    TextView audioNow;
    Button logoUp;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    String sv(String k) { return Feat.str(sp, k, Feat.advDef(k)); }

    boolean bv(String k) { return Feat.bool(sp, k, Feat.advBoolDef(k)); }

    // ───────── عناصر واجهة صغيرة ─────────
    TextView head(String s) {
        TextView h = new TextView(this);
        h.setText(s);
        h.setTextColor(MainActivity.ACC2);
        h.setTextSize(15);
        h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        h.setPadding(0, dp(16), 0, dp(2));
        return h;
    }

    TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MainActivity.DIM);
        t.setTextSize(12);
        t.setPadding(0, 0, 0, dp(4));
        return t;
    }

    /** قسم: عنوان + شرح + «مكانه». */
    void section(LinearLayout p, String title, String explain, String where) {
        p.addView(head(title));
        p.addView(note(explain + "\n📍 مكانه: " + where));
    }

    /** زرار بيلفّ على الاختيارات كل ما تضغط عليه. */
    Button cycle(final String key, final String[] ids, final String[] names, final String prefix) {
        final Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setBackgroundColor(MainActivity.CARD);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        final int[] idx = {0};
        String cur = sv(key);
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(cur)) idx[0] = i;
        b.setText(prefix + names[idx[0]]);
        b.setOnClickListener(v -> {
            idx[0] = (idx[0] + 1) % ids.length;
            sp.edit().putString(key, ids[idx[0]]).apply();
            b.setText(prefix + names[idx[0]]);
            refresh();
        });
        return b;
    }

    void addRow(LinearLayout p, View... views) {
        LinearLayout r = new LinearLayout(this);
        for (View v : views) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            r.addView(v, lp);
        }
        p.addView(r);
    }

    CheckBox box(String s, final String key) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextColor(MainActivity.TXT);
        c.setTextSize(13);
        c.setChecked(bv(key));
        c.setOnCheckedChangeListener((v, on) -> {
            sp.edit().putBoolean(key, on).apply();
            refresh();
        });
        return c;
    }

    CheckBox extraBox(final String id, String name) {
        CheckBox c = new CheckBox(this);
        c.setText(name);
        c.setTextColor(MainActivity.TXT);
        c.setTextSize(13);
        c.setChecked(("," + sv("a_extras") + ",").contains("," + id + ","));
        c.setOnCheckedChangeListener((v, on) -> {
            StringBuilder sb = new StringBuilder();
            for (String e : EX_ID) {
                boolean has = e.equals(id) ? on : ("," + sv("a_extras") + ",").contains("," + e + ",");
                if (has) sb.append(sb.length() > 0 ? "," : "").append(e);
            }
            sp.edit().putString("a_extras", sb.toString()).apply();
            refresh();
        });
        return c;
    }

    EditText edit(final String key, String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MainActivity.DIM);
        e.setTextColor(MainActivity.TXT);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setText(Feat.str(sp, key, ""));
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                sp.edit().putString(key, s.toString().trim()).apply();
                refresh();
            }
        });
        return e;
    }

    Button action(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setBackgroundColor(color);
        return b;
    }

    void refresh() {
        if (preview != null) preview.invalidate();
        if (audioNow != null) audioNow.setText(audioSummary());
    }

    /** الصوت الافتراضي الحالي (اللي هيتطبّق لو ما غيّرتش حاجة). */
    String audioSummary() {
        String mode = sv("a_mode");
        StringBuilder s = new StringBuilder("🔊 الصوت الحالي:\n");
        boolean musicOn = sp.getBoolean("mus", sp.getBoolean("d_mus", Feat.F_MUS));
        String murl = sp.getString("murl", sp.getString("d_murl", ""));
        String mv = sp.getString("mvol", sp.getString("d_mvol", Feat.F_MVOL));
        boolean orig = Feat.get(sp, 4) || !"custom".equals(mode);
        s.append("• الموسيقى: ").append(musicOn ? "مدمجة في الفيديو" : "مش مدمجة (مقفولة)")
                .append(murl.isEmpty() ? " — الافتراضية: موسيقى هادئة مدمجة" : " — ملفك/رابطك").append("، مستوى ").append(mv).append("%\n");
        s.append("• صوت الفيديو الأصلي: ").append(orig ? "شغّال" : "مقفول").append(" بمستوى ").append(sv("a_vol")).append("%\n");
        boolean loud = bv("a_loud");
        s.append("• توحيد المستوى: ").append(loud ? ("شغّال (" + ("quiet".equals(sv("a_target")) ? "-18" : "loud".equals(sv("a_target")) ? "-14" : "-16") + " LUFS)") : "مقفول").append("\n");
        if (!"custom".equals(mode)) s.append("• الوضع «").append("best".equals(mode) ? "الأفضل" : "Pro").append("» بيضيف تلقائيًا: وضوح كلام + تنقية دوشة + خفض الموسيقى وقت الكلام.");
        return s.toString();
    }

    // ───────── الشاشة ─────────
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MainActivity.BG);
        root.setPadding(dp(14), dp(18), dp(14), dp(10));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView t = new TextView(this);
        t.setText("🎛️ الاستوديو");
        t.setTextSize(19);
        t.setTextColor(MainActivity.TXT);
        t.setGravity(Gravity.CENTER);
        root.addView(t);

        // معاينة ثابتة فوق: مكان اللوجو والنص وشريط التقدّم
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        preview = new Preview(this);
        top.addView(preview, new LinearLayout.LayoutParams(dp(108), dp(192)));
        TextView lg = note("معاينة (٩:١٦) — بتبيّن مكان اللوجو والنص والمصدر وشريط التقدّم والفلتر لحظيًا.\n"
                + "الخطوط المنقّطة = لسه مفيش لوجو. الشكل النهائي بيتشوف في «جرّب على فيديو تجريبي» تحت.");
        lg.setPadding(dp(10), 0, 0, 0);
        top.addView(lg, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(top);

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);

        // 1) الوضع
        section(c, "🏆 الوضع", "«الأفضل» (الافتراضي) توليفة جاهزة للريلز: قص بالطول + أحسن اللحظات + وجوه + فلتر سينمائي خفيف + صوت نضيف ومتوحّد + شريط تقدّم. "
                + "«Pro» أقوى: فلتر ذهبي + وضوح + حبيبات + تكبير بطيء + حركة نص + صوت أعلى. «مخصّص» = بس اللي تعلّم عليه. "
                + "الوضع الجاهز بيشغّل توليفته، وأي حاجة تختارها تحت بتتضاف فوقها.", "على الريل كله");
        c.addView(cycle("a_mode", MODE_ID, MODE_NAME, ""), new LinearLayout.LayoutParams(-1, dp(46)));

        // 2) الفلاتر
        section(c, "🎨 الفلاتر", "فلاتر ألوان احترافية زي إنستجرام (ذهبي، سينمائي، كلارندون، جونو...). الشدّة بتتحكم في قوة الفلتر.", "على الفيديو كله");
        addRow(c, cycle("a_look", LOOK_ID, LOOK_NAME, "فلتر: "),
                cycle("a_look_i", new String[]{"30", "50", "70", "100"}, new String[]{"30%", "50%", "70%", "100%"}, "الشدّة: "));
        c.addView(note("إضافات الصورة (علّم اللي عايزه):"));
        for (int i = 0; i < EX_ID.length; i += 2) {
            addRow(c, extraBox(EX_ID[i], EX_NAME[i]), i + 1 < EX_ID.length ? extraBox(EX_ID[i + 1], EX_NAME[i + 1]) : new View(this));
        }

        // 3) اللوجو
        section(c, "🏷️ اللوجو", "حط رابط صورة (PNG شفاف أحسن) أو ارفع صورة من جهازك. بيتحط على كل الريلز بالحجم والشفافية اللي تختارهم.",
                "في ركن من الأركان (فوق بيبعد شوية عن الحافة عشان واجهة المنصة ما تغطّيهوش)");
        c.addView(edit("a_logo_url", "رابط اللوجو (فاضي = من غير لوجو)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI),
                new LinearLayout.LayoutParams(-1, -2));
        logoUp = action("📁 رفع لوجو من الجهاز", MainActivity.ACC);
        logoUp.setOnClickListener(v -> pickLogo());
        Button clr = action("🗑️ مسح اللوجو", 0xFF3A2A4D);
        clr.setOnClickListener(v -> {
            sp.edit().putString("a_logo_url", "").apply();
            recreate();
        });
        addRow(c, logoUp, clr);
        addRow(c, cycle("a_logo_pos", POS_ID, POS_NAME, "المكان: "),
                cycle("a_logo_size", new String[]{"10", "14", "18", "24", "32"}, new String[]{"10%", "14%", "18%", "24%", "32%"}, "الحجم: "),
                cycle("a_logo_op", new String[]{"50", "70", "90", "100"}, new String[]{"50%", "70%", "90%", "100%"}, "الشفافية: "));

        // 4) النص
        section(c, "📝 شكل ومكان النص", "النص نفسه بتكتبه في الشاشة الرئيسية. هنا بتغيّر مكانه (أعلى/وسط/أسفل) وشكله: ساده بس، صندوق، نيون، لون وحجم وخط وحركة ظهور.",
                "أعلى = تحت شريط المنصة بشوية • وسط • أسفل = فوق منطقة الوصف والأزرار");
        addRow(c, cycle("a_tpos", TPOS, TPOS, "المكان: "), cycle("a_tsize", TSIZE, TSIZE, "الحجم: "));
        c.addView(cycle("a_tstyle", TSTYLE, TSTYLE, "النمط: "), new LinearLayout.LayoutParams(-1, dp(44)));
        addRow(c, cycle("a_tfont", TFONT, TFONT, "الخط: "), cycle("a_tcolor", TCOL_ID, TCOL_NAME, "اللون: "));
        c.addView(cycle("a_tanim", ANIM_ID, ANIM_NAME, "حركة الظهور: "), new LinearLayout.LayoutParams(-1, dp(44)));
        c.addView(note("(في «الأفضل» و«Pro» حركة الظهور بتتضاف تلقائي لو سيبتها «ثابت».)"));
        c.addView(note("سطر المصدر/الشكر (اختياري) — سطر صغير بيتكتب فوق في الفيديو، مفيد لو بتنسب المحتوى لصاحبه:"));
        c.addView(edit("a_cred", "مثلًا: المصدر: اسم القناة", InputType.TYPE_CLASS_TEXT), new LinearLayout.LayoutParams(-1, -2));

        // 5) الحركة
        section(c, "🎞️ حركة وتأثيرات", "شريط تقدّم رفيع بيتحرك مع الريل زي تيك توك، وتكبير بطيء متواصل بيدّي الفيديو حيوية.", "الشريط تحت خالص • التكبير على الصورة كلها");
        addRow(c, box("📊 شريط تقدّم", "a_progress"), box("🔍 تكبير بطيء", "a_zoom"));

        // 6) الصوت
        section(c, "🔊 الصوت", "تحكم كامل في صوت الفيديو الأصلي والموسيقى. «وضوح الكلام» و«التنقية» و«خفض الموسيقى وقت الكلام» بيخلّوا الصوت نضيف ومسموع.",
                "على صوت الفيديو الأصلي، والموسيقى بتتحكم فيها من الشاشة الرئيسية (مدمجة/مستوى/ملف)");
        audioNow = note(audioSummary());
        audioNow.setTextColor(MainActivity.TXT);
        c.addView(audioNow);
        addRow(c, box("📏 توحيد المستوى", "a_loud"), box("🗣️ وضوح الكلام", "a_voice"));
        addRow(c, box("🎙️ تنقية الدوشة", "a_denoise"), box("🎚️ خفض الموسيقى وقت الكلام", "a_duck"));
        addRow(c, box("🧱 ضغط ديناميكي", "a_comp"), box("🔈 تقوية الباص", "a_bass"), box("✨ تفتيح الحدّة", "a_treble"));
        addRow(c, cycle("a_target", new String[]{"quiet", "std", "loud"}, new String[]{"هادي (-18)", "قياسي (-16)", "عالي (-14)"}, "المستوى: "),
                cycle("a_vol", new String[]{"60", "80", "100", "130"}, new String[]{"60%", "80%", "100%", "130%"}, "الصوت الأصلي: "));

        // 7) الحقوق
        section(c, "📜 سجل الحقوق", "بيحفظ ملف rights.txt جوه reels.zip فيه التاريخ والموقع المصدر وإقرارك إنك صاحب المحتوى أو مصرّح لك. التطبيق مش بيخفي ولا بيشيل أي حقوق — "
                + "استخدمه مع محتوى بتملكه أو ليك تصريح بيه.", "جوه reels.zip");
        c.addView(box("احفظ سجل الحقوق مع الريلز", "a_ledger"));

        // 8) تجريبي + حفظ
        section(c, "▶️ فيديو تجريبي (١٠ ثواني)", "بيعمل فيديو تجريبي من غير ما تحمّل حاجة، وبيطبّق عليه كل اللي اخترته، وبيطلعلك ملفين «قبل» و«بعد» + ملخص بكل تأثير اتطبّق. "
                + "الأحسن تجرّب بيه قبل ما تشتغل على ريلز حقيقية.", "بيظهر في الشاشة الرئيسية بعد ما يخلص (زي أي ريل)");
        Button demo = action("▶️ جرّب كل الخصائص على فيديو تجريبي", MainActivity.ACC);
        demo.setOnClickListener(v -> {
            sp.edit().putBoolean("run_demo", true).apply();
            finish();
        });
        c.addView(demo, new LinearLayout.LayoutParams(-1, dp(50)));
        Button save = action("💾 حفظ كافتراضي", 0xFF222C5C);
        save.setOnClickListener(v -> {
            Feat.saveDefault(sp);
            toast("اتحفظت كإعدادات افتراضية ✓");
        });
        Button reset = action("↩️ رجوع للأصلي (الأفضل)", 0xFF3A2A4D);
        reset.setOnClickListener(v -> {
            SharedPreferences.Editor e = sp.edit();
            for (String k : Feat.ADV_STR) e.remove(k).remove("d_" + k);
            for (String k : Feat.ADV_BOOL) e.remove(k).remove("d_" + k);
            e.apply();
            toast("رجعت للوضع الأصلي ✓");
            recreate();
        });
        addRow(c, save, reset);

        ScrollView sv = new ScrollView(this);
        sv.addView(c);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    // ───────── رفع اللوجو (بيترفع كملف إصدار على الريبو زي الموسيقى) ─────────
    void pickLogo() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "اختار صورة اللوجو"), REQ_LOGO);
        } catch (Throwable e) {
            toast("مفيش تطبيق ملفات يقدر يفتح الاختيار.");
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_LOGO && res == RESULT_OK && data != null && data.getData() != null) uploadLogo(data.getData());
    }

    void uploadLogo(final Uri uri) {
        toast("⏳ برفع اللوجو...");
        logoUp.setEnabled(false);
        new Thread(() -> {
            String url = null, err = null;
            try {
                url = doUpload(uri);
            } catch (Throwable e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String fu = url, fe = err;
            runOnUiThread(() -> {
                logoUp.setEnabled(true);
                if (fu != null) {
                    sp.edit().putString("a_logo_url", fu).apply();
                    toast("✅ اترفع اللوجو");
                    recreate();
                } else {
                    toast("❌ فشل رفع اللوجو: " + fe + "\nتقدر تلزق رابط مباشر للصورة بدل الرفع.");
                }
            });
        }).start();
    }

    String doUpload(Uri uri) throws Exception {
        String name = "logo.png";
        try (Cursor cu = getContentResolver().query(uri, null, null, null, null)) {
            if (cu != null && cu.moveToFirst()) {
                int ni = cu.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ni >= 0 && cu.getString(ni) != null) name = cu.getString(ni);
            }
        } catch (Throwable ignored) { }
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "png";
        if (!ext.matches("png|jpg|jpeg|webp")) ext = "png";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("تعذّرت قراءة الصورة");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 5 * 1024 * 1024) throw new Exception("الصورة أكبر من 5 م.ب");
            }
        }
        byte[] data = bo.toByteArray();
        if (data.length < 200) throw new Exception("الصورة فاضية أو صغيرة جدًا");

        String old = sp.getString("logo_tag", "");
        if (!old.isEmpty()) {
            try {
                String[] r = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO + "/releases/tags/" + old, null);
                if (r[0].startsWith("2")) {
                    Gh.http("DELETE", Gh.API + "/repos/" + Gh.REPO + "/releases/" + new JSONObject(r[1]).getLong("id"), null);
                }
                Gh.http("DELETE", Gh.API + "/repos/" + Gh.REPO + "/git/refs/tags/" + old, null);
            } catch (Throwable ignored) { }
        }
        String tag = "logo-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String body = new JSONObject().put("tag_name", tag).put("name", "logo").put("body", "logo").put("prerelease", true).toString();
        String[] cr = Gh.http("POST", Gh.API + "/repos/" + Gh.REPO + "/releases", body);
        int code = Integer.parseInt(cr[0]);
        if (code == 401 || code == 403 || code == 404) throw new Exception("التوكن مالوش صلاحية رفع ملفات على الريبو (محتاج Contents: write)");
        if (code < 200 || code >= 300) throw new Exception("خطأ " + code);
        long rid = new JSONObject(cr[1]).getLong("id");
        String mime = "png".equals(ext) ? "image/png" : "webp".equals(ext) ? "image/webp" : "image/jpeg";
        String aname = "logo." + ext;
        HttpURLConnection hc = Gh.open("POST", "https://uploads.github.com/repos/" + Gh.REPO + "/releases/" + rid + "/assets?name="
                + URLEncoder.encode(aname, "UTF-8"), true);
        try {
            hc.setReadTimeout(120000);
            hc.setDoOutput(true);
            hc.setRequestProperty("Content-Type", mime);
            hc.setFixedLengthStreamingMode(data.length);
            try (OutputStream o = hc.getOutputStream()) {
                o.write(data);
            }
            int rc = hc.getResponseCode();
            if (rc < 200 || rc >= 300) throw new Exception("خطأ رفع " + rc);
        } finally {
            hc.disconnect();
        }
        sp.edit().putString("logo_tag", tag).apply();
        return "https://github.com/" + Gh.REPO + "/releases/download/" + tag + "/" + aname;
    }

    // ───────── المعاينة ─────────
    class Preview extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Preview(Activity a) { super(a); }

        @Override protected void onDraw(Canvas cv) {
            int W = getWidth(), H = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new android.graphics.LinearGradient(0, 0, W, H, 0xFF2B3A67, 0xFF111827, android.graphics.Shader.TileMode.CLAMP));
            RectF all = new RectF(0, 0, W, H);
            cv.drawRoundRect(all, dp(10), dp(10), p);
            p.setShader(null);
            // الفلتر (تلوين تقريبي)
            String look = sv("a_look");
            String mode = sv("a_mode");
            int tint = 0;
            for (int i = 0; i < LOOK_ID.length; i++) if (LOOK_ID[i].equals(look)) tint = LOOK_TINT[i];
            if (tint == 0 && "best".equals(mode)) tint = 0x2200A3B5;
            if (tint == 0 && "pro".equals(mode)) tint = 0x33FFB300;
            if (tint != 0) {
                p.setColor(tint);
                cv.drawRoundRect(all, dp(10), dp(10), p);
            }
            // سطر المصدر
            String cred = sv("a_cred");
            if (!cred.isEmpty()) {
                p.setColor(0xCCFFFFFF);
                p.setTextSize(dp(8));
                p.setTextAlign(Paint.Align.CENTER);
                cv.drawText(cred.length() > 22 ? cred.substring(0, 22) + "…" : cred, W / 2f, H * 0.10f, p);
            }
            // اللوجو
            int size = 18, m = (int) (W * 0.04f);
            try { size = Integer.parseInt(sv("a_logo_size")); } catch (Exception ignored) { }
            float d = W * size / 100f;
            String lp = sv("a_logo_pos");
            float topM = m + H * 0.05f, botM = m + H * 0.12f;
            float lx, ly;
            switch (lp) {
                case "tl": lx = m; ly = topM; break;
                case "bl": lx = m; ly = H - botM - d; break;
                case "br": lx = W - m - d; ly = H - botM - d; break;
                case "c": lx = (W - d) / 2f; ly = (H - d) / 2f; break;
                default: lx = W - m - d; ly = topM; break;
            }
            boolean has = !sv("a_logo_url").isEmpty();
            if (has) {
                p.setColor(0xE6FACC15);
                cv.drawOval(new RectF(lx, ly, lx + d, ly + d), p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0x88FFFFFF);
                p.setStrokeWidth(dp(1));
                p.setPathEffect(new DashPathEffect(new float[]{dp(3), dp(3)}, 0));
                cv.drawOval(new RectF(lx, ly, lx + d, ly + d), p);
                p.setPathEffect(null);
                p.setStyle(Paint.Style.FILL);
            }
            p.setColor(has ? 0xFF1C1917 : 0xAAFFFFFF);
            p.setTextSize(dp(7));
            p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("لوجو", lx + d / 2f, ly + d / 2f + dp(2), p);
            // النص
            String tp = sv("a_tpos"), st = sv("a_tstyle");
            float bh = H * 0.075f, bw = W * 0.82f;
            float ty = "أعلى".equals(tp) ? H * 0.13f : "وسط".equals(tp) ? H * 0.5f - bh / 2f : H * 0.78f - bh;
            int boxC = 0, fg = Color.WHITE;
            if (st.contains("أبيض")) { boxC = 0xEEFFFFFF; fg = 0xFF121212; }
            else if (st.contains("ذهبي")) { boxC = 0xF5FACC15; fg = 0xFF1C1917; }
            else if (st.contains("أسود")) { boxC = 0xEB000000; }
            else if (st.contains("بنفسجي")) { boxC = 0xEB7C5CFF; }
            else if (st.contains("داكن")) { boxC = 0xA5000000; }
            else if (st.contains("نيون")) { fg = 0xFF5EEAD4; }
            else if (st.contains("أصفر")) { fg = 0xFFFFE200; }
            String tc = sv("a_tcolor");
            if (!tc.isEmpty()) {
                try { fg = Color.parseColor("#" + tc); } catch (Exception ignored) { }
            }
            if (boxC != 0) {
                p.setColor(boxC);
                cv.drawRoundRect(new RectF((W - bw) / 2f, ty, (W + bw) / 2f, ty + bh), dp(6), dp(6), p);
            }
            p.setColor(fg);
            p.setTextSize(dp(9));
            p.setFakeBoldText(true);
            p.setTextAlign(Paint.Align.CENTER);
            cv.drawText("نصك هنا", W / 2f, ty + bh / 2f + dp(3), p);
            p.setFakeBoldText(false);
            // شريط التقدّم
            if (bv("a_progress") || !"custom".equals(mode)) {
                p.setColor(0x55000000);
                cv.drawRect(0, H - dp(3), W, H, p);
                p.setColor(0xFF2DD4BF);
                cv.drawRect(0, H - dp(3), W * 0.45f, H, p);
            }
        }
    }
}
