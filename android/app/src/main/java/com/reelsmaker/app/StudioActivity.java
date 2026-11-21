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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

/**
 * 🎛️ استوديو برو (v6): كل الخصائص في مكان واحد ومتناسقة مع بعض — كل خاصية جنبها مفتاح تشغيل/إيقاف وشرح بسيط.
 * اللقطات الذكية • الموسيقى والأغاني • عكس الفيديو • الريأكشن • الفلاتر • اللوجو • النص • الحركة • الصوت • الحقوق.
 * فوق شريط حالة بيوريك كل اللي شغّال لحظيًا. كل ضغطة بتتحفظ فورًا. الوضع الجاهز (الأفضل/Pro) بيظبط المفاتيح نفسها،
 * وأي تعديل منك بيحوّله «مخصّص» تلقائي — فمفيش أي تناقض بين الوضع والمفاتيح.
 */
public class StudioActivity extends Activity {
    static final int REQ_LOGO = 31, REQ_MUSIC = 21;
    static int keepY = 0;

    static final String[] LOOK_ID = {"none", "golden", "sunset", "cinematic", "clarendon", "juno", "lark", "valencia",
            "vintage", "noir", "moon", "vivid", "matte", "cool", "drama", "glow"};
    static final String[] LOOK_NAME = {"بدون فلتر", "ذهبي ✨", "غروب", "سينمائي", "كلارندون", "جونو", "لارك", "فالنسيا",
            "فينتاج", "أبيض وأسود", "مون", "زاهي", "مات", "بارد", "دراما", "وهج ناعم"};
    static final int[] LOOK_TINT = {0, 0x66FFB300, 0x66FF7043, 0x5500A3B5, 0x33FFFFFF, 0x55FF8A00, 0x4480DEEA, 0x55FFAB40,
            0x66A1887F, 0x99555555, 0x77666E7A, 0x44FF00AA, 0x44888888, 0x552196F3, 0x77000000, 0x33FFFFFF};

    static final String[] EX_ID = {"vignette", "grain", "sharp", "clarity", "stabilize", "denoise_v", "vibrance", "lowlight", "softskin"};
    static final String[] EX_ICON = {"🌑", "🎞️", "🔪", "🔍", "📐", "🧼", "🌈", "💡", "🪞"};
    static final String[] EX_NAME = {"تعتيم الأطراف", "حبيبات فيلم", "حدّة (Sharpen)", "وضوح (Clarity)", "تثبيت خفيف", "تنظيف الصورة",
            "ألوان نابضة (Vibrance)", "تفتيح الإضاءة الضعيفة", "نعومة البشرة"};
    static final String[] EX_DESC = {"أطراف أغمق بتركّز العين في النص", "لمسة سينمائية خفيفة", "تفاصيل أحدّ", "وضوح أقوى للصورة",
            "بيقلل اهتزاز الفيديو", "بيقلل التشويش", "ألوان أحيى من غير مبالغة", "بيفتّح المشاهد الضلمة", "بشرة أنعم بشكل خفيف"};

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

    // أسماء/أيقونات/شرح الخصائص الثمانية (من Feat) مع تعديل «أحسن اللحظات» ليبقى «اللقطات المهمة»
    static final String[] FT_ICON = {"✂️", "⭐", "👤", "✨", "🔊", "🎙️", "🎞️", "🎼"};
    static final String[] FT_TITLE = {"قص بالطول (٩:١٦)", "اللقطات المهمة", "تتبّع الوجوه", "تحسين الصورة",
            "صوت الفيديو الأصلي", "تنقية الصوت", "تلاشي ناعم", "موسيقى كملف منفصل"};
    static final String[] FT_DESC = {
            "الفيديو يملا الشاشة كلها بدون خلفية مغبّشة",
            "بيختار أقوى لقطات الفيديو (أكشن • ضحك • حب • دراما) بالصورة والمؤثرات — مش بالأغاني",
            "القص بيمشي مع وش الشخص (محتاج «قص بالطول»)",
            "ألوان وحدة أوضح شوية",
            "سيب الصوت الأصلي شغّال في الريل",
            "بيقلل الدوشة ويوضّح الكلام",
            "بداية ونهاية الريل بتدخل وتختفي بنعومة",
            "ملف موسيقى لوحده جنب الريلز"};

