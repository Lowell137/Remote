package com.lowell.tvremote;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Telefondan gelen olaylari UHID / ADB komutlarina cevirir (server.py act() portu). */
final class Remote {
    static final class Res {
        final int code; final String body;
        Res(int c, String b) { code = c; body = b; }
    }

    private static final Pattern PKG = Pattern.compile("[A-Za-z0-9._]+");
    private static final Pattern COMP = Pattern.compile("[A-Za-z0-9._]+/[A-Za-z0-9._$]+");
    private static final java.util.Set<String> PROTECTED = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.termux", "com.termux.boot", "android", "com.android.settings", "com.lowell.tvremote"));
    private static final Map<String, String> HOME_NAMES = new HashMap<>();
    private static final Map<String, String> POWER = new HashMap<>();
    private static final Map<String, String> SETTINGS = new HashMap<>();

    static {
        HOME_NAMES.put("com.spocky.projengmenu", "Projectivy"); HOME_NAMES.put("com.leta.launcher", "LeTa");
        HOME_NAMES.put("com.klevico.monet", "Monet"); HOME_NAMES.put("org.lineageos.tv.launcher", "LineageOS TV");
        HOME_NAMES.put("com.android.tv.settings", "Yedek ana ekran"); HOME_NAMES.put("com.overdevs.at4k", "AT4K");
        POWER.put("sleep", "input keyevent 223"); POWER.put("wake", "input keyevent 224");
        POWER.put("reboot", "reboot"); POWER.put("poweroff", "reboot -p");
        String[][] s = {{"wifi", "WIFI_SETTINGS"}, {"network", "WIRELESS_SETTINGS"}, {"bluetooth", "BLUETOOTH_SETTINGS"},
                {"display", "DISPLAY_SETTINGS"}, {"sound", "SOUND_SETTINGS"}, {"apps", "APPLICATION_SETTINGS"},
                {"storage", "INTERNAL_STORAGE_SETTINGS"}, {"access", "ACCESSIBILITY_SETTINGS"},
                {"defaults", "MANAGE_DEFAULT_APPS_SETTINGS"}, {"date", "DATE_SETTINGS"},
                {"input", "INPUT_METHOD_SETTINGS"}, {"dev", "APPLICATION_DEVELOPMENT_SETTINGS"}, {"all", "SETTINGS"}};
        for (String[] p : s) SETTINGS.put(p[0], "android.settings." + p[1]);
    }

    private final Context ctx;
    private final AdbLink adb;
    private int sw = 1920, sh = 1080;
    private boolean sized = false;

    Remote(Context c, AdbLink a) { ctx = c.getApplicationContext(); adb = a; }

    private static Res ok() { return new Res(200, "{\"ok\": true}"); }
    private static Res err(int code, String e) { return new Res(code, "{\"error\": " + JSONObject.quote(e) + "}"); }

    private static int clamp(int v) { return Hid.clamp(v); }

    private void mouseMove(double fdx, double fdy, double fw) {
        int dx = (int) Math.round(fdx), dy = (int) Math.round(fdy), w = (int) Math.round(fw);
        List<String> cmds = new ArrayList<>();
        while (dx != 0 || dy != 0 || w != 0) {
            int sx = clamp(dx), sy = clamp(dy), sww = clamp(w);
            cmds.add(Hid.reportCmd(Hid.FD_MOUSE, Hid.mouseReport(0, sx, sy, sww)));
            dx -= sx; dy -= sy; w -= sww;
            if (cmds.size() > 12) break;
        }
        if (!cmds.isEmpty()) adb.run(cmds.toArray(new String[0]));
    }

    private void mouseClick(int b, boolean dbl) {
        List<String> cmds = new ArrayList<>();
        for (int i = 0; i < (dbl ? 2 : 1); i++) {
            cmds.add(Hid.reportCmd(Hid.FD_MOUSE, Hid.mouseReport(b, 0, 0, 0)));
            cmds.add(Hid.reportCmd(Hid.FD_MOUSE, Hid.mouseReport(0, 0, 0, 0)));
        }
        adb.run(cmds.toArray(new String[0]));
    }

    private boolean pressKey(String name) {
        Integer k = Hid.KEYS.get(name);
        if (k != null) { adb.run(Hid.keyCmds(k, 0)); return true; }
        Integer c = Hid.CONS.get(name);
        if (c != null) {
            adb.run(Hid.reportCmd(Hid.FD_MEDIA, new byte[]{(byte) (c & 0xFF), (byte) (c >> 8)}),
                    Hid.reportCmd(Hid.FD_MEDIA, new byte[2]));
            return true;
        }
        return false;
    }

