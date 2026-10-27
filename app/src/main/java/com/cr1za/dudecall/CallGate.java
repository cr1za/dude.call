package com.cr1za.dudecall;

import android.content.SharedPreferences;
import android.os.SystemClock;

/** One five-second outgoing-call gate for every app surface. No silent retries. */
final class CallGate {
    private static final String KEY = "last_widget_call_elapsed";
    private CallGate() { }

    static synchronized boolean tryAcquire(SharedPreferences prefs) {
        long now = SystemClock.elapsedRealtime();
        long last = prefs.getLong(KEY, 0L);
        if (last > 0 && now >= last && now - last < 5000L) return false;
        return prefs.edit().putLong(KEY, now).commit();
    }

    static synchronized void rollback(SharedPreferences prefs) {
        prefs.edit().remove(KEY).commit();
    }
}
