package com.lowell.tvremote;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/** TV ekrani: sunucu durumu, adres, log. */
public class TvActivity extends AppCompatActivity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView tServer, tAdb, tHid, tPhones, tUrl, tHint, tLog;
    private ScrollView scroll;
    private String lastLog = "";

    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); h.postDelayed(this, 1000); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_tv);
        tServer = findViewById(R.id.t_server); tAdb = findViewById(R.id.t_adb); tHid = findViewById(R.id.t_hid);
        tPhones = findViewById(R.id.t_phones); tUrl = findViewById(R.id.t_url); tHint = findViewById(R.id.t_hint);
        tLog = findViewById(R.id.t_log); scroll = findViewById(R.id.scroll);
        MaterialButton restart = findViewById(R.id.b_restart), clear = findViewById(R.id.b_clear);
        restart.setOnClickListener(v -> { Backend.I.restart(this); RemoteService.startIt(this); });
        clear.setOnClickListener(v -> { Logx.clear(); lastLog = ""; refresh(); });
        restart.requestFocus();
        RemoteService.startIt(this);
    }

    @Override protected void onResume() { super.onResume(); h.post(tick); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }

    private void dot(TextView tv, boolean ok, boolean warn, String label, String text) {
        int col = ok ? 0xFF7DDC8C : (warn ? 0xFFFFC857 : 0xFFFF6B6B);
        SpannableStringBuilder sb = new SpannableStringBuilder("●  " + label + ": " + text);
        sb.setSpan(new ForegroundColorSpan(col), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(sb);
    }

    private void refresh() {
        Backend be = Backend.I;
        dot(tServer, be.serverUp, false, "Sunucu", be.serverUp ? "calisiyor (port " + Backend.PORT + ")" : be.serverMsg);
        boolean awaiting = !be.adbConnected() && be.adbState().contains("onayla");
        dot(tAdb, be.adbConnected(), awaiting, "ADB", be.adbConnected() ? "bagli" : be.adbState());
        dot(tHid, be.hidReady(), false, "Sanal fare/klavye", be.hidReady() ? "hazir" : "bekliyor");
        dot(tPhones, be.clients() > 0, be.clients() == 0, "Telefon", be.clients() + " bagli, " + be.requests() + " istek");
        tUrl.setText("http://" + Backend.lanIp() + ":" + Backend.PORT);
        tHint.setText(awaiting ? "Ekrandaki 'USB hata ayiklamasina izin verilsin mi?' penceresinde \"Her zaman izin ver\"i sec."
                : "Telefondan bu adresi ac ya da telefon uygulamasini kullan.");
        String log = Logx.tail(60);
        if (!log.equals(lastLog)) { lastLog = log; tLog.setText(log); scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN)); }
    }
}