    SharedPreferences sp;
    Preview preview;
    TextView sumTv, warnTv;
    Button logoUp, pickMusicBtn;
    EditText musicUrlBox;
    ScrollView sv;
    LinearLayout content, modeRow, momentsRow;
    final Switch[] featSw = new Switch[Feat.ID.length];
    final TextView[] modeChips = new TextView[3];
    Switch denoiseSw;

    int dp(int v) { return Ui.dp(this, v); }

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    String sv(String k) { return Feat.str(sp, k, Feat.advDef(k)); }

    boolean bv(String k) { return Feat.bool(sp, k, Feat.advBoolDef(k)); }

    // ───────── عناصر الواجهة ─────────
    /** كارت قسم: أيقونة + عنوان + شرح + «مكانه». */
    LinearLayout section(String icon, String title, String explain, String where) {
        LinearLayout c = Ui.card(this);
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.addView(Ui.iconChip(this, icon, 34, Ui.ACC2));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), 0, 0, 0);
        col.addView(Ui.text(this, title, 15.5f, Ui.TXT, true));
        h.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        c.addView(h);
        TextView ex = Ui.text(this, explain + (where == null ? "" : "\n📍 مكانه: " + where), 11.5f, Ui.DIM, false);
        ex.setPadding(0, dp(6), 0, dp(4));
        c.addView(ex);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        content.addView(c, lp);
        return c;
    }

    TextView note(String s) {
        TextView t = Ui.text(this, s, 11.5f, Ui.DIM, false);
        t.setPadding(0, dp(4), 0, dp(2));
        return t;
    }

    /** أي تعديل يدوي على «وصفة» الوضع الجاهز بيحوّل الوضع لـ «مخصّص». */
    void manualChange() {
        if (!"custom".equals(sv("a_mode"))) {
            sp.edit().putString("a_mode", "custom").apply();
            paintModes();
        }
        refresh();
    }

    /** صف تشغيل/إيقاف لمفتاح boolean عادي. */
    void tog(LinearLayout card, String icon, String title, String desc, final String key, boolean def, final boolean recipe) {
        card.addView(Ui.toggleRow(this, icon, title, desc, Feat.bool(sp, key, def), (v, on) -> {
            sp.edit().putBoolean(key, on).apply();
            if (recipe) manualChange(); else refresh();
        }));
    }

    /** صف خاصية من الثمانية. */
    void featTog(LinearLayout card, final int i) {
        LinearLayout r = Ui.toggleRow(this, FT_ICON[i], FT_TITLE[i], FT_DESC[i], Feat.get(sp, i), (v, on) -> {
            sp.edit().putBoolean("f_" + Feat.ID[i], on).apply();
            if (i == Feat.FACE && on && !Feat.get(sp, Feat.CROP)) {
                sp.edit().putBoolean("f_crop", true).apply();
                if (featSw[Feat.CROP] != null) featSw[Feat.CROP].setChecked(true);
                toast("فعّلت «قص بالطول» لأن «تتبّع الوجوه» بيشتغل معاه.");
            }
            if (i == Feat.CROP && !on && Feat.get(sp, Feat.FACE)) {
                sp.edit().putBoolean("f_face", false).apply();
                if (featSw[Feat.FACE] != null) featSw[Feat.FACE].setChecked(false);
                toast("قفلت «تتبّع الوجوه» لأنه محتاج «قص بالطول».");
            }
            if (i == Feat.BEST && momentsRow != null) momentsRow.setAlpha(on ? 1f : 0.4f);
            manualChange();
        });
        featSw[i] = (Switch) r.getTag();
        card.addView(r);
    }

    /** اختيار واحد من كام شريحة (للمجموعات الصغيرة). */
    LinearLayout chipRow(final String key, final String def, final String[] ids, final String[] names, final boolean recipe) {
        final LinearLayout row = new LinearLayout(this);
        final TextView[] cs = new TextView[ids.length];
        final String cur = Feat.str(sp, key, def);
        for (int i = 0; i < ids.length; i++) {
            final int idx = i;
            TextView t = Ui.text(this, names[i], 12.5f, Ui.TXT, true);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(4), dp(9), dp(4), dp(9));
            cs[i] = t;
            t.setOnClickListener(v -> {
                sp.edit().putString(key, ids[idx]).apply();
                for (int j = 0; j < cs.length; j++) paintChip(cs[j], j == idx);
                if (recipe) manualChange(); else refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(dp(3), dp(4), dp(3), dp(4));
            row.addView(t, lp);
            paintChip(t, ids[i].equals(cur));
        }
        return row;
    }

    void paintChip(TextView t, boolean on) {
        t.setBackground(on ? Ui.shape(this, Ui.ON_FILL, 12, Ui.ACC, 2) : Ui.shape(this, Ui.CARD2, 12, 0, 0));
        t.setTextColor(on ? Ui.ACC : Ui.DIM);
    }

    /** زرار بيلفّ على الاختيارات (للقوائم الطويلة). */
    Button cycle(final String key, final String[] ids, final String[] names, final String prefix, final boolean recipe) {
        final Button b = Ui.pill(this, "", Ui.CARD2);
        b.setSingleLine(true);
        final int[] idx = {0};
        String cur = sv(key);
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(cur)) idx[0] = i;
        b.setText(prefix + names[idx[0]]);
        b.setOnClickListener(v -> {
            idx[0] = (idx[0] + 1) % ids.length;
            sp.edit().putString(key, ids[idx[0]]).apply();
            b.setText(prefix + names[idx[0]]);
            if (recipe) manualChange(); else refresh();
        });
        return b;
    }

    void addRow(LinearLayout p, View... views) {
        LinearLayout r = new LinearLayout(this);
        for (View v : views) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            r.addView(v, lp);
        }
        p.addView(r);
    }

    EditText edit(final String key, String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Ui.DIM);
        e.setTextColor(Ui.TXT);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setBackground(Ui.shape(this, Ui.CARD2, 12, 0, 0));
        e.setPadding(dp(12), 0, dp(12), 0);
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
        Button b = Ui.pill(this, s, color);
        b.setTextColor(Color.WHITE);
        return b;
    }

    void refresh() {
        if (preview != null) preview.invalidate();
        if (sumTv == null) return;
        String mid = sv("a_moments");
        String mom = "الكل";
        for (int i = 0; i < Feat.MOMENT_ID.length; i++) if (Feat.MOMENT_ID[i].equals(mid)) mom = Feat.MOMENT_NAME[i];
        boolean mus = Feat.bool(sp, "mus", Feat.F_MUS), rv = Feat.bool(sp, "rev", Feat.F_REV), rc = Feat.bool(sp, "react", Feat.F_REACT);
        sumTv.setText("🏆 " + Feat.modeName(sp) + "  •  ✨ " + Feat.enabledCount(sp) + " خصائص  •  🎯 " + (Feat.get(sp, Feat.BEST) ? mom : "توزيع متساوي")
                + "\n🎵 موسيقى: " + (mus ? "شغّالة" : "لا") + "  •  🔁 عكس: " + (rv ? "شغّال" : "لا") + "  •  🎭 ريأكشن: " + (rc ? "شغّال" : "لا"));
        String w = Feat.warnings(sp);
        warnTv.setText(w);
        warnTv.setVisibility(w.isEmpty() ? View.GONE : View.VISIBLE);
    }

    void paintModes() {
        String m = sv("a_mode");
        String[] ids = {"best", "pro", "custom"};
        for (int i = 0; i < 3; i++) if (modeChips[i] != null) paintChip(modeChips[i], ids[i].equals(m));
    }

    // ───────── الشاشة ─────────
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        Feat.ensureInit(sp);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(dp(12), dp(12), dp(12), dp(8));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // الهيدر
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "›", 26, Ui.TXT, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(Ui.shape(this, Ui.CARD, 18, Ui.STROKE, 1));
        back.setOnClickListener(v -> finish());
        hd.addView(back, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        tcol.setPadding(dp(10), 0, 0, 0);
        tcol.addView(Ui.text(this, "🎛️ استوديو برو", 19, Ui.TXT, true));
        tcol.addView(Ui.text(this, "كل الخصائص هنا — كل واحدة جنبها مفتاح تشغيل/إيقاف", 11, Ui.DIM, false));
        hd.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(hd);

        // شريط الحالة العلوي (ثابت): بيوريك الدنيا لحظيًا
        LinearLayout bar = Ui.card(this);
        bar.setPadding(dp(12), dp(8), dp(12), dp(8));
        sumTv = Ui.text(this, "", 12, Ui.TXT, true);
        warnTv = Ui.text(this, "", 11, Ui.WARM, false);
        warnTv.setPadding(0, dp(4), 0, 0);
        bar.addView(sumTv);
        bar.addView(warnTv);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(8);
        root.addView(bar, bp);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout c;

        // ① الوضع الجاهز
        c = section("🏆", "الوضع الجاهز", "«الأفضل» توليفة جاهزة للريلز: قص بالطول + لقطات مهمة + وجوه + فلتر سينمائي خفيف + صوت نضيف + شريط تقدّم. "
                + "«Pro» أقوى: فلتر ذهبي + وضوح + حبيبات + تكبير بطيء + صوت أعلى. «مخصّص» = بس اللي تعلّم عليه. "
                + "الوضع الجاهز بيظبط المفاتيح اللي تحت، وأي تعديل منك بيحوّله «مخصّص» لوحده.", "على الريل كله");
        modeRow = new LinearLayout(this);
        String[] mids = {"best", "pro", "custom"}, mnames = {"🏆 الأفضل", "🔥 Pro", "🛠️ مخصّص"};
        for (int i = 0; i < 3; i++) {
            final String id = mids[i];
            TextView t = Ui.text(this, mnames[i], 13, Ui.TXT, true);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(4), dp(10), dp(4), dp(10));
            modeChips[i] = t;
            t.setOnClickListener(v -> {
                if ("custom".equals(id)) {
                    sp.edit().putString("a_mode", "custom").apply();
                    paintModes();
                    refresh();
                } else {
                    Feat.applyMode(sp, id);
                    keepY = sv.getScrollY();
                    recreate();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(dp(3), dp(4), dp(3), dp(4));
            modeRow.addView(t, lp);
        }
        c.addView(modeRow);
        paintModes();

        // ② اللقطات الذكية
        c = section("🎯", "اللقطات الذكية (التقطيع)", "بيقطّع الفيديو على أحسن اللقطات: أكشن، ضحك، حب، دراما — بالصورة (حركة وقطعات ووجوه) ومؤثرات الصوت (ضحك/صراخ). "
                + "الأغاني مش بتتحسب كلقطة مهمة.", "على اختيار أجزاء الريلز من الفيديو الأصلي");
        featTog(c, Feat.CROP);
        featTog(c, Feat.BEST);
        c.addView(note("نوع اللقطات اللي تدوّر عليها:"));
        momentsRow = chipRow("a_moments", "all", Feat.MOMENT_ID, Feat.MOMENT_NAME, false);
        momentsRow.setAlpha(Feat.get(sp, Feat.BEST) ? 1f : 0.4f);
        c.addView(momentsRow);
        c.addView(note("كل — متوازن • أكشن — حركة وقطعات سريعة • كوميدي — وجوه وضحك ومؤثرات • حب ودراما — لقطات وشوش قريبة وكلام."));
        featTog(c, Feat.FACE);
        featTog(c, 3);

        // ③ الموسيقى والأغاني
        c = section("🎵", "الموسيقى والأغاني", "تحكم كامل في موسيقى الريل: شغّلها/اقفلها، مستواها، ملفك أو رابطك، وبديل أغنية الفيديو الأصلية.", "في صوت الريل النهائي");
        tog(c, "🎵", "موسيقى خلفية", "بتتحط تحت الكلام في كل ريل (فاضي = موسيقى هادئة مدمجة)", "mus", Feat.F_MUS, false);
        c.addView(note("مستوى الموسيقى:"));
        c.addView(chipRow("mvol", Feat.F_MVOL, new String[]{"8", "15", "25"}, new String[]{"🔈 قياسية", "🔉 وسط", "🔊 عالية"}, false));
        c.addView(note("🎵 رابط موسيقى أو ملف من جهازك (فاضي = المدمجة):"));
        musicUrlBox = edit("murl", "رابط مباشر لموسيقى (اختياري)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        pickMusicBtn = action("📁 رفع ملف", Ui.ACC);
        pickMusicBtn.setOnClickListener(v -> pickMusic());
        LinearLayout mr = new LinearLayout(this);
        mr.addView(musicUrlBox, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(92), dp(42));
        pl.setMargins(dp(6), 0, 0, 0);
        mr.addView(pickMusicBtn, pl);
        c.addView(mr);
        tog(c, "🎶", "أغاني الفيديو الأساسية (تلقائي)", "لو الفيديو فيه أغنية، الموسيقى الأساسية بتشتغل مكانها تلقائيًا. مقفولة = صوت الفيديو يفضل زي ما هو", "rsong", Feat.F_RSONG, false);
        featTog(c, 7);
        featTog(c, 4);
        c.addView(cycle("a_vol", new String[]{"60", "80", "100", "130"}, new String[]{"60%", "80%", "100%", "130%"}, "مستوى صوت الفيديو الأصلي: ", true),
                new LinearLayout.LayoutParams(-1, dp(42)));

        // ④ تنويع الفيديو
        c = section("🔁", "تنويع الفيديو", "إعدادات بتغيّر شكل الفيديو الناتج: عكس، ووضع الريأكشن لفيديوهات الرد والتعليق.", "على الصورة والصوت");
        tog(c, "🔁", "عكس الفيديو", "بيشغّل الريل بالعكس (والصوت كمان بيتعكس — الأحسن تشغّل موسيقى معاه)", "rev", Feat.F_REV, false);
        tog(c, "🎭", "وضع الريأكشن", "تكبير بسيط + لمسة ألوان + سرعة 1.03 لفيديوهات الريأكشن. ده مش بيضمن تفادي أي مطالبة حقوق", "react", Feat.F_REACT, false);

        // ⑤ الفلاتر والصورة
        c = section("🎨", "الفلاتر وتحسين الصورة", "فلاتر ألوان احترافية (ذهبي، سينمائي، كلارندون، جونو...) والشدّة بتتحكم في قوة الفلتر. وتحتها إضافات للصورة تشغّل/تقفل كل واحدة.", "على الفيديو كله");
        addRow(c, cycle("a_look", LOOK_ID, LOOK_NAME, "فلتر: ", true),
                cycle("a_look_i", new String[]{"30", "50", "70", "100"}, new String[]{"30%", "50%", "70%", "100%"}, "الشدّة: ", true));
        for (int i = 0; i < EX_ID.length; i++) {
            final String id = EX_ID[i];
            c.addView(Ui.toggleRow(this, EX_ICON[i], EX_NAME[i], EX_DESC[i], (",".concat(sv("a_extras")).concat(",")).contains("," + id + ","), (v, on) -> {
                StringBuilder sb = new StringBuilder();
                for (String e : EX_ID) {
                    boolean has = e.equals(id) ? on : ("," + sv("a_extras") + ",").contains("," + e + ",");
                    if (has) sb.append(sb.length() > 0 ? "," : "").append(e);
                }
                sp.edit().putString("a_extras", sb.toString()).apply();
                manualChange();
            }));
        }

        // ⑥ اللوجو (المعاينة بتتحط قبله)
        final int logoIdx = content.getChildCount();
        c = section("🏷️", "اللوجو", "حط رابط صورة (PNG شفاف أحسن) أو ارفع صورة من جهازك. بيتحط على كل الريلز بالحجم والشفافية اللي تختارهم.",
                "في ركن من الأركان (فوق بيبعد شوية عن الحافة عشان واجهة المنصة ما تغطّيهوش)");
        c.addView(edit("a_logo_url", "رابط اللوجو (فاضي = من غير لوجو)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI),
                new LinearLayout.LayoutParams(-1, dp(42)));
        logoUp = action("📁 رفع لوجو", Ui.ACC);
        logoUp.setOnClickListener(v -> pickLogo());
        Button clr = action("🗑️ مسح اللوجو", Ui.DANGER);
        clr.setOnClickListener(v -> {
            sp.edit().putString("a_logo_url", "").apply();
            keepY = sv.getScrollY();
            recreate();
        });
        addRow(c, logoUp, clr);
        addRow(c, cycle("a_logo_pos", POS_ID, POS_NAME, "المكان: ", false),
                cycle("a_logo_size", new String[]{"10", "14", "18", "24", "32"}, new String[]{"10%", "14%", "18%", "24%", "32%"}, "الحجم: ", false),
                cycle("a_logo_op", new String[]{"50", "70", "90", "100"}, new String[]{"50%", "70%", "90%", "100%"}, "الشفافية: ", false));

        // ⑦ النص
        c = section("📝", "شكل ومكان النص", "النص نفسه بتكتبه في الشاشة الرئيسية. هنا بتغيّر مكانه وشكله: ساده، صندوق، نيون، لون وحجم وخط وحركة ظهور.",
                "أعلى = تحت شريط المنصة بشوية • وسط • أسفل = فوق منطقة الوصف والأزرار");
        addRow(c, cycle("a_tpos", TPOS, TPOS, "المكان: ", false), cycle("a_tsize", TSIZE, TSIZE, "الحجم: ", false));
        c.addView(cycle("a_tstyle", TSTYLE, TSTYLE, "النمط: ", false), new LinearLayout.LayoutParams(-1, dp(42)));
        addRow(c, cycle("a_tfont", TFONT, TFONT, "الخط: ", false), cycle("a_tcolor", TCOL_ID, TCOL_NAME, "اللون: ", false));
        c.addView(cycle("a_tanim", ANIM_ID, ANIM_NAME, "حركة الظهور: ", true), new LinearLayout.LayoutParams(-1, dp(42)));
        c.addView(note("سطر المصدر/الشكر (اختياري) — سطر صغير بيتكتب فوق في الفيديو، مفيد لو بتنسب المحتوى لصاحبه:"));
        c.addView(edit("a_cred", "مثلًا: المصدر: اسم القناة", InputType.TYPE_CLASS_TEXT), new LinearLayout.LayoutParams(-1, dp(42)));

        // ⑧ الحركة
        c = section("🎞️", "حركة وتأثيرات", "شريط تقدّم رفيع بيتحرك مع الريل، وتكبير بطيء بيدّي الفيديو حيوية، وتلاشي ناعم في البداية والنهاية.", "الشريط تحت خالص • التكبير على الصورة كلها");
        tog(c, "📊", "شريط تقدّم", "شريط رفيع تحت زي تيك توك", "a_progress", false, true);
        tog(c, "🔍", "تكبير بطيء", "حركة تكبير هادية طول الريل", "a_zoom", false, true);
        featTog(c, 6);

        // ⑨ الصوت
        c = section("🔊", "الصوت", "تحكم في جودة الصوت الأصلي. «وضوح الكلام» و«التنقية» و«خفض الموسيقى وقت الكلام» بيخلّوا الصوت نضيف ومسموع.", "على صوت الفيديو الأصلي");
        tog(c, "📏", "توحيد المستوى", "بيخلّي علو الصوت متساوي في كل الريلز", "a_loud", true, true);
        tog(c, "🗣️", "وضوح الكلام", "بيبرّز الكلام على الخلفية", "a_voice", false, true);
        LinearLayout dn = Ui.toggleRow(this, "🎙️", "تنقية الدوشة", "بيقلل الضوضاء من الكلام", Feat.get(sp, 5) || bv("a_denoise"), (v, on) -> {
            sp.edit().putBoolean("f_denoise", on).putBoolean("a_denoise", on).apply();
            manualChange();
        });
        denoiseSw = (Switch) dn.getTag();
        c.addView(dn);
        tog(c, "🎚️", "خفض الموسيقى وقت الكلام", "الموسيقى بتهدى لما حد يتكلم", "a_duck", false, true);
        tog(c, "🧱", "ضغط ديناميكي", "بيقرّب الصوت العالي من الواطي", "a_comp", false, true);
        tog(c, "🔈", "تقوية الباص", "صوت أعمق", "a_bass", false, true);
        tog(c, "✨", "تفتيح الحدّة", "صوت أصفى", "a_treble", false, true);
        c.addView(cycle("a_target", new String[]{"quiet", "std", "loud"}, new String[]{"هادي (-18)", "قياسي (-16)", "عالي (-14)"}, "مستوى التوحيد: ", true),
                new LinearLayout.LayoutParams(-1, dp(42)));

        // ⑩ الحقوق
        c = section("📜", "سجل الحقوق", "بيحفظ ملف rights.txt جوه reels.zip فيه التاريخ والموقع المصدر وإقرارك إنك صاحب المحتوى أو مصرّح لك. "
                + "التطبيق مش بيخفي ولا بيشيل أي حقوق — استخدمه مع محتوى بتملكه أو ليك تصريح بيه.", "جوه reels.zip");
        tog(c, "📜", "احفظ سجل الحقوق مع الريلز", "مفعّل أصلًا (موصى بيه)", "a_ledger", true, false);

        // ⑪ تجريبي + حفظ
        c = section("▶️", "فيديو تجريبي (١٠ ثواني)", "بيعمل فيديو تجريبي من غير ما تحمّل حاجة، وبيطبّق عليه كل اللي اخترته، وبيطلعلك ملفين «قبل» و«بعد» + ملخص بكل تأثير اتطبّق. "
                + "الأحسن تجرّب بيه قبل ما تشتغل على ريلز حقيقية.", "بيظهر في الشاشة الرئيسية بعد ما يخلص (زي أي ريل)");
        Button demo = action("▶️ جرّب كل الخصائص على فيديو تجريبي", Ui.ACC);
        demo.setOnClickListener(v -> {
            sp.edit().putBoolean("run_demo", true).apply();
            finish();
        });
        c.addView(demo, new LinearLayout.LayoutParams(-1, dp(48)));
        Button save = action("💾 حفظ كافتراضي", Ui.CARD2);
        save.setOnClickListener(v -> {
            Feat.saveDefault(sp);
            toast("اتحفظت كإعدادات افتراضية ✓");
        });
        Button reset = action("↩️ رجوع للأصلي (الأفضل)", Ui.DANGER);
        reset.setOnClickListener(v -> {
            Feat.resetAll(sp);
            toast("رجعت للوضع الأصلي ✓");
            keepY = 0;
            recreate();
        });
        addRow(c, save, reset);
        View pad = new View(this);
        content.addView(pad, new LinearLayout.LayoutParams(-1, dp(12)));

        sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(content);
        // معاينة ثابتة فوق: مكان اللوجو والنص وشريط التقدّم (جوه قسم اللوجو بدل ما تاخد مساحة الشاشة)
        preview = new Preview(this);
        LinearLayout pv = Ui.card(this);
        LinearLayout pr = new LinearLayout(this);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        pr.addView(preview, new LinearLayout.LayoutParams(dp(90), dp(160)));
        TextView lg = note("معاينة (٩:١٦) — بتبيّن مكان اللوجو والنص والمصدر والفلتر لحظيًا.\nالخطوط المنقّطة = لسه مفيش لوجو. الشكل النهائي بيتشوف في «جرّب على فيديو تجريبي» تحت.");
        lg.setPadding(dp(10), 0, 0, 0);
        pr.addView(lg, new LinearLayout.LayoutParams(0, -2, 1f));
        pv.addView(pr);
        LinearLayout.LayoutParams pvp = new LinearLayout.LayoutParams(-1, -2);
        pvp.topMargin = dp(10);
        content.addView(pv, logoIdx, pvp);

        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        if (keepY > 0) {
            final int y = keepY;
            keepY = 0;
            sv.post(() -> sv.scrollTo(0, y));
        }
        refresh();
    }

    // ───────── رفع ملف موسيقى من الجهاز (بيترفع كملف إصدار على الريبو، ويتبعت رابطه للسيرفر) ─────────
    void pickMusic() {
        if (Gh.TOK.isEmpty() || Gh.REPO.isEmpty()) { toast("التطبيق اتبنى من غير بيانات الخدمة."); return; }
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("audio/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "اختار ملف موسيقى"), REQ_MUSIC);
        } catch (Throwable e) {
            toast("مفيش تطبيق ملفات يقدر يفتح الاختيار.");
        }
    }

    void uploadMusic(final Uri uri) {
        toast("⏳ برفع الموسيقى...");
        pickMusicBtn.setEnabled(false);
        new Thread(() -> {
            String url = null, err = null;
            try {
                url = doUploadMusic(uri);
            } catch (Throwable e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String fu = url, fe = err;
            runOnUiThread(() -> {
                pickMusicBtn.setEnabled(true);
                if (fu != null) {
                    musicUrlBox.setText(fu);
                    toast("✅ اترفعت الموسيقى. هتتستخدم في الريل الجاي.");
                } else {
                    toast("❌ فشل رفع الموسيقى: " + fe + "\nتقدر تلزق رابط مباشر بدل الرفع.");
                }
            });
        }).start();
    }

    String doUploadMusic(Uri uri) throws Exception {
        String name = "music.mp3";
        long size = -1;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME), si = c.getColumnIndex(OpenableColumns.SIZE);
                if (ni >= 0 && c.getString(ni) != null) name = c.getString(ni);
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
            }
        } catch (Throwable ignored) { }
        if (size > 25L * 1024 * 1024) throw new Exception("الملف أكبر من 25 م.ب");
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "mp3";
        if (!ext.matches("mp3|m4a|aac|wav|ogg|oga|opus|flac")) ext = "mp3";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("تعذّرت قراءة الملف");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 25 * 1024 * 1024) throw new Exception("الملف أكبر من 25 م.ب");
            }
        }
        byte[] data = bo.toByteArray();
        if (data.length < 2048) throw new Exception("الملف فاضي أو صغير جدًا");

        // امسح موسيقى اتـرفعت قبل كده (عشان الريبو ما يتملاش)
        String old = sp.getString("music_tag", "");
        if (!old.isEmpty()) {
            try {
                String[] r = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO + "/releases/tags/" + old, null);
                if (r[0].startsWith("2")) {
                    Gh.http("DELETE", Gh.API + "/repos/" + Gh.REPO + "/releases/" + new JSONObject(r[1]).getLong("id"), null);
                }
                Gh.http("DELETE", Gh.API + "/repos/" + Gh.REPO + "/git/refs/tags/" + old, null);
            } catch (Throwable ignored) { }
        }
        String tag = "music-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String body = new JSONObject().put("tag_name", tag).put("name", "music").put("body", "music").put("prerelease", true).toString();
        String[] cr = Gh.http("POST", Gh.API + "/repos/" + Gh.REPO + "/releases", body);
        int code = Integer.parseInt(cr[0]);
        if (code == 401 || code == 403 || code == 404) throw new Exception("التوكن مالوش صلاحية رفع ملفات على الريبو (محتاج Contents: write)");
        if (code < 200 || code >= 300) throw new Exception("خطأ " + code);
        long rid = new JSONObject(cr[1]).getLong("id");

        String mime = "wav".equals(ext) ? "audio/wav" : "ogg".equals(ext) || "oga".equals(ext) || "opus".equals(ext) ? "audio/ogg"
                : "m4a".equals(ext) || "aac".equals(ext) ? "audio/mp4" : "flac".equals(ext) ? "audio/flac" : "audio/mpeg";
        String aname = "music." + ext;
        HttpURLConnection c = Gh.open("POST", "https://uploads.github.com/repos/" + Gh.REPO + "/releases/" + rid + "/assets?name="
                + URLEncoder.encode(aname, "UTF-8"), true);
        try {
            c.setReadTimeout(120000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", mime);
            c.setFixedLengthStreamingMode(data.length);
            try (OutputStream o = c.getOutputStream()) {
                o.write(data);
            }
            int rc = c.getResponseCode();
            if (rc < 200 || rc >= 300) throw new Exception("خطأ رفع " + rc);
        } finally {
            c.disconnect();
        }
        sp.edit().putString("music_tag", tag).apply();
        return "https://github.com/" + Gh.REPO + "/releases/download/" + tag + "/" + aname;
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
        if (req == REQ_MUSIC && res == RESULT_OK && data != null && data.getData() != null) uploadMusic(data.getData());
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
