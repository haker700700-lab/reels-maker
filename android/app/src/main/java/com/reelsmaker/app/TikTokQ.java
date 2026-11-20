package com.reelsmaker.app;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** طابور فيديوهات جاهزة للنشر: بتتفك من الـ zip وتتحفظ جوه التطبيق بس (مش في المعرض). */
final class TikTokQ {
    private TikTokQ() { }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "tiktok");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** بيفك الفيديوهات من الـ zip ويبدّل الطابور القديم. بيرجّع عدد الفيديوهات. */
    static int extract(Context c, File zip) throws IOException {
        File d = dir(c);
        File[] old = d.listFiles();
        if (old != null) for (File f : old) f.delete();
        int n = 0;
        try (ZipInputStream z = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String nm = e.getName().toLowerCase();
                if (e.isDirectory() || !nm.endsWith(".mp4")) continue;
                String base = e.getName().substring(e.getName().lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
                File out = new File(d, String.format("%03d_%s", ++n, base));
                try (OutputStream o = new FileOutputStream(out)) {
                    byte[] buf = new byte[1 << 16];
                    int r;
                    while ((r = z.read(buf)) > 0) o.write(buf, 0, r);
                }
            }
        }
        return n;
    }

    static List<File> list(Context c) {
        File[] fs = dir(c).listFiles((x, nm) -> nm.endsWith(".mp4"));
        if (fs == null) return new ArrayList<>();
        Arrays.sort(fs);
        return new ArrayList<>(Arrays.asList(fs));
    }
}
