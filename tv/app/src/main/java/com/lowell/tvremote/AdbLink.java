package com.lowell.tvremote;

import android.content.Context;
import android.util.Base64;

import com.cgutman.adblib.AdbBase64;
import com.cgutman.adblib.AdbConnection;
import com.cgutman.adblib.AdbCrypto;
import com.cgutman.adblib.AdbStream;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** TV'nin kendi ADB'sine (127.0.0.1:5555) baglanir; kalici shell + tek seferlik komutlar. */
final class AdbLink {
    private static final AdbBase64 B64 = data -> Base64.encodeToString(data, Base64.NO_WRAP);

    private final Context ctx;
    private final Object lock = new Object();
    private volatile boolean connecting = false;
    private AdbConnection conn;
    private Socket sock;
    private AdbCrypto crypto;
    private AdbStream shell;
    private final Object shellLock = new Object();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final java.util.concurrent.ExecutorService pool = Executors.newCachedThreadPool();

    volatile String state = "Baslamadi";
    volatile boolean connected = false;
    volatile boolean hidReady = false;

    AdbLink(Context c) { ctx = c.getApplicationContext(); }

    private AdbCrypto crypto() throws Exception {
        if (crypto != null) return crypto;
        File k = new File(ctx.getFilesDir(), "adbkey"), p = new File(ctx.getFilesDir(), "adbkey.pub");
        try {
            if (k.exists() && p.exists()) { crypto = AdbCrypto.loadAdbKeyPair(B64, k, p); return crypto; }
        } catch (Exception e) { Logx.w("ADB anahtari okunamadi, yenisi uretiliyor: " + e.getMessage()); }
        crypto = AdbCrypto.generateAdbKeyPair(B64);
        crypto.saveAdbKeyPair(k, p);
        Logx.i("Yeni ADB anahtari uretildi");
        return crypto;
    }

    boolean connect() {
        if (connecting) return false;
        synchronized (lock) {
            connecting = true;
            try {
                closeConn();
                state = "Baglaniyor...";
                Socket s = new Socket();
                s.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
                sock = s;
                conn = AdbConnection.create(s, crypto());
                state = "TV'de 'USB hata ayiklamasina izin ver' penceresini onayla (Her zaman izin ver)";
                Logx.i("ADB'ye baglaniliyor (ilk seferde TV'de izin penceresi cikar)");
                conn.connect();
                connected = true;
                state = "Bagli";
                Logx.i("ADB bagli");
                return true;
            } catch (Exception e) {
                state = "Hata: " + e.getMessage();
                Logx.e("ADB baglanti hatasi: " + e);
                closeConn();
                return false;
            } finally {
                connecting = false;
            }
        }
    }

    private void closeConn() {
        connected = false;
        hidReady = false;
        synchronized (shellLock) { shell = null; }
        try { if (conn != null) conn.close(); } catch (Exception ignored) { }
        try { if (sock != null) sock.close(); } catch (Exception ignored) { }
        conn = null; sock = null;
    }

    void reset() {
        synchronized (lock) { closeConn(); state = "Yeniden baglanilacak"; }
    }

    private AdbConnection need() throws Exception {
        AdbConnection c = conn;
        if (c == null || !connected) {
            if (!connect()) throw new IllegalStateException("ADB bagli degil: " + state);
            c = conn;
        }
        return c;
    }

    /** Tek seferlik shell komutu; cikti doner. */
    String exec(String cmd, int timeoutMs) throws Exception {
        Exception last = null;
        for (int a = 0; a < 2; a++) {
            AdbStream st = null;
            try {
                st = need().open("shell:" + cmd);
                final AdbStream fs = st;
                timer.schedule(() -> { try { fs.close(); } catch (Exception ignored) { } }, timeoutMs, TimeUnit.MILLISECONDS);
                StringBuilder sb = new StringBuilder();
                while (true) {
                    byte[] b;
                    try { b = st.read(); } catch (java.io.IOException e) { break; }
                    if (b == null) break;
                    sb.append(new String(b, "UTF-8"));
                }
                return sb.toString().replace("\r", "").trim();
            } catch (Exception e) {
                last = e;
                connected = false; // yeniden baglan
                try { if (st != null) st.close(); } catch (Exception ignored) { }
            }
        }
        throw last;
    }

