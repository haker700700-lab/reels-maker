package com.reelsmaker.app;
import android.net.Uri;
import java.io.File;
/** URI helper for Facebook queue videos served by the shared provider. */
final class FacebookVideoUri {
    private FacebookVideoUri() { }
    static Uri forFile(File f) { return Uri.parse("content://" + VideoProvider.AUTH + "/fb_" + f.getName()); }
}
