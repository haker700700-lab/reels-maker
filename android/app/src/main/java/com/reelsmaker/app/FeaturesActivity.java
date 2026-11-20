package com.reelsmaker.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * شاشة الخصائص: مربعات (٣ × ٣) بشرح خفيف، علّم على اللي عايزه. كل ضغطة بتتحفظ فورًا.
 * الشاشة ثابتة (من غير تمرير). المربع التاسع بيرجّع كل الإعدادات للأصلية.
 */
public class FeaturesActivity extends Activity {
    static final int BG = 0xFF0E1224, CARD = 0xFF1A2040, ACC = 0xFF7C5CFF, ACC2 = 0xFF2DD4BF, TXT = 0xFFF1F3FF, DIM = 0xFF8E95B8;
    static final int ON_FILL = 0xFF222C5C, OFF_STROKE = 0xFF2A3156;

    SharedPreferences sp;
    final LinearLayout[] tiles = new LinearLayout[Feat.ID.length];
    final TextView[] marks = new TextView[Feat.ID.length];
    TextView note;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable shape(int fill, int r, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(r));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    LinearLayout.LayoutParams cellLp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -1, 1f);
        l.setMargins(dp(4), dp(4), dp(4), dp(4));
        return l;
    }

    TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    Button pill(String s, int fill) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TXT);
        b.setTextSize(13);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(shape(fill, 12, 0, 0));
        b.setStateListAnimator(null);
        return b;
    }

    /** مربع خاصية: أيقونة + علامة (✓ / ○) + الاسم + شرح خفيف. */
    LinearLayout tile(final int i) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(8), dp(8), dp(8), dp(6));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView ic = text(Feat.ICON[i], 24, TXT, false);
        View gap = new View(this);
        TextView mk = text("○", 18, DIM, true);
        marks[i] = mk;
        top.addView(ic, new LinearLayout.LayoutParams(-2, -2, 0f));
        top.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(mk, new LinearLayout.LayoutParams(-2, -2, 0f));
        t.addView(top);
        TextView nm = text(Feat.NAME[i], 13, TXT, true);
        nm.setSingleLine(true);
        nm.setPadding(0, dp(4), 0, dp(2));
        t.addView(nm);
        TextView ds = text(Feat.DESC[i], 10.5f, DIM, false);
        ds.setMaxLines(3);
        ds.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(ds);
        t.setOnClickListener(v -> {
            sp.edit().putBoolean("f_" + Feat.ID[i], !Feat.get(sp, i)).apply();
            paint(i);
            updateNote(null);
        });
        tiles[i] = t;
        paint(i);
        return t;
    }

    void paint(int i) {
        boolean on = Feat.get(sp, i);
        tiles[i].setBackground(on ? shape(ON_FILL, 14, ACC2, 2) : shape(CARD, 14, OFF_STROKE, 1));
        marks[i].setText(on ? "✓" : "○");
        marks[i].setTextColor(on ? ACC2 : DIM);
    }

    void paintAll() {
        for (int i = 0; i < Feat.ID.length; i++) paint(i);
    }

    void updateNote(String msg) {
        if (msg != null) {
            note.setText(msg);
        } else if (Feat.get(sp, Feat.FACE) && !Feat.get(sp, Feat.CROP)) {
            note.setText("⚠️ تتبّع الوجوه بيشتغل مع «قص بالطول» بس — فعّله.");
        } else {
            note.setText("علّم على اللي عايزه، والباقي سيبه. (" + Feat.enabledCount(sp) + " مفعّلة)");
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(12), dp(12), dp(12), dp(10));

        TextView title = text("✨ الخصائص", 22, TXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        note = text("", 11, DIM, false);
        note.setGravity(Gravity.CENTER);
        note.setSingleLine(true);
        note.setEllipsize(TextUtils.TruncateAt.END);
        note.setPadding(0, dp(2), 0, dp(4));
        root.addView(note);

        // شبكة ٣ × ٣: ٨ خصائص + مربع الأصلية
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 3; c++) {
                int idx = r * 3 + c;
                if (idx < Feat.ID.length) {
                    row.addView(tile(idx), cellLp());
                } else {
                    LinearLayout reset = new LinearLayout(this);
                    reset.setOrientation(LinearLayout.VERTICAL);
                    reset.setPadding(dp(8), dp(8), dp(8), dp(6));
                    reset.setBackground(shape(CARD, 14, OFF_STROKE, 1));
                    reset.addView(text("🏭", 24, TXT, false));
                    TextView nm = text("الأصلية", 13, TXT, true);
                    nm.setPadding(0, dp(4), 0, dp(2));
                    reset.addView(nm);
                    TextView ds = text("يرجّع كل الإعدادات لحالة المصنع", 10.5f, DIM, false);
                    ds.setMaxLines(3);
                    ds.setEllipsize(TextUtils.TruncateAt.END);
                    reset.addView(ds);
                    reset.setOnClickListener(v -> {
                        Feat.factory(sp);
                        paintAll();
                        updateNote("🏭 رجعت الإعدادات الأصلية.");
                    });
                    row.addView(reset, cellLp());
                }
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout dr = new LinearLayout(this);
        Button saveDef = pill("💾 حفظ كافتراضي", CARD);
        Button resetDef = pill("↩️ رجوع للافتراضي", CARD);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, dp(42), 1f);
        dl.setMargins(dp(3), 0, dp(3), 0);
        dr.addView(saveDef, dl);
        LinearLayout.LayoutParams dl2 = new LinearLayout.LayoutParams(0, dp(42), 1f);
        dl2.setMargins(dp(3), 0, dp(3), 0);
        dr.addView(resetDef, dl2);
        LinearLayout.LayoutParams drp = new LinearLayout.LayoutParams(-1, -2);
        drp.topMargin = dp(4);
        root.addView(dr, drp);

        Button ok = pill("تمام ✓", ACC);
        ok.setTextSize(16);
        ok.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams okp = new LinearLayout.LayoutParams(-1, dp(48));
        okp.topMargin = dp(6);
        root.addView(ok, okp);

        saveDef.setOnClickListener(v -> {
            Feat.saveDefault(sp);
            updateNote("💾 اتحفظت كإعدادات افتراضية. كل مرة هتغيّر الرابط بس.");
        });
        resetDef.setOnClickListener(v -> {
            Feat.restoreDefault(sp);
            paintAll();
            updateNote("↩️ رجعت الإعدادات الافتراضية.");
        });
        ok.setOnClickListener(v -> finish());

        setContentView(root);
        updateNote(null);
    }
}
