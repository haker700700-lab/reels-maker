package com.reelsmaker.app;

import android.Manifest;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * خدمة أمامية: بتبعت الطلب وتتابع السيرفر وتنزّل الملف المضغوط.
 * بتكمّل لوحدها لو الشاشة اتقفلت أو التطبيق اتقفل، وبتستنى النت لو فصل وتكمّل التنزيل من حيث وقف.
 * كل حالة العملية محفوظة في SharedPreferences، فلو الخدمة اتقتلت بتكمّل من نفس النقطة.
 */
public class JobService extends Service {
    static volatile boolean alive = false;
    static final int NID = 71, NID_DONE = 72;
    static final String CH_RUN = "reels_run", CH_DONE = "reels_done";

    SharedPreferences sp;
    NotificationManager nm;
    PowerManager.WakeLock wake;
    WifiManager.WifiLock wifiLock;
    volatile Thread worker;
    volatile boolean stopping = false;
    long lastNotif = 0;

    interface Net<T> {
        T call() throws Exception;
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getSharedPreferences("reels", MODE_PRIVATE);
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel a = new NotificationChannel(CH_RUN, "متابعة الريلز", NotificationManager.IMPORTANCE_LOW);
            a.setShowBadge(false);
            nm.createNotificationChannel(a);
            NotificationChannel b = new NotificationChannel(CH_DONE, "نتيجة الريلز", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(b);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        alive = true;
        try {
            Notification n = notif("صانع الريلز شغّال في الخلفية...", 0, true);
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NID, n);
            }
        } catch (Throwable t) {
            // لو أندرويد رفض الخدمة الأمامية، هنكمّل بأحسن ما نقدر
        }
        String st = sp.getString("stage", "");
        boolean pending = "NEW".equals(st) || "RUNNING".equals(st);
        if (!pending) {
            finish();
            return START_NOT_STICKY;
        }
        if (worker == null || !worker.isAlive()) {
            stopping = false;
            worker = new Thread(this::work, "reels-job");
            worker.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        alive = false;
        stopping = true;
        Thread t = worker;
        if (t != null) t.interrupt();
        releaseLocks();
        super.onDestroy();
    }

