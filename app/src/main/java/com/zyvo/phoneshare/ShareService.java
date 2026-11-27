package com.zyvo.phoneshare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import java.io.File;

public class ShareService extends Service {
    public static final String CHANNEL_ID = "nexlink_share";
    public static final int NOTIF_ID = 1001;
    public static final String ACTION_STOP = "com.zyvo.phoneshare.STOP";

    private static ShareService instance;
    private static FileServer server;
    private static File rootDir;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    public static boolean isRunning() { return instance != null && server != null; }
    public static FileServer getServer() { return server; }
    public static File getRootDir() { return rootDir; }
    public static ShareService getInstance() { return instance; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        rootDir = new File(getFilesDir(), "shared");
        if (!rootDir.exists()) rootDir.mkdirs();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIF_ID, buildNotification());
        acquireLocks();
        startServer();
        return START_STICKY;
    }

    private void startServer() {
        if (server != null) return;
        try {
            server = new FileServer(this, rootDir);
            server.start();
        } catch (Exception e) {
            server = null;
        }
    }

    private void acquireLocks() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NexLink::ShareWakeLock");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Exception ignored) {}
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "NexLink::WifiLock");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        } catch (Exception ignored) {}
    }

    private void releaseLocks() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) {}
        wakeLock = null; wifiLock = null;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, ShareService.class).setAction(ACTION_STOP);
        PendingIntent spi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String ip = getIpString();
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NexLink sharing active")
                .setContentText(ip + ":" + FileServer.PORT + "  •  tap to open")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(pi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", spi)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private String getIpString() {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            android.net.wifi.WifiInfo wi = wm.getConnectionInfo();
            int ip = wi.getIpAddress();
            if (ip != 0) return String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        } catch (Exception ignored) {}
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress ia : java.util.Collections.list(ni.getInetAddresses())) {
                    if (!ia.isLoopbackAddress() && ia.getHostAddress().indexOf(':') < 0) return ia.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "NexLink Sharing", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps file sharing running in background");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        if (server != null) { try { server.stop(); } catch (Exception ignored) {} server = null; }
        releaseLocks();
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}