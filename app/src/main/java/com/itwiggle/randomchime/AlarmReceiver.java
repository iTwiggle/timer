package com.itwiggle.randomchime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Intent service=new Intent(c,PlaybackService.class); service.setAction("PLAY"); c.startForegroundService(service);
        Store.addHistory(c,"prompted");
    }
}
