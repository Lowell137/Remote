package com.lowell.tvremote;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/** UHID sanal cihaz tanimlari ve rapor uretimi (server.py'nin birebir portu). */
final class Hid {
    static final byte[] MOUSE_RD = hex("05010902a1010901a100050919012903150025019503750181029501750581030501093009310938158125 7f750895038106c0c0");
    static final byte[] KBD_RD = hex("05010906a101050719e029e715002501750195088102950175088101950575010508190129059102950175039101950675081500256505071900296581 00c0");
    static final byte[] CONS_RD = hex("050c0901a1011500 26ff0319002aff03751095018100c0");

    static final int FD_MOUSE = 3, FD_KBD = 4, FD_MEDIA = 5;

    static final Map<String, Integer> KEYS = new HashMap<>();
    static final Map<String, Integer> CONS = new HashMap<>();

    static {
        KEYS.put("right", 0x4F); KEYS.put("left", 0x50); KEYS.put("down", 0x51); KEYS.put("up", 0x52);
        KEYS.put("enter", 0x28); KEYS.put("esc", 0x29); KEYS.put("backspace", 0x2A); KEYS.put("tab", 0x2B);
        KEYS.put("space", 0x2C); KEYS.put("delete", 0x4C); KEYS.put("home_key", 0x4A); KEYS.put("end", 0x4D);
        KEYS.put("pgup", 0x4B); KEYS.put("pgdn", 0x4E); KEYS.put("menu_key", 0x65);
        CONS.put("vol_up", 0xE9); CONS.put("vol_down", 0xEA); CONS.put("mute", 0xE2); CONS.put("home", 0x223);
        CONS.put("back", 0x224); CONS.put("play_pause", 0xCD); CONS.put("next", 0xB5); CONS.put("prev", 0xB6);
        CONS.put("menu", 0x40); CONS.put("search", 0x221); CONS.put("power", 0x30);
    }

    static byte[] hex(String s) {
        s = s.replace(" ", "");
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    static byte[] createEvent(String name, byte[] rd) {
        ByteBuffer b = ByteBuffer.allocate(4 + 128 + 128 + 2 + 2 + 4 + 4 + 4 + 4 + rd.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(11); // UHID_CREATE2
        byte[] n = name.getBytes();
        byte[] nm = new byte[128];
        System.arraycopy(n, 0, nm, 0, Math.min(n.length, 127));
        b.put(nm);
        b.put(new byte[128]); // phys + uniq
        b.putShort((short) rd.length);
        b.putShort((short) 3); // BUS_USB
        b.putInt(0x1209); b.putInt(0x7E11); b.putInt(1); b.putInt(0);
        b.put(rd);
        return b.array();
    }

    /** printf '\ooo...' >&fd  satiri */
    static String reportCmd(int fd, byte[] data) {
        ByteBuffer b = ByteBuffer.allocate(6 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(12); // UHID_INPUT2
        b.putShort((short) data.length);
        b.put(data);
        return "printf '" + octal(b.array()) + "' >&" + fd;
    }

    static String octal(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 4);
        for (byte x : a) {
            int v = x & 0xFF;
            sb.append('\\').append((char) ('0' + (v >> 6))).append((char) ('0' + ((v >> 3) & 7))).append((char) ('0' + (v & 7)));
        }
        return sb.toString();
    }

    static int clamp(int v) { return Math.max(-127, Math.min(127, v)); }

    static byte[] mouseReport(int btn, int dx, int dy, int wheel) {
        return new byte[]{(byte) (btn & 7), (byte) clamp(dx), (byte) clamp(dy), (byte) clamp(wheel)};
    }

    /** {kod, mod} ya da null */
    static int[] asciiKey(char ch) {
        if (ch >= 'a' && ch <= 'z') return new int[]{0x04 + ch - 'a', 0};
        if (ch >= 'A' && ch <= 'Z') return new int[]{0x04 + ch - 'A', 2};
        if (ch >= '1' && ch <= '9') return new int[]{0x1E + ch - '1', 0};
        if (ch == '0') return new int[]{0x27, 0};
        String plain = " -=[]\\;'`,./\n";
        int[] pc = {0x2C, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x28};
        int i = plain.indexOf(ch);
        if (i >= 0) return new int[]{pc[i], 0};
        String sh = "!@#$%^&*()_+{}|:\"~<>?";
        int[] sc = {0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38};
        i = sh.indexOf(ch);
        if (i >= 0) return new int[]{sc[i], 2};
        return null;
    }

    static String[] keyCmds(int code, int mod) {
        return new String[]{reportCmd(FD_KBD, new byte[]{(byte) mod, 0, (byte) code, 0, 0, 0, 0, 0}), reportCmd(FD_KBD, new byte[8])};
    }
}
