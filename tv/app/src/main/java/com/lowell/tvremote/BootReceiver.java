package com.lowell.tvremote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Logx.i("Acilis olayi: " + i.getAction());
        RemoteService.startIt(c);
    }
}
