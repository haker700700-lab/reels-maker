package com.reelsmaker.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** مساعد بسيط للتخاطب مع GitHub API (من غير أي مكتبات خارجية). */
final class Gh {
    static final String API = "https://api.github.com";
    static final String REPO = BuildConfig.APP_REPO;
    static final String TOK = BuildConfig.APP_TOKEN;

    /** خطأ نهائي: مفيش فايدة من إعادة المحاولة. */
    static class Fatal extends Exception {
        Fatal(String m) { super(m); }
    }

    /** خطأ مؤقت (نت/سيرفر مشغول): بنعيد المحاولة. */
    static class Transient extends Exception {
        Transient(String m) { super(m); }
    }

    private Gh() { }

    static HttpURLConnection open(String method, String url, boolean auth) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(40000);
        c.setRequestProperty("User-Agent", "ReelsMaker-App");
        if (auth) {
            c.setRequestProperty("Authorization", "Bearer " + TOK);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        }
        return c;
    }

    /** بيرجّع {code, body}. أخطاء الشبكة بتطلع IOException. */
    static String[] http(String method, String url, String body) throws IOException {
        HttpURLConnection c = open(method, url, true);
        try {
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = c.getOutputStream()) {
                    o.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String ln;
                    while ((ln = r.readLine()) != null) sb.append(ln).append('\n');
                }
            }
            return new String[]{String.valueOf(code), sb.toString()};
        } finally {
            c.disconnect();
        }
    }

    static String msgOf(String body) {
        try {
            return new JSONObject(body).optString("message", "").trim();
        } catch (Exception e) {
            return body == null ? "" : body.replace('\n', ' ').trim();
        }
    }

    /** بيفحص كود الرد: 2xx تمام، أخطاء الحساب/الصلاحيات نهائية وبتظهر بسببها الحقيقي، الباقي مؤقت. */
    static void check(String[] x) throws Fatal, Transient {
        int code;
        try {
            code = Integer.parseInt(x[0]);
        } catch (NumberFormatException e) {
            throw new Transient("bad code");
        }
        if (code >= 200 && code < 300) return;
        String m = msgOf(x[1]);
        if (code == 401) throw new Fatal("GitHub مش قابل التوكن (منتهي أو اتلغى). شغّل أمر التحديث وهيعمل توكن جديد: RESET_TOKEN=1 bash ~/reels-maker-main/termux-update.sh");
        if (code == 403) {
            String l = m.toLowerCase();
            if (l.contains("rate limit") || l.contains("abuse")) throw new Transient("busy 403 " + m);
            throw new Fatal("GitHub رفض الطلب (403): " + m + " — التوكن ناقصه صلاحية «Actions: Read and write» أو مش مختار مستودع reels-maker. "
                    + "اعمل توكن جديد: RESET_TOKEN=1 bash ~/reels-maker-main/termux-update.sh");
        }
        if (code == 404) throw new Fatal("مش لاقي الخدمة (404): " + m + " — اتأكد إن المستودع " + REPO + " وملف reels.yml موجودين، وإن التوكن مختار المستودع ده.");
        if (code == 429 || code >= 500 || code == 408) throw new Transient("busy " + code);
        throw new Fatal("خطأ " + code + ": " + m);
    }

    /** بيدوّر على تشغيل بنفس رقم العملية بتاعنا (كل عملية ليها رقم مختلف، فمفيش خلط). 0 = لسه ما ظهرش. */
    static long findRun(String job) throws Exception {
        String[] x = http("GET", API + "/repos/" + REPO + "/actions/workflows/reels.yml/runs?per_page=30", null);
        check(x);
        JSONArray a = new JSONObject(x[1]).getJSONArray("workflow_runs");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            if (("reels " + job).equals(o.optString("display_title"))) return o.getLong("id");
        }
        return 0;
    }

    static JSONObject getJson(String path) throws Exception {
        String[] x = http("GET", API + "/repos/" + REPO + path, null);
        check(x);
        return new JSONObject(x[1]);
    }
}
