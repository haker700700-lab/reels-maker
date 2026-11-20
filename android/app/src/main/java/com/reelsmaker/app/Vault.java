package com.reelsmaker.app;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * الخزنة المشفّرة: حسابات السوشيال + جدول النشر. مخزّنة في فرع «vault» جوه مستودعك (عام) لكن مشفّرة AES-256-GCM،
 * والمفتاح بيتحسب من التوكن بتاعك (الموجود جوه تطبيقك وفي Secret المستودع) — فمحدش غيرك يقدر يفكها.
 * نفس الصيغة اللي بيفكها بيها السيرفر (cloud/vault.py): base64( nonce(12) + ciphertext + tag ).
 */
final class Vault {
    static final String BRANCH = "vault", PATH = "v.bin";
    private static String sha = null;
    static JSONObject cache = null;

    static class Conflict extends Exception {
        Conflict() { super("conflict"); }
    }

    interface Mut {
        void apply(JSONObject d) throws Exception;
    }

    private Vault() { }

    // ───────── تشفير ─────────
    private static byte[] key() throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(("reels-vault-v1|" + Gh.TOK).getBytes("UTF-8"));
    }

    static String seal(JSONObject o) throws Exception {
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, nonce));
        byte[] ct = c.doFinal(o.toString().getBytes("UTF-8"));
        byte[] all = new byte[12 + ct.length];
        System.arraycopy(nonce, 0, all, 0, 12);
        System.arraycopy(ct, 0, all, 12, ct.length);
        return Base64.encodeToString(all, Base64.NO_WRAP);
    }

    static JSONObject unseal(String b64) throws Exception {
        byte[] all = Base64.decode(b64.trim(), Base64.DEFAULT);
        if (all.length < 29) throw new Gh.Fatal("الخزنة تالفة.");
        byte[] nonce = new byte[12];
        System.arraycopy(all, 0, nonce, 0, 12);
        byte[] ct = new byte[all.length - 12];
        System.arraycopy(all, 12, ct, 0, ct.length);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, nonce));
        return new JSONObject(new String(c.doFinal(ct), "UTF-8"));
    }

    static JSONObject empty() throws Exception {
        return new JSONObject().put("v", 1).put("keys", new JSONObject()).put("accounts", new JSONArray())
                .put("queue", new JSONArray()).put("settings", new JSONObject());
    }

    // ───────── مساعدات للبيانات ─────────
    static JSONObject obj(JSONObject p, String k) throws Exception {
        JSONObject o = p.optJSONObject(k);
        if (o == null) {
            o = new JSONObject();
            p.put(k, o);
        }
        return o;
    }

    static JSONArray arr(JSONObject p, String k) throws Exception {
        JSONArray a = p.optJSONArray(k);
        if (a == null) {
            a = new JSONArray();
            p.put(k, a);
        }
        return a;
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** بيضيف حساب جديد أو بيحدّث بيانات حساب موجود (نفس المنصة ونفس المعرّف) من غير ما يمس إعداداته. */
    static JSONObject upsert(JSONObject d, JSONObject in) throws Exception {
        JSONArray a = arr(d, "accounts");
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            if (o.optString("platform").equals(in.optString("platform")) && o.optString("ext_id").equals(in.optString("ext_id"))) {
                java.util.Iterator<String> it = in.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    o.put(k, in.get(k));
                }
                return o;
            }
        }
        JSONObject n = new JSONObject();
        n.put("id", newId()).put("desc", "").put("start_h", 0).put("step_h", 1).put("enabled", true);
        java.util.Iterator<String> it = in.keys();
        while (it.hasNext()) {
            String k = it.next();
            n.put(k, in.get(k));
        }
        a.put(n);
        return n;
    }

    // ───────── قراءة وكتابة ─────────
    private static String contentsUrl() {
        return Gh.API + "/repos/" + Gh.REPO + "/contents/" + PATH;
    }

    static synchronized JSONObject load() throws Exception {
        String[] x = Gh.http("GET", contentsUrl() + "?ref=" + BRANCH, null);
        int code = Integer.parseInt(x[0]);
        if (code == 404) {
            sha = null;
            cache = empty();
            return cache;
        }
        if (code == 401) throw new Gh.Fatal("التوكن منتهي. شغّل: RESET_TOKEN=1 bash termux-update.sh");
        if (code != 200) throw new java.io.IOException("vault " + code);
        JSONObject j = new JSONObject(x[1]);
        sha = j.optString("sha", null);
        String inner = new String(Base64.decode(j.getString("content"), Base64.DEFAULT), "UTF-8");
        try {
            cache = unseal(inner);
        } catch (Gh.Fatal e) {
            throw e;
        } catch (Exception e) {
            throw new Gh.Fatal("الخزنة مش بتتفك: غالبًا التوكن اتغيّر. اربط الحسابات تاني.");
        }
        return cache;
    }

    private static final String NO_WRITE = "التوكن مالوش صلاحية الكتابة (Contents). شغّل في تيرمكس: RESET_TOKEN=1 bash termux-update.sh واختار Contents: Read and write.";

    private static void ensureBranch() throws Exception {
        String[] r = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO + "/git/ref/heads/" + BRANCH, null);
        if ("200".equals(r[0])) return;
        String[] m = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO + "/git/ref/heads/main", null);
        if (!"200".equals(m[0])) throw new Gh.Fatal(NO_WRITE);
        String msha = new JSONObject(m[1]).getJSONObject("object").getString("sha");
        String[] c = Gh.http("POST", Gh.API + "/repos/" + Gh.REPO + "/git/refs",
                new JSONObject().put("ref", "refs/heads/" + BRANCH).put("sha", msha).toString());
        int code = Integer.parseInt(c[0]);
        if (code == 403 || code == 404) throw new Gh.Fatal(NO_WRITE);
        if (code != 201 && code != 422) throw new java.io.IOException("branch " + code);
    }

    static synchronized void save(JSONObject d) throws Exception, Conflict {
        if (sha == null) ensureBranch();
        JSONObject body = new JSONObject().put("message", "vault").put("branch", BRANCH)
                .put("content", Base64.encodeToString(seal(d).getBytes("UTF-8"), Base64.NO_WRAP));
        if (sha != null) body.put("sha", sha);
        String[] x = Gh.http("PUT", contentsUrl(), body.toString());
        int code = Integer.parseInt(x[0]);
        if (code == 409 || code == 422) throw new Conflict();
        if (code == 403 || code == 404) throw new Gh.Fatal(NO_WRITE);
        if (code != 200 && code != 201) throw new java.io.IOException("vault write " + code);
        sha = new JSONObject(x[1]).getJSONObject("content").getString("sha");
        cache = d;
    }

    /** قراءة ← تعديل ← كتابة. لو السيرفر كتب في نفس اللحظة بيعيد المحاولة على أحدث نسخة. */
    static JSONObject mutate(Mut m) throws Exception {
        for (int i = 0; i < 5; i++) {
            JSONObject d = load();
            m.apply(d);
            try {
                save(d);
                return d;
            } catch (Conflict c) {
                Thread.sleep(700L * (i + 1));
            }
        }
        throw new java.io.IOException("الخزنة مشغولة، جرّب تاني.");
    }
}
