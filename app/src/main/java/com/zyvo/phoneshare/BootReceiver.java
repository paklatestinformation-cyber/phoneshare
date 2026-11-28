package com.zyvo.phoneshare;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        SharedPreferences sp = context.getSharedPreferences("ps", Context.MODE_PRIVATE);
        if (!sp.getBoolean("sharing_enabled", true)) return;
        Intent svc = new Intent(context, ShareService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc);
            else context.startService(svc);
        } catch (Exception ignored) {
            // Android may block background starts on some OEM ROMs; the next app open re-starts it.
        }
    }
}
