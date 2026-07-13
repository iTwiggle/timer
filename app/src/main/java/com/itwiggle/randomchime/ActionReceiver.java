package com.itwiggle.randomchime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String action=i.getAction(); c.stopService(new Intent(c,PlaybackService.class));
        if("DONE".equals(action)){ Store.addHistory(c,"done"); }
        else if("SNOOZE".equals(action)){ Store.addHistory(c,"snoozed"); Scheduler.scheduleAt(c,System.currentTimeMillis()+10*60*1000L,1099); }
    }
}
