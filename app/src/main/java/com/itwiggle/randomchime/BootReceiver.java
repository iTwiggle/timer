package com.itwiggle.randomchime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) { if(Store.p(c).getBoolean("armed",false)) Scheduler.scheduleDay(c); }
}
