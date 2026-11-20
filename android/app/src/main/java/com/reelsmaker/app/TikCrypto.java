package com.reelsmaker.app;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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

    /** تشفير هجين لنص كبير (كوكيز): مفتاح AES عشوائي مشفّر بالـ RSA + AES-GCM للبيانات. */
    static String encBlob(String plain) throws Exception {
        byte[] der = Base64.decode(BuildConfig.TT_PUB, Base64.DEFAULT);
        PublicKey k = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        byte[] key = new byte[32], iv = new byte[12];
        SecureRandom r = new SecureRandom();
        r.nextBytes(key);
        r.nextBytes(iv);
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, k);
        byte[] wrapped = rsa.doFinal(key);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = aes.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] all = new byte[wrapped.length + iv.length + ct.length];
        System.arraycopy(wrapped, 0, all, 0, wrapped.length);
        System.arraycopy(iv, 0, all, wrapped.length, iv.length);
        System.arraycopy(ct, 0, all, wrapped.length + iv.length, ct.length);
        return Base64.encodeToString(all, Base64.NO_WRAP);
    }
}
