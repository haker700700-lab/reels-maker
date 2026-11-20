package com.reelsmaker.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** اتصالات تسجيل الدخول للمنصات (OAuth). التوكنات بتروح للخزنة المشفّرة بس — مش بتتخزن ولا بتتطبع في أي مكان تاني. */
final class Social {
    static final String GRAPH = "https://graph.facebook.com/v21.0";
    static final String REDIR_META = "https://www.facebook.com/connect/login_success.html";
    static final String META_SCOPES = "pages_show_list,pages_read_engagement,pages_manage_posts,publish_video,instagram_basic,instagram_content_publish,business_management";
    static final String TT_SCOPES = "user.info.basic,video.publish,video.upload";
    static final String G_SCOPE = "https://www.googleapis.com/auth/youtube";

    private Social() { }

    /** رابط الرجوع لتيك توك: صفحة المستودع (التطبيق بيمسك الرجوع قبل ما الصفحة تتفتح). */
    static String redirGeneric() {
        return "https://github.com/" + Gh.REPO;
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    static String form(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(kv[i])).append('=').append(enc(kv[i + 1]));
        }
        return sb.toString();
    }

    /** {code, body}. من غير أي توكن GitHub (بنكلّم مواقع تانية). */
    static String[] req(String method, String url, String body, String ctype, String auth) throws IOException {
        HttpURLConnection c = Gh.open(method, url, false);
        try {
            if (auth != null) c.setRequestProperty("Authorization", auth);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", ctype);
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

    static JSONObject json(String[] x, String what) throws Exception {
        JSONObject j;
        try {
            j = new JSONObject(x[1]);
        } catch (Exception e) {
            j = new JSONObject();
        }
        int code = Integer.parseInt(x[0]);
        if (code >= 200 && code < 300) return j;
        String m = "";
        Object e = j.opt("error");
        if (e instanceof JSONObject) m = ((JSONObject) e).optString("message", "");
        else if (e != null) m = String.valueOf(e);
        if (m.isEmpty()) m = j.optString("error_description", "");
        if (m.isEmpty()) m = x[1].replace('\n', ' ');
        if (m.length() > 220) m = m.substring(0, 220);
        throw new Gh.Fatal(what + " (" + code + "): " + m);
    }

    /** بيفك parameters من الرابط: الجزء بعد # (فيسبوك) أو بعد ? (الباقي). */
    static Map<String, String> params(String url) {
        Map<String, String> m = new HashMap<>();
        int h = url.indexOf('#');
        int q = url.indexOf('?');
        String s;
        if (h >= 0) s = url.substring(h + 1);
        else if (q >= 0) s = url.substring(q + 1);
        else return m;
        for (String p : s.split("&")) {
            int i = p.indexOf('=');
            if (i <= 0) continue;
            try {
                m.put(URLDecoder.decode(p.substring(0, i), "UTF-8"), URLDecoder.decode(p.substring(i + 1), "UTF-8"));
            } catch (Exception ignored) { }
        }
        return m;
    }

    // ───────── فيسبوك + إنستجرام ─────────
    static String metaAuthUrl(String appId) {
        return "https://www.facebook.com/v21.0/dialog/oauth?" + form("client_id", appId, "redirect_uri", REDIR_META,
                "response_type", "token", "scope", META_SCOPES, "display", "touch");
    }

    /** توكن قصير ← طويل (60 يوم). توكنات الصفحات المشتقة منه ما بتنتهيش. */
    static String metaLongLived(String appId, String secret, String shortTok) throws Exception {
        String u = GRAPH + "/oauth/access_token?" + form("grant_type", "fb_exchange_token", "client_id", appId,
                "client_secret", secret, "fb_exchange_token", shortTok);
        return json(req("GET", u, null, null, null), "تبديل التوكن").getString("access_token");
    }

    /** الصفحات (وحسابات إنستجرام المربوطة بيها) اللي المستخدم أدمن عليها. */
    static JSONArray metaPages(String userToken) throws Exception {
        String u = GRAPH + "/me/accounts?" + form("fields", "id,name,access_token,instagram_business_account{id,username}",
                "limit", "100", "access_token", userToken);
        JSONObject j = json(req("GET", u, null, null, null), "قراءة الصفحات");
        JSONArray d = j.optJSONArray("data");
        return d == null ? new JSONArray() : d;
    }

    // ───────── تيك توك ─────────
    static String tiktokAuthUrl(String clientKey, String state) {
        return "https://www.tiktok.com/v2/auth/authorize/?" + form("client_key", clientKey, "scope", TT_SCOPES,
                "response_type", "code", "redirect_uri", redirGeneric(), "state", state);
    }

    static JSONObject tiktokExchange(String key, String secret, String code) throws Exception {
        String b = form("client_key", key, "client_secret", secret, "code", code, "grant_type", "authorization_code",
                "redirect_uri", redirGeneric());
        JSONObject j = json(req("POST", "https://open.tiktokapis.com/v2/oauth/token/", b, "application/x-www-form-urlencoded", null), "تيك توك");
        if (!j.has("access_token")) throw new Gh.Fatal("تيك توك: " + j.optString("error_description", j.optString("error", "فشل")));
        return j;
    }

    static String tiktokName(String access) {
        try {
            JSONObject j = json(req("GET", "https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name", null, null, "Bearer " + access), "تيك توك");
            return j.getJSONObject("data").getJSONObject("user").optString("display_name", "TikTok");
        } catch (Exception e) {
            return "TikTok";
        }
    }

    // ───────── يوتيوب (تسجيل دخول بالكود — من غير متصفح داخلي) ─────────
    static JSONObject googleDeviceStart(String clientId) throws Exception {
        return json(req("POST", "https://oauth2.googleapis.com/device/code", form("client_id", clientId, "scope", G_SCOPE),
                "application/x-www-form-urlencoded", null), "جوجل");
    }

    /** بيرجّع الرد كما هو (فيه access_token أو error=authorization_pending...). */
    static JSONObject googlePoll(String clientId, String secret, String deviceCode) throws Exception {
        String[] x = req("POST", "https://oauth2.googleapis.com/token", form("client_id", clientId, "client_secret", secret,
                "device_code", deviceCode, "grant_type", "urn:ietf:params:oauth:grant-type:device_code"), "application/x-www-form-urlencoded", null);
        try {
            return new JSONObject(x[1]);
        } catch (Exception e) {
            throw new IOException("bad reply");
        }
    }

    /** {id, name} للقناة. */
    static String[] googleChannel(String access) throws Exception {
        JSONObject j = json(req("GET", "https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true", null, null, "Bearer " + access), "يوتيوب");
        JSONArray it = j.optJSONArray("items");
        if (it == null || it.length() == 0) throw new Gh.Fatal("الحساب ده مفيهوش قناة يوتيوب.");
        JSONObject c = it.getJSONObject(0);
        return new String[]{c.getString("id"), c.getJSONObject("snippet").optString("title", "YouTube")};
    }
}
