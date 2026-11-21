package com.reelsmaker.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** نظام التصميم المشترك: ألوان هادية مريحة للعين + مكوّنات خفيفة (من غير أي مكتبات خارجية). */
final class Ui {
    private Ui() { }

    static final int BG = 0xFF0F1B22, CARD = 0xFF182933, CARD2 = 0xFF203543, STROKE = 0xFF263E4B,
            ACC = 0xFF38B2A5, ACC2 = 0xFF8AB4F8, WARM = 0xFFF2B866, TXT = 0xFFE9F1F4, DIM = 0xFF93A7B1,
            OK = 0xFF6FCF97, DANGER = 0xFF4A2E3A, ON_FILL = 0xFF1B4547, HEART = 0xFFFB7185;

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static GradientDrawable shape(Context c, int fill, int r, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, r));
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), stroke);
        return g;
    }

    static TextView text(Context c, String s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** كارت: خلفية مدوّرة هادية + حد خفيف. */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        l.setBackground(shape(c, CARD, 16, STROKE, 1));
        return l;
    }

    /** أيقونة داخل مربع مدوّر بلون خفيف. */
    static TextView iconChip(Context c, String emoji, int sizeDp, int tint) {
        TextView t = new TextView(c);
        t.setText(emoji);
        t.setTextSize(sizeDp * 0.45f);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setBackground(shape(c, (tint & 0x00FFFFFF) | 0x2A000000, sizeDp / 3, 0, 0));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }

    /** مفتاح تشغيل/إيقاف بألوان هادية. */
    static Switch sw(Context c, boolean on) {
        Switch s = new Switch(c);
        s.setChecked(on);
        s.setShowText(false);
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{ACC, 0xFF8497A2}));
        s.setTrackTintList(new ColorStateList(st, new int[]{0x8838B2A5, 0xFF2B4250}));
        return s;
    }

    /** صف خاصية: أيقونة + اسم + شرح قصير + مفتاح (تشغيل/إيقاف). الضغط على الصف كله بيقلب المفتاح. */
    static LinearLayout toggleRow(Context c, String icon, String title, String desc, boolean on,
                                  CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 5), 0, dp(c, 5));
        row.addView(iconChip(c, icon, 34, ACC));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(c, 10), 0, dp(c, 8), 0);
        col.addView(text(c, title, 13.5f, TXT, true));
        if (desc != null && !desc.isEmpty()) {
            TextView d = text(c, desc, 11, DIM, false);
            d.setMaxLines(3);
            col.addView(d);
        }
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        final Switch s = sw(c, on);
        s.setOnCheckedChangeListener(l);
        row.addView(s, new LinearLayout.LayoutParams(-2, -2));
        row.setTag(s);
        row.setOnClickListener(v -> s.toggle());
        return row;
    }

    static Button pill(Context c, String s, int fill) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TXT);
        b.setTextSize(13);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 6), 0, dp(c, 6), 0);
        b.setBackground(shape(c, fill, 12, 0, 0));
        b.setStateListAnimator(null);
        return b;
    }

    /** شعار التطبيق (نفس شكل الأيقونة) مرسوم بالكود — خفيف ومن غير صور. */
    static class LogoView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        LogoView(Context c) { super(c); }

        @Override protected void onDraw(Canvas cv) {
            float s = Math.min(getWidth(), getHeight());
            float k = s / 108f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, s, s, 0xFF0F766E, 0xFF312E81, Shader.TileMode.CLAMP));
            cv.drawRoundRect(new RectF(0, 0, s, s), 24 * k, 24 * k, p);
            p.setShader(null);
            cv.save();
            cv.translate(54 * k, 54 * k);
            cv.scale(1.18f, 1.18f);
            cv.translate(-54 * k, -54 * k);
            p.setColor(0xFFF8FAFC);
            cv.drawRoundRect(new RectF(36 * k, 24 * k, 72 * k, 84 * k), 7 * k, 7 * k, p);
            p.setColor(0xFF0F9D8F);
            Path tri = new Path();
            tri.moveTo(49 * k, 44 * k);
            tri.lineTo(49 * k, 62 * k);
            tri.lineTo(63.5f * k, 53 * k);
            tri.close();
            cv.drawPath(tri, p);
            p.setColor(0xFFFBBF24);
            float[] st = {74, 17, 76.2f, 23.8f, 83, 26, 76.2f, 28.2f, 74, 35, 71.8f, 28.2f, 65, 26, 71.8f, 23.8f};
            Path star = new Path();
            star.moveTo(st[0] * k, st[1] * k);
            for (int i = 2; i < st.length; i += 2) star.lineTo(st[i] * k, st[i + 1] * k);
            star.close();
            cv.drawPath(star, p);
            p.setColor(0xFFCBD5E1);
            cv.drawRoundRect(new RectF(41 * k, 73 * k, 67 * k, 76.2f * k), 1.6f * k, 1.6f * k, p);
            p.setColor(0xFF0F9D8F);
            cv.drawRoundRect(new RectF(41 * k, 73 * k, 55 * k, 76.2f * k), 1.6f * k, 1.6f * k, p);
            cv.restore();
        }
    }
}
