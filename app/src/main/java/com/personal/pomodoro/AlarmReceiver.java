package com.personal.pomodoro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        // Always queue the next alert first, so the chain never breaks.
        Scheduler.schedule(c);
        if (!Scheduler.ACTION.equals(i.getAction())) return;

        int type = i.getIntExtra("type", 1);   // 1 = first alert, 2 = second, 3 = third ...
        long sid = i.getLongExtra("sid", 0);
        Scheduler.S s = Scheduler.find(c, sid);
        boolean withSound = s == null || s.sound;
        boolean withVibration = s == null || s.vibrate;

        Scheduler.deliver(c, type, withVibration);
        if (withSound) Scheduler.play(c, s == null ? "" : s.soundFor(type), goAsync());
    }
}
