package com.personal.pomodoro;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public final class Scheduler {
    static final String PREFS = "pomodoro";
    static final String KEY = "schedules_v3";
    static final String ACTION = "com.personal.pomodoro.ALERT";
    static final long MAX_PLAY_MS = 7000;
    static final int MAX_EXTRA = 5;

    /** First alert: three pulses. Every later alert: two pulses. */
    static final long[] P3 = {0, 400, 250, 400, 250, 400};
    static final long[] P2 = {0, 400, 250, 400};

    /** An extra alert that follows the first one: `value` seconds or minutes after the previous alert. */
    static class Alert {
        int value = 30;
        int unit = 0;                 // 0 = seconds, 1 = minutes
        String uri = "", name = "";   // empty uri = default notification tone

        Alert(int value, int unit) { this.value = value; this.unit = unit; }
        int seconds() { return value * (unit == 1 ? 60 : 1); }
    }

    /** One schedule: window in minutes after midnight, repeat interval, and its alerts. */
    static class S {
        long id;
        int start, end;
        int everyVal, everyUnit;      // everyUnit: 0 = minutes, 1 = hours
        boolean on;
        boolean sound = true, vibrate = true;
        String snd1 = "", n1 = "";    // first alert sound
        List<Alert> extra = new ArrayList<>();
        boolean open;                 // UI only, not saved

        S(long id, int start, int end, int everyVal, int everyUnit, boolean on) {
            this.id = id; this.start = start; this.end = end;
            this.everyVal = everyVal; this.everyUnit = everyUnit; this.on = on;
        }

        int everyMin() { return Math.max(1, everyVal * (everyUnit == 1 ? 60 : 1)); }

        String soundFor(int type) {
            if (type == 1) return snd1;
            int k = type - 2;
            return k >= 0 && k < extra.size() ? extra.get(k).uri : "";
        }
    }

    // ---------- storage ----------

    static List<S> defaults() {
        List<S> list = new ArrayList<>();
        S a = new S(1, 8 * 60, 10 * 60, 25, 0, true);
        a.extra.add(new Alert(30, 0));
        S b = new S(2, 18 * 60, 22 * 60, 25, 0, true);
        b.extra.add(new Alert(30, 0));
        list.add(a);
        list.add(b);
        return list;
    }

    static List<S> load(Context c) {
        String raw = c.getSharedPreferences(PREFS, 0).getString(KEY, null);
        if (raw == null) return defaults();
        List<S> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                S s = new S(o.getLong("id"), o.getInt("start"), o.getInt("end"),
                        o.getInt("ev"), o.getInt("eu"), o.getBoolean("on"));
                s.sound = o.optBoolean("sound", true);
                s.vibrate = o.optBoolean("vib", true);
                s.snd1 = o.optString("snd1", "");
                s.n1 = o.optString("n1", "");
                JSONArray ex = o.optJSONArray("extra");
                if (ex != null) {
                    for (int k = 0; k < ex.length(); k++) {
                        JSONObject ao = ex.getJSONObject(k);
                        Alert al = new Alert(ao.getInt("v"), ao.getInt("u"));
                        al.uri = ao.optString("uri", "");
                        al.name = ao.optString("name", "");
                        s.extra.add(al);
                    }
                }
                list.add(s);
            }
        } catch (Exception e) {
            return defaults();
        }
        return list;
    }

    static void save(Context c, List<S> list) {
        try {
            JSONArray a = new JSONArray();
            for (S s : list) {
                JSONObject o = new JSONObject();
                o.put("id", s.id).put("start", s.start).put("end", s.end)
                 .put("ev", s.everyVal).put("eu", s.everyUnit).put("on", s.on)
                 .put("sound", s.sound).put("vib", s.vibrate)
                 .put("snd1", s.snd1).put("n1", s.n1);
                JSONArray ex = new JSONArray();
                for (Alert al : s.extra) {
                    JSONObject ao = new JSONObject();
                    ao.put("v", al.value).put("u", al.unit).put("uri", al.uri).put("name", al.name);
                    ex.put(ao);
                }
                o.put("extra", ex);
                a.put(o);
            }
            c.getSharedPreferences(PREFS, 0).edit().putString(KEY, a.toString()).apply();
        } catch (Exception e) {
            // ignore
        }
    }

    static S find(Context c, long id) {
        for (S s : load(c)) if (s.id == id) return s;
        return null;
    }

    static boolean master(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean("master", true); }
    static void setMaster(Context c, boolean v) { c.getSharedPreferences(PREFS, 0).edit().putBoolean("master", v).apply(); }
    static long pauseUntil(Context c) { return c.getSharedPreferences(PREFS, 0).getLong("pause", 0); }
    static void setPause(Context c, long t) { c.getSharedPreferences(PREFS, 0).edit().putLong("pause", t).apply(); }

    // ---------- timing ----------

    static long midnight(int addDays) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, addDays);
        return cal.getTimeInMillis();
    }

    /** Next alert after `from`: {timeMillis, alertNumber, scheduleId}. alertNumber 1 = first alert, 2 = second ... */
    static long[] nextFrom(List<S> list, long from, boolean strict) {
        long best = Long.MAX_VALUE, type = 0, sid = 0;
        for (int d = 0; d < 2; d++) {
            long base = midnight(d);
            for (S s : list) {
                if (!s.on) continue;
                int ev = s.everyMin();
                for (int m = s.start + ev; m <= s.end; m += ev) {
                    long t = base + m * 60000L;
                    if (t > from && t < best) { best = t; type = 1; sid = s.id; }
                    long at = t;
                    for (int k = 0; k < s.extra.size(); k++) {
                        at += s.extra.get(k).seconds() * 1000L;
                        if (at > from && (!strict || t > from) && at < best) { best = at; type = k + 2; sid = s.id; }
                    }
                }
            }
        }
        return best == Long.MAX_VALUE ? null : new long[]{best, type, sid};
    }

    /** Next alert, honouring the master switch and "pause". */
    static long[] next(Context c, List<S> list, long now) {
        if (!master(c)) return null;
        long pause = pauseUntil(c);
        if (pause > now) return nextFrom(list, pause, true);
        return nextFrom(list, now, false);
    }

    /** Times of the next first-alerts, soonest first. */
    static List<Long> upcoming(List<S> list, long from, int max) {
        List<Long> out = new ArrayList<>();
        for (int d = 0; d < 2; d++) {
            long base = midnight(d);
            for (S s : list) {
                if (!s.on) continue;
                int ev = s.everyMin();
                for (int m = s.start + ev; m <= s.end; m += ev) {
                    long t = base + m * 60000L;
                    if (t > from) out.add(t);
                }
            }
        }
        Collections.sort(out);
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    static int leftToday(List<S> list, long now) {
        int n = 0;
        long base = midnight(0);
        for (S s : list) {
            if (!s.on) continue;
            int ev = s.everyMin();
            for (int m = s.start + ev; m <= s.end; m += ev) {
                if (base + m * 60000L > now) n++;
            }
        }
        return n;
    }

    static PendingIntent pending(Context c, int type, long sid) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION)
                .putExtra("type", type).putExtra("sid", sid);
        return PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Sets ONE alarm for the next alert. Called after every alert, boot and settings change. */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long[] n = next(c, load(c), System.currentTimeMillis());
        if (n == null) {
            am.cancel(pending(c, 0, 0));
            return;
        }
        PendingIntent pi = pending(c, (int) n[1], n[2]);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n[0], pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n[0], pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n[0], pi);
        }
    }

    // ---------- alert output ----------

    private static void ensureChannels(NotificationManager nm) {
        NotificationChannel v3 = new NotificationChannel("alert_v3", "Alerts (3 pulses)", NotificationManager.IMPORTANCE_HIGH);
        v3.setSound(null, null);
        v3.enableVibration(true);
        v3.setVibrationPattern(P3);
        NotificationChannel v2 = new NotificationChannel("alert_v2", "Later alerts (2 pulses)", NotificationManager.IMPORTANCE_HIGH);
        v2.setSound(null, null);
        v2.enableVibration(true);
        v2.setVibrationPattern(P2);
        NotificationChannel v0 = new NotificationChannel("alert_v0", "Alerts (no vibration)", NotificationManager.IMPORTANCE_HIGH);
        v0.setSound(null, null);
        v0.enableVibration(false);
        nm.createNotificationChannel(v3);
        nm.createNotificationChannel(v2);
        nm.createNotificationChannel(v0);
    }

    /**
     * Shows the alert banner and vibrates. Vibration is requested two independent ways: through the
     * notification channel (Android's own path) and directly on the alarm channel. If one is blocked
     * by a phone setting, the other still runs.
     */
    static void deliver(Context c, int type, boolean vib) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            ensureChannels(nm);
            String channel = !vib ? "alert_v0" : type == 1 ? "alert_v3" : "alert_v2";
            Notification n = new Notification.Builder(c, channel)
                    .setSmallIcon(R.drawable.ic_stat_timer)
                    .setContentTitle(type == 1 ? "Break time" : type == 2 ? "Back to work" : "Reminder " + type)
                    .setContentText(type == 1 ? "Stand up and rest."
                            : type == 2 ? "2nd alert. Break is over." : "Alert " + type + " of your break.")
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class),
                            PendingIntent.FLAG_IMMUTABLE))
                    .build();
            nm.notify(type, n);
        } catch (Exception e) {
            // banner is optional
        }
        if (vib) vibrateDirect(c, type);
    }

    private static void vibrateDirect(Context c, int type) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            long[] p = type == 1 ? P3 : P2;
            VibrationEffect fx;
            if (v.hasAmplitudeControl()) {
                int[] amps = new int[p.length];
                for (int i = 1; i < p.length; i += 2) amps[i] = 255; // full strength on every pulse
                fx = VibrationEffect.createWaveform(p, amps, -1);
            } else {
                fx = VibrationEffect.createWaveform(p, -1);
            }
            v.vibrate(fx, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * Plays the chosen file, or the default notification tone when uriStr is empty.
     * Falls back to the default tone if the chosen file can no longer be played.
     * `pr` (nullable) is finished when playback ends, so the phone can sleep again.
     */
    static void play(Context c, String uriStr, BroadcastReceiver.PendingResult pr) {
        Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        Uri def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        boolean custom = uriStr != null && !uriStr.isEmpty();
        try {
            new Playback(app, custom ? Uri.parse(uriStr) : def, custom ? def : null, pr);
        } catch (Exception e) {
            if (custom) {
                try {
                    new Playback(app, def, null, pr);
                    return;
                } catch (Exception e2) {
                    // fall through
                }
            }
            if (pr != null) pr.finish();
        }
    }

    private static final class Playback implements MediaPlayer.OnPreparedListener,
            MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, Runnable {
        private final MediaPlayer mp = new MediaPlayer();
        private final Handler h = new Handler(Looper.getMainLooper());
        private final Context ctx;
        private final Uri fallback;
        private final BroadcastReceiver.PendingResult pr;
        private boolean done;

        Playback(Context ctx, Uri uri, Uri fallback, BroadcastReceiver.PendingResult pr) throws Exception {
            this.ctx = ctx;
            this.fallback = fallback;
            this.pr = pr;
            try {
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                mp.setDataSource(ctx, uri);
                mp.setOnPreparedListener(this);
                mp.setOnCompletionListener(this);
                mp.setOnErrorListener(this);
                mp.prepareAsync();
            } catch (Exception e) {
                mp.release();
                throw e;
            }
            h.postDelayed(this, MAX_PLAY_MS);
        }

        @Override public void onPrepared(MediaPlayer m) { m.start(); }

        @Override public void onCompletion(MediaPlayer m) { finish(); }

        @Override public void run() { finish(); } // time limit reached

        @Override public boolean onError(MediaPlayer m, int what, int extra) {
            if (done) return true;
            done = true;
            h.removeCallbacks(this);
            mp.release();
            if (fallback != null) {
                try {
                    new Playback(ctx, fallback, null, pr);
                    return true;
                } catch (Exception e) {
                    // fall through
                }
            }
            if (pr != null) pr.finish();
            return true;
        }

        private void finish() {
            if (done) return;
            done = true;
            h.removeCallbacks(this);
            mp.release();
            if (pr != null) pr.finish();
        }
    }
}
