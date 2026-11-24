package com.lowell.tvremote;

import android.content.Context;

import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/** TV tarafi: ADB baglantisi + web sunucusu; servis ve ekran bunu paylasir. */
final class Backend {
    static final Backend I = new Backend();
    static final int PORT = 8080;

    private AdbLink adb;
    private WebServer web;
    private Remote remote;
    volatile boolean serverUp = false;
    volatile String serverMsg = "Baslamadi";
    volatile long startedAt = 0;

    synchronized void start(Context c) {
        if (serverUp) return;
        Context app = c.getApplicationContext();
        if (adb == null) { adb = new AdbLink(app); remote = new Remote(app, adb); }
        try {
            web = new WebServer(app, remote);
            web.start(PORT);
            serverUp = true;
            serverMsg = "Calisiyor";
            startedAt = System.currentTimeMillis();
        } catch (Exception e) {
            serverUp = false;
            serverMsg = "Acilamadi: " + e.getMessage();
            Logx.e("Sunucu acilamadi: " + e);
        }
        // ADB'yi ve sanal cihazlari arka planda hazirla (izin penceresi beklenebilir)
        new Thread(() -> {
            for (int i = 0; i < 120; i++) {
                if (adb.connected && adb.hidReady) return;
                if (adb.connect()) { adb.run("true"); return; }
                try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
            }
        }, "adb-init").start();
    }

    synchronized void restart(Context c) {
        if (web != null) web.stop();
        serverUp = false;
        if (adb != null) adb.reset();
        Logx.i("Yeniden baslatiliyor");
        start(c);
    }

    synchronized void stop() {
        if (web != null) web.stop();
        serverUp = false;
        serverMsg = "Durduruldu";
    }

    String adbState() { return adb == null ? "Baslamadi" : adb.state; }
    boolean adbConnected() { return adb != null && adb.connected; }
    boolean hidReady() { return adb != null && adb.hidReady; }
    int clients() { return web == null ? 0 : web.clients; }
    long requests() { return web == null ? 0 : web.requests; }

    static String lanIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return "?";
    }

    String statusJson() {
        try {
            return new JSONObject().put("server", serverUp).put("adb", adbConnected()).put("hid", hidReady())
                    .put("adbState", adbState()).put("clients", clients()).put("ip", lanIp()).toString();
        } catch (Exception e) { return "{}"; }
    }
}
