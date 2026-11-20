package com.reelsmaker.app;

import android.content.SharedPreferences;

/** تعريف الخصائص (المربعات) والقيم الافتراضية — مشترك بين الشاشة الرئيسية وشاشة الخصائص. */
final class Feat {
    // الإعدادات الأصلية (المصنع)
    static final String F_N = "5", F_MIN = "15", F_MAX = "25", F_RES = "1080";
    static final String F_TEXT = "لمشاهدة وتحميل كاملا الرابط في البايو الملف الشخصي";
    static final boolean F_REV = false, F_MUS = false;
    // موسيقى الخلفية: مستوى الصوت (8 قياسية • 15 وسط • 25 عالية) + رابط موسيقى + أغاني الفيديو الأساسية (تلقائي) + وضع الريأكشن
    static final String F_MVOL = "8", F_MURL = "";
    static final boolean F_RSONG = true, F_REACT = false;

    // رقم كل خاصية (لازم يطابق ترتيب ID)
    static final int CROP = 0, BEST = 1, FACE = 2;

    static final String[] ID = {"crop", "best", "face", "enhance", "orig", "denoise", "fade", "musicfile"};
    static final String[] ICON = {"✂️", "⭐", "👤", "✨", "🔊", "🎙️", "🎞️", "🎼"};
    static final String[] NAME = {"قص بالطول", "أحسن اللحظات", "تتبّع الوجوه", "تحسين الصورة",
            "صوت الفيديو", "تنقية الصوت", "تلاشي ناعم", "موسيقى منفصلة"};
    static final String[] DESC = {
            "الفيديو يملا الشاشة كلها بدون خلفية مغبشة",
            "يختار الأقوى: حركة ووجوه وصوت",
            "القص يمشي مع وش الشخص",
            "ألوان وحدة أوضح شوية",
            "سيب الصوت الأصلي شغّال",
            "يقلل الدوشة من الكلام",
            "بداية ونهاية بتدخل وتختفي بنعومة",
            "ملف موسيقى لوحده جنب الريلز"};
    static final boolean[] DEF = {true, true, true, true, true, false, false, false};

    static final String[] STR_KEYS = {"n", "min", "max", "res", "text", "mvol", "murl"};
    static final String[] BOOL_KEYS = {"rev", "mus", "rsong", "react"};

    private Feat() { }

    /** القيمة الحالية، وإلا الافتراضي المحفوظ، وإلا الأصلي. */
    static String str(SharedPreferences sp, String k, String factory) {
        return sp.getString(k, sp.getString("d_" + k, factory));
    }

    static boolean bool(SharedPreferences sp, String k, boolean factory) {
        return sp.getBoolean(k, sp.getBoolean("d_" + k, factory));
    }

    static boolean get(SharedPreferences sp, int i) {
        return sp.getBoolean("f_" + ID[i], sp.getBoolean("d_f_" + ID[i], DEF[i]));
    }

    static int enabledCount(SharedPreferences sp) {
        int c = 0;
        for (int i = 0; i < ID.length; i++) {
            if (get(sp, i)) c++;
        }
        return c;
    }

    /** النص اللي بيتبعت للسيرفر: crop,best,... أو none. */
    static String string(SharedPreferences sp) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ID.length; i++) {
            if (get(sp, i)) {
                if (sb.length() > 0) sb.append(',');
                sb.append(ID[i]);
            }
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    /** حفظ كل اللي على الشاشة (وقت الحفظ) كإعدادات افتراضية. */
    static void saveDefault(SharedPreferences sp) {
        SharedPreferences.Editor e = sp.edit();
        for (String k : STR_KEYS) {
            if (sp.contains(k)) e.putString("d_" + k, sp.getString(k, ""));
        }
        for (String k : BOOL_KEYS) {
            if (sp.contains(k)) e.putBoolean("d_" + k, sp.getBoolean(k, false));
        }
        for (int i = 0; i < ID.length; i++) {
            e.putBoolean("d_f_" + ID[i], get(sp, i));
        }
        e.apply();
    }

    /** رجوع للافتراضي المحفوظ (لو مفيش، بيرجع للأصلي). */
    static void restoreDefault(SharedPreferences sp) {
        SharedPreferences.Editor e = sp.edit();
        for (String k : STR_KEYS) {
            if (sp.contains("d_" + k)) e.putString(k, sp.getString("d_" + k, ""));
            else e.remove(k);
        }
        for (String k : BOOL_KEYS) {
            if (sp.contains("d_" + k)) e.putBoolean(k, sp.getBoolean("d_" + k, false));
            else e.remove(k);
        }
        for (int i = 0; i < ID.length; i++) {
            String k = "f_" + ID[i];
            if (sp.contains("d_" + k)) e.putBoolean(k, sp.getBoolean("d_" + k, DEF[i]));
            else e.remove(k);
        }
        e.apply();
    }

    /** الإعدادات الأصلية: بيمسح الحالي والافتراضي المحفوظ. */
    static void factory(SharedPreferences sp) {
        SharedPreferences.Editor e = sp.edit();
        for (String k : STR_KEYS) e.remove(k).remove("d_" + k);
        for (String k : BOOL_KEYS) e.remove(k).remove("d_" + k);
        for (int i = 0; i < ID.length; i++) e.remove("f_" + ID[i]).remove("d_f_" + ID[i]);
        e.apply();
    }
}
