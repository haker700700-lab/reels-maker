package com.reelsmaker.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** أدوات واجهة مشتركة للشاشات الجديدة (نفس ألوان وشكل باقي التطبيق). */
final class Ui {
    static final int BG = 0xFF0E1224, CARD = 0xFF1A2040, ACC = 0xFF7C5CFF, ACC2 = 0xFF2DD4BF, TXT = 0xFFF1F3FF, DIM = 0xFF8E95B8;
    static final int OK = 0xFF34D399, BAD = 0xFFF87171, WARN = 0xFFFBBF24, STROKE = 0xFF2A3156;

    private Ui() { }

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
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    static Button pill(Context c, String s, int fill) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TXT);
        b.setTextSize(13);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(shape(c, fill, 12, 0, 0));
        b.setStateListAnimator(null);
        return b;
    }

    static EditText edit(Context c, String hint, String val, int type) {
        EditText e = new EditText(c);
        e.setText(val);
        e.setHint(hint);
        e.setInputType(type);
        e.setTextColor(TXT);
        e.setHintTextColor(DIM);
        e.setTextSize(14);
        e.setBackground(shape(c, CARD, 12, 0, 0));
        e.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        return e;
    }

    static CheckBox check(Context c, String s, boolean v) {
        CheckBox b = new CheckBox(c);
        b.setText(s);
        b.setChecked(v);
        b.setTextColor(TXT);
        b.setTextSize(13);
        b.setButtonTintList(android.content.res.ColorStateList.valueOf(ACC2));
        return b;
    }

    static LinearLayout.LayoutParams lp(int w, int h, float wt) {
        return new LinearLayout.LayoutParams(w, h, wt);
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, float wt, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, wt);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    /** كارت رأسي بخلفية مدوّرة. */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(shape(c, CARD, 14, STROKE, 1));
        l.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        return l;
    }

    /** نافذة داكنة (متناسقة مع شكل التطبيق). */
    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }
}
