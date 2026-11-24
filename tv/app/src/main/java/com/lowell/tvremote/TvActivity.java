package com.lowell.tvremote;

import android.content.SharedPreferences;
import android.content.res.Configuration;
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

import java.util.Locale;

/** TV ekrani: sunucu durumu, adres, log. TR / EN. */
public class TvActivity extends AppCompatActivity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView tServer, tAdb, tHid, tPhones, tUrl, tHint, tLog;
    private ScrollView scroll;
    private String lastLog = "";
    private boolean en;

    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); h.postDelayed(this, 1000); }
    };

    private void applyLocale() {
        SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
        en = "en".equals(p.getString("lang", "tr"));
        Locale loc = new Locale(en ? "en" : "tr");
        Locale.setDefault(loc);
        Configuration cfg = new Configuration(getResources().getConfiguration());
        cfg.setLocale(loc);
        getResources().updateConfiguration(cfg, getResources().getDisplayMetrics());
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        applyLocale();
        setContentView(R.layout.activity_tv);
        tServer = findViewById(R.id.t_server); tAdb = findViewById(R.id.t_adb); tHid = findViewById(R.id.t_hid);
        tPhones = findViewById(R.id.t_phones); tUrl = findViewById(R.id.t_url); tHint = findViewById(R.id.t_hint);
        tLog = findViewById(R.id.t_log); scroll = findViewById(R.id.scroll);
        MaterialButton restart = findViewById(R.id.b_restart), clear = findViewById(R.id.b_clear);
        MaterialButton lang = findViewById(R.id.b_lang);
        restart.setOnClickListener(v -> { Backend.I.restart(this); RemoteService.startIt(this); });
        clear.setOnClickListener(v -> { Logx.clear(); lastLog = ""; refresh(); });
        lang.setText(en ? R.string.lang_tr : R.string.lang_en);
        lang.setOnClickListener(v -> {
            getSharedPreferences("settings", MODE_PRIVATE).edit().putString("lang", en ? "tr" : "en").apply();
            recreate();
        });
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
        dot(tServer, be.serverUp, false, getString(R.string.label_server),
                be.serverUp ? getString(R.string.status_running, Backend.PORT) : be.serverMsg);
        boolean awaiting = !be.adbConnected() && be.adbState().contains("onayla");
        dot(tAdb, be.adbConnected(), awaiting, getString(R.string.label_adb),
                be.adbConnected() ? getString(R.string.status_connected) : be.adbState());
        dot(tHid, be.hidReady(), false, getString(R.string.label_hid),
                be.hidReady() ? getString(R.string.status_ready) : getString(R.string.status_waiting));
        dot(tPhones, be.clients() > 0, be.clients() == 0, getString(R.string.label_phone),
                getString(R.string.phones, be.clients(), be.requests()));
        tUrl.setText("http://" + Backend.lanIp() + ":" + Backend.PORT);
        tHint.setText(awaiting ? getString(R.string.hint_awaiting) : getString(R.string.hint_normal));
        String log = Logx.tail(60);
        if (!log.equals(lastLog)) { lastLog = log; tLog.setText(log); scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN)); }
    }
}
