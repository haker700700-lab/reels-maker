package com.reelsmaker.app;

import android.content.Context;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** طابور فيديوهات فيسبوك، مستقل عن طابور تيك توك. */
final class FacebookQ {
    private FacebookQ() { }
    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "facebook");
        if (!d.exists()) d.mkdirs();
        return d;
    }
    static int extract(Context c, File zip) throws IOException {
        File d = dir(c); File[] old = d.listFiles();
        if (old != null) for (File f : old) f.delete();
        int n = 0;
        try (ZipInputStream z = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String nm = e.getName().toLowerCase(Locale.ROOT);
                if (e.isDirectory() || !nm.endsWith(".mp4")) continue;
                String base = e.getName().substring(e.getName().lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
                File out = new File(d, String.format(Locale.US, "%03d_%s", ++n, base));
                try (OutputStream o = new FileOutputStream(out)) {
                    byte[] buf = new byte[1 << 16]; int r;
                    while ((r = z.read(buf)) > 0) o.write(buf, 0, r);
                }
            }
        }
        return n;
    }
    static List<File> list(Context c) {
        File[] fs = dir(c).listFiles((x, nm) -> nm.toLowerCase(Locale.ROOT).endsWith(".mp4"));
        if (fs == null) return new ArrayList<>();
        Arrays.sort(fs); return new ArrayList<>(Arrays.asList(fs));
    }
}
