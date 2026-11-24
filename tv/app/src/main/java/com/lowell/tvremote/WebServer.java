package com.lowell.tvremote;

import android.content.Context;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minik HTTP + WebSocket (RFC 6455) sunucusu. */
final class WebServer {
    private final Context ctx;
    private final Remote remote;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private ServerSocket ss;
    private volatile boolean running;
    volatile int clients = 0;
    volatile long requests = 0;

    WebServer(Context c, Remote r) { ctx = c.getApplicationContext(); remote = r; }

    void start(int port) throws IOException {
        ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(port), 64);
        running = true;
        Thread t = new Thread(this::loop, "accept");
        t.setDaemon(true);
        t.start();
        Logx.i("Web sunucusu dinliyor: " + port);
    }

    void stop() {
        running = false;
        try { if (ss != null) ss.close(); } catch (IOException ignored) { }
    }

    private void loop() {
        while (running) {
            try {
                Socket s = ss.accept();
                s.setTcpNoDelay(true);
                s.setSoTimeout(120000);
                pool.execute(() -> handle(s));
            } catch (IOException e) {
                if (!running) return;
                Logx.w("accept hatasi: " + e);
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) throw new IOException("satir cok uzun");
        }
        if (c == -1 && sb.length() == 0) return null;
        return sb.toString();
    }

    private void handle(Socket s) {
        try {
            InputStream in = new BufferedInputStream(s.getInputStream(), 8192);
            OutputStream out = s.getOutputStream();
            while (true) {
                String req = readLine(in);
                if (req == null || req.isEmpty()) return;
                String[] p = req.split(" ");
                if (p.length < 2) return;
                Map<String, String> h = new HashMap<>();
                String line;
                while ((line = readLine(in)) != null && !line.isEmpty()) {
                    int i = line.indexOf(':');
                    if (i > 0) h.put(line.substring(0, i).trim().toLowerCase(), line.substring(i + 1).trim());
                }
                String method = p[0], path = p[1];
                int q = path.indexOf('?');
                if (q >= 0) path = path.substring(0, q);
                requests++;
                if (path.equals("/ws") && "websocket".equalsIgnoreCase(h.get("upgrade"))) {
                    s.setSoTimeout(600000);
                    ws(in, out, h.get("sec-websocket-key"));
                    return;
                }
                byte[] body = new byte[0];
                int cl = 0;
                try { cl = Integer.parseInt(h.getOrDefault("content-length", "0")); } catch (NumberFormatException ignored) { }
                if (cl > 0) {
                    if (cl > (1 << 20)) return;
                    body = new byte[cl];
                    new DataInputStream(in).readFully(body);
                }
                if (method.equals("POST")) {
                    Remote.Res r;
                    try { r = remote.act(new JSONObject(new String(body, StandardCharsets.UTF_8))); }
                    catch (Exception e) { r = new Remote.Res(400, "{\"error\": \"json\"}"); }
                    send(out, r.code, r.body, "application/json");
                } else if (method.equals("GET")) {
                    get(out, path);
                } else {
                    send(out, 405, "{\"error\": \"yontem\"}", "application/json");
                }
            }
        } catch (Exception ignored) {
        } finally {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    private byte[] indexPage() throws IOException {
        File f = new File(ctx.getFilesDir(), "index.html"); // istenirse guncel sayfa buraya konabilir
        InputStream is = f.exists() ? new FileInputStream(f) : ctx.getAssets().open("index.html");
        try (InputStream in = is) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }

    private void get(OutputStream out, String path) throws IOException {
        try {
            switch (path) {
                case "/": case "/index.html":
                    send(out, 200, indexPage(), "text/html; charset=utf-8");
                    break;
                case "/info": send(out, 200, remote.screenInfo(), "application/json"); break;
                case "/apps": send(out, 200, remote.listApps(), "application/json"); break;
                case "/launchers": send(out, 200, remote.listLaunchers(), "application/json"); break;
                case "/log": send(out, 200, Logx.tail(300), "text/plain; charset=utf-8"); break;
                case "/status": send(out, 200, Backend.I.statusJson(), "application/json"); break;
                case "/ime": send(out, 200, remote.imeInfo(), "application/json"); break;
                default: send(out, 404, "{\"error\": \"yok\"}", "application/json");
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            send(out, 500, "{\"error\": " + JSONObject.quote(String.valueOf(e)) + "}", "application/json");
        }
    }

    private static void send(OutputStream out, int code, String body, String ctype) throws IOException {
        send(out, code, body.getBytes(StandardCharsets.UTF_8), ctype);
    }

    private static void send(OutputStream out, int code, byte[] b, String ctype) throws IOException {
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Error") + "\r\nContent-Type: " + ctype
                + "\r\nContent-Length: " + b.length + "\r\nCache-Control: no-store\r\nConnection: keep-alive\r\n\r\n";
        ByteArrayOutputStream bo = new ByteArrayOutputStream(b.length + 200);
        bo.write(head.getBytes(StandardCharsets.UTF_8));
        bo.write(b);
        out.write(bo.toByteArray());
        out.flush();
    }

    // ---- WebSocket ----

    private static void wsSend(OutputStream out, byte[] b, int opcode) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(0x80 | opcode);
        int n = b.length;
        if (n < 126) bo.write(n);
        else if (n < 65536) { bo.write(126); bo.write(n >> 8); bo.write(n & 0xFF); }
        else { bo.write(127); for (int i = 7; i >= 0; i--) bo.write((int) (((long) n >> (8 * i)) & 0xFF)); }
        bo.write(b);
        synchronized (out) { out.write(bo.toByteArray()); out.flush(); }
    }

    private void ws(InputStream in, OutputStream out, String key) throws Exception {
        String accept = Base64.encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
                + accept + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        DataInputStream di = new DataInputStream(in);
        clients++;
        Logx.i("Telefon baglandi (WS), aktif: " + clients);
        try {
            while (true) {
                int b1 = di.readUnsignedByte(), b2 = di.readUnsignedByte();
                int opcode = b1 & 0x0F;
                boolean masked = (b2 & 0x80) != 0;
                long len = b2 & 0x7F;
                if (len == 126) len = di.readUnsignedShort();
                else if (len == 127) len = di.readLong();
                if (len > (1 << 20)) break;
                byte[] mask = new byte[4];
                if (masked) di.readFully(mask);
                byte[] data = new byte[(int) len];
                di.readFully(data);
                if (masked) for (int i = 0; i < data.length; i++) data[i] ^= mask[i % 4];
                if (opcode == 0x8) { wsSend(out, new byte[0], 0x8); break; }
                if (opcode == 0x9) { wsSend(out, data, 0xA); continue; }
                if (opcode != 0x1) continue;
                Remote.Res r;
                String txt = new String(data, StandardCharsets.UTF_8);
                try { r = remote.act(new JSONObject(txt)); }
                catch (Exception e) { r = new Remote.Res(400, "{\"error\": \"json\"}"); }
                if (txt.contains("\"ping\"")) wsSend(out, "{\"pong\":1}".getBytes(StandardCharsets.UTF_8), 0x1); // canli kalma yaniti
                else if (r.code != 200 || r.body.contains("skipped") || txt.contains("\"ime\"")) wsSend(out, r.body.getBytes(StandardCharsets.UTF_8), 0x1);
            }
        } catch (IOException ignored) {
        } finally {
            clients--;
            Logx.i("Telefon ayrildi, aktif: " + clients);
        }
    }
}
