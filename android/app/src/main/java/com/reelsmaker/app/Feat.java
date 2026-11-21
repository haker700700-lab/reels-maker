package com.reelsmaker.app;

import android.content.SharedPreferences;

import org.json.JSONObject;

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

    // ───── خصائص v5 (الاستوديو): الوضع • الفلاتر • اللوجو • النص • الصوت ─────
    // a_mode: best (الأفضل — الافتراضي) / pro / custom (أنا أختار)
    static final String[] ADV_STR = {"a_mode", "a_look", "a_look_i", "a_extras", "a_logo_url", "a_logo_pos", "a_logo_size",
            "a_logo_op", "a_tpos", "a_tstyle", "a_tsize", "a_tfont", "a_tcolor", "a_tanim", "a_cred", "a_target", "a_vol"};
    static final String[] ADV_BOOL = {"a_progress", "a_zoom", "a_voice", "a_comp", "a_bass", "a_treble", "a_duck", "a_loud",
            "a_denoise", "a_ledger"};

    static final String[] STR_KEYS = cat(new String[]{"n", "min", "max", "res", "text", "mvol", "murl"}, ADV_STR);
    static final String[] BOOL_KEYS = cat(new String[]{"rev", "mus", "rsong", "react"}, ADV_BOOL);

    static String[] cat(String[] a, String[] b) {
        String[] o = new String[a.length + b.length];
        System.arraycopy(a, 0, o, 0, a.length);
        System.arraycopy(b, 0, o, a.length, b.length);
        return o;
    }

    /** القيمة الأصلية لمفتاح من مفاتيح الاستوديو. */
    static String advDef(String k) {
        switch (k) {
            case "a_mode": return "best";
            case "a_look": return "none";
            case "a_look_i": return "70";
            case "a_logo_pos": return "tr";
            case "a_logo_size": return "18";
            case "a_logo_op": return "90";
            case "a_tpos": return "أسفل";
            case "a_tstyle": return "صندوق داكن شفاف";
            case "a_tsize": return "متوسط";
            case "a_tfont": return "Tajawal";
            case "a_tanim": return "none";
            case "a_target": return "std";
            case "a_vol": return "100";
            default: return "";
        }
    }

    static boolean advBoolDef(String k) {
        return "a_loud".equals(k) || "a_ledger".equals(k);
    }

    /** JSON مدخل adv اللي بيتبعت للسيرفر (كل خصائص v5 + إعدادات النشر على X). demo = فيديو تجريبي. */
    static String adv(SharedPreferences sp, boolean demo, boolean xOn) {
        try {
            JSONObject o = new JSONObject();
            String mode = str(sp, "a_mode", advDef("a_mode"));
            o.put("mode", "custom".equals(mode) ? "" : mode);
            o.put("look", str(sp, "a_look", advDef("a_look")));
            o.put("look_i", str(sp, "a_look_i", advDef("a_look_i")));
            o.put("extras", str(sp, "a_extras", ""));
            o.put("logo_url", str(sp, "a_logo_url", "").trim());
            o.put("logo_pos", str(sp, "a_logo_pos", advDef("a_logo_pos")));
            o.put("logo_size", str(sp, "a_logo_size", advDef("a_logo_size")));
            o.put("logo_op", str(sp, "a_logo_op", advDef("a_logo_op")));
            o.put("tpos", str(sp, "a_tpos", advDef("a_tpos")));
            o.put("tstyle", str(sp, "a_tstyle", advDef("a_tstyle")));
            o.put("tsize", str(sp, "a_tsize", advDef("a_tsize")));
            o.put("tfont", str(sp, "a_tfont", advDef("a_tfont")));
            o.put("tcolor", str(sp, "a_tcolor", ""));
            o.put("text_anim", str(sp, "a_tanim", advDef("a_tanim")));
            o.put("credit", str(sp, "a_cred", "").trim());
            o.put("a_target", str(sp, "a_target", advDef("a_target")));
            o.put("a_vol", str(sp, "a_vol", advDef("a_vol")));
            o.put("progress", bool(sp, "a_progress", false));
            o.put("zoom_slow", bool(sp, "a_zoom", false));
            for (String k : new String[]{"voice", "comp", "bass", "treble", "duck", "loud", "denoise"}) {
                o.put("a_" + k, bool(sp, "a_" + k, advBoolDef("a_" + k)));
            }
            o.put("ledger", bool(sp, "a_ledger", true));
            if (demo) o.put("demo", true);
            o.put("x_on", xOn && !demo);
            o.put("x_caption", sp.getString("x_caption", ""));
            o.put("x_accs", xOn && !demo ? sp.getString("x_accs", "[]") : "[]");
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

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