    /** Sonucu beklemeden calistir. */
    void execBg(String cmd) {
        pool.execute(() -> {
            try { exec(cmd, 15000); } catch (Exception e) { Logx.w("komut basarisiz: " + e.getMessage()); }
        });
    }

    // ---- kalici shell (UHID) ----

    private void startShell() throws Exception {
        AdbStream st;
        try {
            st = need().open("shell,raw:sh"); // pty yok: satir siniri/yankilanma sorunu olmaz
        } catch (Exception e) {
            Logx.w("raw shell acilamadi, pty'li deneniyor: " + e);
            st = need().open("shell:");
        }
        final AdbStream fs = st;
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    byte[] b = fs.read();
                    if (b == null) break;
                    String s = new String(b, "UTF-8").trim();
                    String lo = s.toLowerCase();
                    if (lo.contains("bad file") || lo.contains("denied") || lo.contains("not found") || lo.contains("no such")
                            || lo.contains("error") || lo.contains("invalid")) Logx.w("[shell] " + (s.length() > 160 ? s.substring(0, 160) : s));
                }
            } catch (Exception ignored) { }
            synchronized (shellLock) { if (shell == fs) { shell = null; hidReady = false; Logx.w("Kalici shell kapandi"); } }
        }, "shell-drain");
        t.setDaemon(true);
        t.start();
        shell = st;
        String[] names = {"tvremote-mouse", "tvremote-keyboard", "tvremote-media"};
        byte[][] rds = {Hid.MOUSE_RD, Hid.KBD_RD, Hid.CONS_RD};
        int[] fds = {Hid.FD_MOUSE, Hid.FD_KBD, Hid.FD_MEDIA};
        for (int i = 0; i < 3; i++) {
            // her cihaz ayri satir, aralarda bekle (pty giris tamponu tasmasin)
            write(st, "exec " + fds[i] + "<>/dev/uhid; printf '" + Hid.octal(Hid.createEvent(names[i], rds[i]))
                    + "' >&" + fds[i] + "\n");
            Thread.sleep(300);
        }
        Thread.sleep(500);
        String n = exec("grep -c tvremote /proc/bus/input/devices", 5000);
        if (!"3".equals(n.trim())) {
            shell = null;
            try { st.close(); } catch (Exception ignored) { }
            throw new IllegalStateException("sanal cihazlar olusmadi (" + n.trim() + "/3)");
        }
        hidReady = true;
        Logx.i("Sanal fare/klavye/medya cihazlari olusturuldu");
    }

    private void write(AdbStream st, String s) throws Exception {
        byte[] b = s.getBytes("UTF-8");
        int off = 0;
        while (off < b.length) {
            int n = Math.min(3500, b.length - off);
            byte[] c = new byte[n];
            System.arraycopy(b, off, c, 0, n);
            st.write(c);
            off += n;
            if (off < b.length) Thread.sleep(60);
        }
    }

    /** UHID komut satirlarini kalici shell'e yazar. */
    boolean run(String... lines) {
        synchronized (shellLock) {
            for (int a = 0; a < 2; a++) {
                try {
                    if (shell == null || shell.isClosed()) startShell();
                    write(shell, String.join("\n", lines) + "\n");
                    return true;
                } catch (Exception e) {
                    Logx.w("shell hatasi: " + e);
                    shell = null; hidReady = false; connected = false;
                    if (a == 0) { try { Thread.sleep(300); } catch (InterruptedException ignored) { } }
                }
            }
            return false;
        }
    }
}
