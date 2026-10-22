package com.cr1za.dudecall;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.telephony.TelephonyManager;

/** Brief transparent permission/call hand-off so the home screen stays visible.
 *  Widget interactions may launch an Activity; using a broadcast to start a call
 *  could be blocked by modern Android background-activity restrictions.
 */
public final class WidgetCallActivity extends Activity {
    private static final String PREFS = "dude_call_once";
    private static final String PHONE = "permanent_number";
    private static final int REQUEST_CALL_PERMISSIONS = 61;
    private boolean started;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) started = state.getBoolean("started", false);
        if (!started) {
            started = true;
            callOrRequestPermission();
        }
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("started", started);
        super.onSaveInstanceState(outState);
    }

    private void callOrRequestPermission() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String number = prefs.getString(PHONE, null);
        if (number == null || number.trim().isEmpty()) {
            openMain(false); // First-run number entry stays inside the existing app.
            return;
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE}, REQUEST_CALL_PERMISSIONS);
            return;
        }
        placeCall(number);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CALL_PERMISSIONS) return;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            String number = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PHONE, null);
            if (number != null && !number.isEmpty()) placeCall(number);
            else openMain(false);
        } else {
            // Use the original glossy PHONE PERMISSION REQUIRED panel / OPEN SETTINGS link.
            openMain(true);
        }
    }

    private void placeCall(String number) {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                TelephonyManager manager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                if (manager != null && manager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                    // Already in a call: show the existing in-app warning rather than placing another call.
                    Intent show = new Intent(this, MainActivity.class);
                    show.setAction("com.cr1za.dudecall.SHOW_CALL_ACTIVE");
                    show.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(show);
                    finish();
                    return;
                }
            } catch (RuntimeException ignored) {
                // Call state not reliably available; permit the outgoing call normally.
            }
        }
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        long now = SystemClock.elapsedRealtime();
        long previous = prefs.getLong("last_widget_call_elapsed", 0L);
        if (previous > 0 && now >= previous && now - previous < 5000L) {
            // Double tapping the launcher widget must never place duplicate calls.
            finish();
            return;
        }
        prefs.edit().putLong("last_widget_call_elapsed", now).commit();
        try {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null));
            startActivity(intent);
        } catch (SecurityException | ActivityNotFoundException ignored) {
            prefs.edit().remove("last_widget_call_elapsed").apply();
            // Android controls phone/call failure dialogs. Do not silently retry or place two calls.
        } finally {
            finish();
        }
    }

    private void openMain(boolean showPermission) {
        Intent main = new Intent(this, MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (showPermission) main.setAction("com.cr1za.dudecall.SHOW_PERMISSION_PANEL");
        startActivity(main);
        finish();
    }
}
