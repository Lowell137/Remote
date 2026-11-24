package com.lowell.tvremote;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Bellekte tutulan halka log: TV ekraninda ve /log adresinde gosterilir. */
final class Logx {
    private static final int MAX = 400;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    static void i(String m) { add("I", m); Log.i("TVK", m); }
    static void w(String m) { add("W", m); Log.w("TVK", m); }
    static void e(String m) { add("E", m); Log.e("TVK", m); }

    private static synchronized void add(String lvl, String m) {
        LINES.addLast(FMT.format(new Date()) + " " + lvl + " " + m);
        while (LINES.size() > MAX) LINES.removeFirst();
    }

    static synchronized String tail(int n) {
        StringBuilder sb = new StringBuilder();
        int skip = Math.max(0, LINES.size() - n), k = 0;
        for (String s : LINES) { if (k++ >= skip) sb.append(s).append('\n'); }
        return sb.toString();
    }

    static synchronized void clear() { LINES.clear(); }
}
