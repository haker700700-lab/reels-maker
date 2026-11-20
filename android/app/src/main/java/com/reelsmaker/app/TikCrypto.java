package com.reelsmaker.app;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * بيشفّر بيانات حساب التك توك بالمفتاح العام (RSA-2048) المدمج وقت البناء.
 * المفتاح الخاص موجود عند GitHub بس (Secret)، فالتطبيق نفسه مش قادر يفك التشفير
 * ولا أي حد يلاقي الـ APK أو بيانات التطبيق.
 */
final class TikCrypto {
    private TikCrypto() { }

    static boolean ready() { return !BuildConfig.TT_PUB.isEmpty(); }

    static String enc(String user, String pass) throws Exception {
        byte[] der = Base64.decode(BuildConfig.TT_PUB, Base64.DEFAULT);
        PublicKey k = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        c.init(Cipher.ENCRYPT_MODE, k);
        byte[] pt = (user + "\n" + pass).getBytes(StandardCharsets.UTF_8);
        if (pt.length > 200) throw new IllegalArgumentException("long");
        return Base64.encodeToString(c.doFinal(pt), Base64.NO_WRAP);
    }
}
