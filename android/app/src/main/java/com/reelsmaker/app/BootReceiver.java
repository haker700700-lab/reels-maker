package com.reelsmaker.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/** بعد إعادة تشغيل الموبايل: لو في عملية لسه شغّالة، نكمّل متابعتها. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("reels", Context.MODE_PRIVATE);
            String st = sp.getString("stage", "");
            if (!"NEW".equals(st) && !"RUNNING".equals(st)) return;
            Intent i = new Intent(ctx, JobService.class);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Throwable ignored) {
            // أندرويد ممكن يمنع التشغيل من الخلفية؛ هنكمّل لما التطبيق يتفتح
        }
    }
}
