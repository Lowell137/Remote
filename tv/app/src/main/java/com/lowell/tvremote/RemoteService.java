package com.lowell.tvremote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;

public class RemoteService extends Service {
    private static final String CH = "kumanda";
    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifi;

    static void startIt(Context c) {
        Intent i = new Intent(c, RemoteService.class);
        try { c.startForegroundService(i); } catch (Exception e) { Logx.e("Servis baslatilamadi: " + e); }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, "Kumanda sunucusu", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, TvActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CH)
                .setContentTitle("TV kumanda sunucusu")
                .setContentText("http://" + Backend.lanIp() + ":" + Backend.PORT)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        try {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } catch (Exception e) {
            Logx.e("startForeground hatasi: " + e);
            try { startForeground(1, n); } catch (Exception e2) { Logx.e("startForeground (2) hatasi: " + e2); }
        }
        PowerManager pm = getSystemService(PowerManager.class);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tvk:server");
        wake.acquire();
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) { wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tvk:wifi"); wifi.acquire(); }
        Logx.i("Servis olustu");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Backend.I.start(this);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        Logx.w("Servis yok ediliyor");
        Backend.I.stop();
        if (wake != null && wake.isHeld()) wake.release();
        if (wifi != null && wifi.isHeld()) wifi.release();
        super.onDestroy();
    }
}