    // ───────── إشعارات وحالة ─────────
    Notification notif(String text, int pct, boolean indeterminate) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_RUN) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("🎬 صانع الريلز")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, Math.max(0, Math.min(100, pct)), indeterminate)
                .setContentIntent(openApp());
        return b.build();
    }

    PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    void status(String msg, int pct, boolean indeterminate) {
        sp.edit().putString("msg", msg).putInt("pct", pct).apply();
        long now = System.currentTimeMillis();
        if (now - lastNotif < 1500) return;
        lastNotif = now;
        try {
            if (nm != null) nm.notify(NID, notif(msg, pct, indeterminate));
        } catch (Throwable ignored) { }
    }

    void status(String msg) { status(msg, sp.getInt("pct", 0), true); }

    void notifyDone(String text) {
        try {
            if (nm == null) return;
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_DONE) : new Notification.Builder(this);
            Intent v = sp.getInt("tt_queued", 0) > 0 ? new Intent(this, TikTokActivity.class) : new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            v.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("🎬 صانع الريلز")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(this, 1, v, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            nm.notify(NID_DONE, b.build());
        } catch (Throwable ignored) { }
    }

    void acquireLocks() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && wake == null) {
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reels:job");
                wake.acquire(4 * 60 * 60 * 1000L);
            }
        } catch (Throwable ignored) { }
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null && wifiLock == null) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "reels:wifi");
                wifiLock.acquire();
            }
        } catch (Throwable ignored) { }
    }

    void releaseLocks() {
        try {
            if (wake != null && wake.isHeld()) wake.release();
        } catch (Throwable ignored) { }
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Throwable ignored) { }
        wake = null;
        wifiLock = null;
    }

    void finish() {
        try {
            stopForeground(true);
        } catch (Throwable ignored) { }
        stopSelf();
    }

    // ───────── منطق العملية ─────────
    /** بيعيد المحاولة مع انتظار متزايد لو النت فصل أو السيرفر مشغول؛ الأخطاء النهائية بتطلع. */
    <T> T retry(Net<T> f) throws Exception {
        long wait = 3000;
        while (true) {
            if (stopping) throw new InterruptedException();
            try {
                return f.call();
            } catch (Gh.Fatal e) {
                throw e;
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                status("📶 مفيش اتصال حاليًا — الشغل مكمّل على السيرفر، وهكمّل أول ما النت يرجع");
                Thread.sleep(wait);
                wait = Math.min(wait * 2, 30000);
            }
        }
    }

    void work() {
        acquireLocks();
        try {
            final String job = sp.getString("job", "");
            final String body = sp.getString("body", "");
            String stage = sp.getString("stage", "");
            if (job.isEmpty()) {
                finish();
                return;
            }
            long t0 = sp.getLong("t0", 0);
            if (t0 == 0) {
                t0 = System.currentTimeMillis();
                sp.edit().putLong("t0", t0).apply();
            }
            if ("NEW".equals(stage)) {
                status("ببعت الطلب للسيرفر...", 0, true);
                retry(() -> {
                    if (Gh.findRun(job) != 0) return Boolean.TRUE;
                    if (!sp.getBoolean("posted", false)) {
                        String[] d = Gh.http("POST", Gh.API + "/repos/" + Gh.REPO + "/actions/workflows/reels.yml/dispatches", body);
                        Gh.check(d);
                        sp.edit().putBoolean("posted", true).apply();
                    }
                    return Boolean.TRUE;
                });
                sp.edit().putString("stage", "RUNNING").apply();
            }

            // استنى ظهور التشغيل
            long runId = 0;
            long until = System.currentTimeMillis() + 8 * 60 * 1000L;
            while (runId == 0) {
                runId = retry(() -> Gh.findRun(job));
                if (runId != 0) break;
                if (System.currentTimeMillis() > until) throw new Gh.Fatal("السيرفر ما بدأش الشغل — جرّب تاني.");
                status("⏳ السيرفر بيبدأ الشغل...");
                Thread.sleep(3000);
            }

            // تابع لحد ما يخلص
            final long rid = runId;
            String conclusion;
            while (true) {
                JSONObject j = retry(() -> Gh.getJson("/actions/runs/" + rid));
                if ("completed".equals(j.optString("status"))) {
                    conclusion = j.optString("conclusion");
                    break;
                }
                long mins = (System.currentTimeMillis() - sp.getLong("t0", System.currentTimeMillis())) / 60000;
                if (mins > 120) throw new Gh.Fatal("الشغل طوّل أوي.");
                status("☁️ السيرفر شغّال... (" + mins + " د) — الوقت المتوقع 3 لـ 8 دقايق، تقدر تقفل الشاشة أو التطبيق");
                Thread.sleep(7000);
            }
            if (!"success".equals(conclusion)) throw new Gh.Fatal(failReason(job, conclusion));

            if (sp.getBoolean("job_nodl", false)) {
                String r = ttResult(job);
                sp.edit().putString("stage", "DONE").putString("msg", "✅ خلصنا (من غير تنزيل على الجهاز) — " + r).putInt("pct", 100).apply();
                notifyDone(r);
                return;
            }
            String name = download("reels-" + job);
            int q = sp.getInt("tt_queued", 0);
            String ttNote = q > 0 ? " — " + q + " فيديو جاهز للنشر على التك توك" : "";
            String where = name.isEmpty() ? "جوه التطبيق" : "الملف في «التنزيلات»: " + name;
            sp.edit().putString("stage", "DONE").putString("msg", "✅ خلصنا! " + where + ttNote).putInt("pct", 100).apply();
            notifyDone("الريلز جاهزة" + ttNote);
        } catch (InterruptedException e) {
            // الخدمة اتوقفت: الحالة محفوظة وهنكمّل لما تشتغل تاني
        } catch (Gh.Fatal e) {
            sp.edit().putString("stage", "FAILED").putString("msg", "❌ " + e.getMessage()).apply();
            notifyDone("حصلت مشكلة — افتح التطبيق");
        } catch (Throwable e) {
            sp.edit().putString("stage", "FAILED").putString("msg", "❌ خطأ: " + e.getClass().getSimpleName() + " " + String.valueOf(e.getMessage())).apply();
            notifyDone("حصلت مشكلة — افتح التطبيق");
        } finally {
            releaseLocks();
            if (!stopping) finish();
        }
    }

    /** تقرير النشر على التك توك اللي السيرفر سجّله. */
    String ttResult(String job) {
        try {
            String[] x = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO + "/releases/tags/tt-" + job, null);
            if ("200".equals(x[0])) {
                String b = new JSONObject(x[1]).optString("body", "").trim();
                if (!b.isEmpty()) return b.length() > 500 ? b.substring(0, 500) : b;
            }
        } catch (Exception ignored) { }
        return "مفيش تقرير نشر من السيرفر (راجع تبويب Actions)";
    }

    /** بيجيب سبب الفشل الحقيقي اللي السيرفر سجّله. */
    String failReason(String job, String con) {
        try {
            String[] x = Gh.http("GET", Gh.API + "/repos/" + Gh.REPO + "/releases/tags/err-" + job, null);
            if ("200".equals(x[0])) {
                String b = new JSONObject(x[1]).optString("body", "").trim();
                if (!b.isEmpty()) return b.length() > 700 ? b.substring(0, 700) : b;
            }
        } catch (Exception ignored) { }
        return "الشغل فشل (" + con + "). جرّب رابط تاني.";
    }

    // ───────── التنزيل (بيكمّل من حيث وقف) ─────────
    String download(final String tag) throws Exception {
        JSONObject rel = retry(() -> Gh.getJson("/releases/tags/" + tag));
        JSONArray assets = rel.getJSONArray("assets");
        long aid = 0, size = 0;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject o = assets.getJSONObject(i);
            if ("reels.zip".equals(o.getString("name"))) {
                aid = o.getLong("id");
                size = o.getLong("size");
            }
        }
        if (aid == 0 || size <= 0) throw new Gh.Fatal("ملف reels.zip مش موجود في الإصدار.");
        final long fAid = aid, fSize = size;
        final File part = new File(getCacheDir(), tag + ".part");
        if (part.exists() && part.length() > fSize) part.delete();
        while (part.length() < fSize) {
            retry(() -> {
                pullOnce(fAid, fSize, part);
                return Boolean.TRUE;
            });
        }
        String name = "Reels_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".zip";
        boolean tt = sp.getBoolean("tt_on", false);
        boolean keep = sp.getBoolean("tt_keep", true);
        int queued = 0;
        if (tt) {
            try { queued = TikTokQ.extract(this, part); } catch (Exception ignored) { }
        }
        String where = (tt && !keep && queued > 0) ? "" : save(part, name);
        part.delete();
        sp.edit().putInt("tt_queued", queued).apply();
        return where;
    }

    void pullOnce(long aid, long size, File part) throws Exception {
        final long have = part.length();
        HttpURLConnection c = Gh.open("GET", Gh.API + "/repos/" + Gh.REPO + "/releases/assets/" + aid, true);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/octet-stream");
        if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
        int code = c.getResponseCode();
        HttpURLConnection d = c;
        if (code >= 300 && code < 400) {
            String loc = c.getHeaderField("Location");
            c.disconnect();
            if (loc == null) throw new IOException("no location");
            d = Gh.open("GET", loc, false);   // الرابط الموقّع من غير التوكن
            d.setRequestProperty("Accept", "application/octet-stream");
            if (have > 0) d.setRequestProperty("Range", "bytes=" + have + "-");
            code = d.getResponseCode();
        }
        try {
            if (code == 416) {
                part.delete();
                throw new IOException("range");
            }
            if (code == 401 || code == 404) throw new Gh.Fatal("فشل التنزيل (" + code + ").");
            if (code != 200 && code != 206) throw new IOException("http " + code);
            boolean append = code == 206 && have > 0;
            long done = append ? have : 0;
            long lastShown = done;
            try (InputStream in = d.getInputStream(); OutputStream out = new FileOutputStream(part, append)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (stopping) throw new InterruptedException();
                    out.write(buf, 0, n);
                    done += n;
                    if (done - lastShown > (512 << 10)) {
                        lastShown = done;
                        int pct = size > 0 ? (int) (done * 100 / size) : 0;
                        status("⬇️ بنزّل الملف: " + (done >> 20) + " / " + (size >> 20) + " MB", pct, false);
                    }
                }
            }
        } finally {
            d.disconnect();
        }
    }

    /** بيحفظ الملف في «التنزيلات» (أندرويد 10+ من غير صلاحيات). بيرجّع الاسم/المكان للعرض. */
    String save(File src, String name) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Gh.Fatal("مقدرتش أكتب في التنزيلات.");
            try (InputStream in = new FileInputStream(src); OutputStream out = cr.openOutputStream(uri)) {
                if (out == null) throw new IOException("no stream");
                copy(in, out);
            } catch (Exception e) {
                cr.delete(uri, null, null);
                throw e;
            }
            v.clear();
            v.put(MediaStore.Downloads.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            return name;
        }
        File dir;
        boolean pub = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        if (pub) {
            dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        } else {
            dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        }
        if (dir == null || (!dir.exists() && !dir.mkdirs())) throw new Gh.Fatal("مقدرتش أعمل مجلد التنزيلات.");
        File f = new File(dir, name);
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(f)) {
            copy(in, out);
        } catch (Exception e) {
            f.delete();
            throw e;
        }
        MediaScannerConnection.scanFile(this, new String[]{f.getAbsolutePath()}, new String[]{"application/zip"}, null);
        return pub ? name : f.getAbsolutePath();
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