    boolean setClipboard(String text) {
        if (text == null) text = "";
        final String val = text;
        final AtomicBoolean ok = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    ClipData clip = ClipData.newPlainText("tvremote", val);
                    cm.setPrimaryClip(clip);
                    ok.set(true);
                }
            } catch (Throwable e) {
                Logx.w("ClipboardManager hatasi: " + e);
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(600, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) { }
        return ok.get();
    }

    boolean pasteText(String s) {
        if (s == null || s.isEmpty()) return true;
        boolean ok = setClipboard(s);
        // Ctrl+V key combination via HID keyboard (0x19 is KEY_V, 0x01 is KEY_MOD_LCTRL)
        adb.run(Hid.keyCmds(0x19, 0x01));
        // Fallback Android keyevent for paste
        adb.execBg("input keyevent 279");
        return ok;
    }

    private int typeText(String s) {
        List<String> cmds = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < Math.min(400, s.length()); i++) {
            char ch = s.charAt(i);
            int[] k = Hid.asciiKey(ch);
            if (k != null) {
                Collections.addAll(cmds, Hid.keyCmds(k[0], k[1]));
            } else {
                if (!cmds.isEmpty()) {
                    adb.run(cmds.toArray(new String[0]));
                    cmds.clear();
                }
                boolean p = pasteText(String.valueOf(ch));
                if (!p) skipped++;
            }
        }
        if (!cmds.isEmpty()) adb.run(cmds.toArray(new String[0]));
        return skipped;
    }

    String imeInfo() {
        try {
            String out = adb.exec("dumpsys input_method", 1500);
            boolean inputShown = out.contains("mInputShown=true");
            boolean showRequested = out.contains("mShowInputRequested=true");
            boolean viewStarted = out.contains("mInputViewStarted=true");

            int inputType = 0;
            String pkg = "";
            String hint = "";
            boolean hasConnection = false;

            int edIdx = out.indexOf("mCurrentEditorInfo:");
            if (edIdx >= 0) {
                String sub = out.substring(edIdx, Math.min(out.length(), edIdx + 1200));
                java.util.regex.Matcher mi = Pattern.compile("inputType=(0x[0-9a-fA-F]+|\\d+)").matcher(sub);
                if (mi.find()) {
                    String itStr = mi.group(1);
                    inputType = itStr.startsWith("0x") ? Integer.parseInt(itStr.substring(2), 16) : Integer.parseInt(itStr);
                }
                java.util.regex.Matcher mp = Pattern.compile("packageName=([a-zA-Z0-9._]+)").matcher(sub);
                if (mp.find()) pkg = mp.group(1);
                java.util.regex.Matcher mh = Pattern.compile("hintText=([^ \n\r]+)").matcher(sub);
                if (mh.find()) {
                    String h = mh.group(1);
                    if (!"null".equalsIgnoreCase(h)) hint = h;
                }
            }

            int icIdx = out.indexOf("mServedInputConnection=");
            if (icIdx >= 0) {
                int end = out.indexOf('\n', icIdx);
                String line = end > 0 ? out.substring(icIdx, end) : out.substring(icIdx);
                if (!line.contains("=null")) hasConnection = true;
            }

            boolean active = inputShown || showRequested || viewStarted || (hasConnection && inputType != 0);
            return new JSONObject()
                    .put("active", active)
                    .put("shown", inputShown)
                    .put("inputType", inputType)
                    .put("pkg", pkg)
                    .put("hint", hint)
                    .toString();
        } catch (Exception e) {
            return "{\"active\": false, \"error\": " + JSONObject.quote(String.valueOf(e)) + "}";
        }
    }

    private static String launchCmd(String pkg) {
        return "monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1 2>&1 | grep -q 'Events injected' "
                + "|| monkey -p " + pkg + " -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1";
    }

    Res act(JSONObject d) {
        String t = d.optString("t", "");
        try {
            switch (t) {
                case "ping": break;
                case "move": mouseMove(d.optDouble("dx", 0), d.optDouble("dy", 0), d.optDouble("w", 0)); break;
                case "click": mouseClick(d.optInt("b", 1), d.optBoolean("double", false)); break;
                case "btn": adb.run(Hid.reportCmd(Hid.FD_MOUSE, Hid.mouseReport(d.optBoolean("down", false) ? d.optInt("b", 1) : 0, 0, 0, 0))); break;
                case "key": if (!pressKey(d.optString("k", ""))) return err(400, "tus"); break;
                case "text": return new Res(200, "{\"skipped\": " + typeText(d.optString("s", "")) + "}");
                case "paste": return new Res(200, "{\"ok\": " + pasteText(d.optString("s", "")) + "}");
                case "ime": return new Res(200, imeInfo());
                case "app": {
                    String pkg = d.optString("pkg", "");
                    if (!PKG.matcher(pkg).matches()) return err(400, "paket");
                    adb.execBg(launchCmd(pkg));
                    break;
                }
                case "power": {
                    String cmd = POWER.get(d.optString("a", ""));
                    if (cmd == null) return err(400, "guc");
                    adb.execBg(cmd);
                    break;
                }
                case "settings": {
                    String a = SETTINGS.get(d.optString("k", ""));
                    if (a == null) return err(400, "ayar");
                    adb.execBg("am start -a " + a);
                    break;
                }
                case "home": {
                    String c = d.optString("c", "");
                    if (!COMP.matcher(c).matches()) return err(400, "bilesen");
                    String out = adb.exec("cmd package set-home-activity " + c, 20000);
                    return new Res(200, "{\"ok\": true, \"out\": " + JSONObject.quote(out.length() > 200 ? out.substring(0, 200) : out) + "}");
                }
                case "appact": return appAction(d.optString("a", ""), d.optString("pkg", ""));
                default: return err(400, "t");
            }
        } catch (Exception e) {
            Logx.w("act(" + t + ") hata: " + e);
            return err(500, String.valueOf(e));
        }
        return ok();
    }

    private Res appAction(String a, String pkg) throws Exception {
        if (!PKG.matcher(pkg).matches()) return new Res(400, "{\"ok\": false, \"msg\": \"paket\"}");
        if ((a.equals("stop") || a.equals("clear") || a.equals("uninstall")) && PROTECTED.contains(pkg))
            return new Res(400, "{\"ok\": false, \"msg\": \"korumali\"}");
        switch (a) {
            case "open": adb.execBg(launchCmd(pkg)); break;
            case "stop": adb.execBg("am force-stop " + pkg); break;
            case "info": adb.execBg("am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:" + pkg); break;
            case "clear": adb.execBg("pm clear " + pkg); break;
            case "uninstall": {
                String out = adb.exec("pm uninstall " + pkg, 30000);
                boolean s = out.contains("Success");
                apps = null;
                Logx.i("uninstall " + pkg + " -> " + out);
                return new Res(s ? 200 : 400, "{\"ok\": " + s + ", \"msg\": " + JSONObject.quote(out) + "}");
            }
            default: return new Res(400, "{\"ok\": false, \"msg\": \"islem\"}");
        }
        return new Res(200, "{\"ok\": true, \"msg\": \"ok\"}");
    }

    // ---- listeler ----

    private String apps; private long appsT = 0;

    synchronized String listApps() {
        if (apps != null && System.currentTimeMillis() - appsT < 60000) return apps;
        PackageManager pm = ctx.getPackageManager();
        List<String[]> rows = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue;
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (ai.packageName.equals(ctx.getPackageName())) continue;
            rows.add(new String[]{ai.packageName, String.valueOf(pm.getApplicationLabel(ai))});
        }
        Collections.sort(rows, (x, y) -> x[1].compareToIgnoreCase(y[1]));
        JSONArray arr = new JSONArray();
        try {
            for (String[] r : rows) arr.put(new JSONObject().put("pkg", r[0]).put("name", r[1]));
        } catch (Exception ignored) { }
        apps = arr.toString(); appsT = System.currentTimeMillis();
        return apps;
    }

    String listLaunchers() throws Exception {
        String out = adb.exec("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME", 20000);
        List<String> comps = new ArrayList<>();
        for (String l : out.split("\n")) {
            l = l.trim();
            if (COMP.matcher(l).matches() && !comps.contains(l)) comps.add(l);
        }
        String cur = adb.exec("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME", 20000).trim();
        String[] cl = cur.split("\n");
        cur = cl.length > 0 ? cl[cl.length - 1].trim() : "";
        PackageManager pm = ctx.getPackageManager();
        JSONArray items = new JSONArray();
        for (String c : comps) {
            String pkg = c.split("/")[0];
            String name = HOME_NAMES.get(pkg);
            if (name == null) {
                try { name = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))); }
                catch (Exception e) { String[] p = pkg.split("\\."); name = p[p.length - 1]; }
            }
            items.put(new JSONObject().put("c", c).put("pkg", pkg).put("name", name).put("current", c.equals(cur)));
        }
        return new JSONObject().put("items", items).put("current", cur).toString();
    }

    String screenInfo() {
        if (!sized) {
            sized = true;
            try {
                java.util.regex.Matcher m = Pattern.compile("(\\d+)x(\\d+)").matcher(adb.exec("wm size", 10000));
                while (m.find()) { sw = Integer.parseInt(m.group(1)); sh = Integer.parseInt(m.group(2)); }
            } catch (Exception e) { sized = false; }
        }
        return "{\"w\": " + sw + ", \"h\": " + sh + "}";
    }
}
