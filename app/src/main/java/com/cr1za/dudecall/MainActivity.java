package com.cr1za.dudecall;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.DisplayCutout;

import java.util.Locale;

/** One activity, one fullscreen custom-drawn arcade interface. */
public final class MainActivity extends Activity implements ArcadeView.Events {
    private static final String PREFS = "dude_call_once";
    private static final String PHONE = "permanent_number";
    private static final String PERMISSION_REQUESTS = "permission_attempts";
    private static final int PERMISSIONS = 41;
    private static final int SETTINGS = 42;
    private static final int OVERLAY_SETTINGS = 43;
    private static final int OVERLAY_CALL_PERMISSION = 44;
    private static final String OVERLAY_WANTED = "overlay_enabled";
    private static final int RED = Color.rgb(242, 13, 23);

    private SharedPreferences prefs;
    private ArcadeView arcade;
    private ArcadeAudio audio;
    private String savedNumber;
    private boolean launchBusy;
    private boolean settingsLaunched;
    private boolean overlayPromptShown;
    private boolean overlaySettingsLaunched;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(immersiveFlags());
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility ->
                getWindow().getDecorView().setSystemUiVisibility(immersiveFlags()));

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        savedNumber = prefs.getString(PHONE, null);
        audio = new ArcadeAudio(this);
        arcade = new ArcadeView(this, this, audio, savedNumber != null && !savedNumber.isEmpty());
        setContentView(arcade);
        handleWidgetAction(getIntent());
        arcade.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    arcade.setSafeInsets(cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                            cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
                }
            }
            return insets;
        });
    }

    private int immersiveFlags() {
        return View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused && arcade != null) arcade.cancelHeldPress();
        if (focused) getWindow().getDecorView().setSystemUiVisibility(immersiveFlags());
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (arcade != null) arcade.onDeviceConfigurationChanged();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWidgetAction(intent);
    }

    private void handleWidgetAction(Intent intent) {
        if (arcade == null || intent == null) return;
        String action = intent.getAction();
        if ("com.cr1za.dudecall.SHOW_PERMISSION_PANEL".equals(action)) {
            arcade.showPermissionPanel();
        } else if ("com.cr1za.dudecall.SHOW_CALL_ACTIVE".equals(action)) {
            arcade.showTimedMessage("CALL ALREADY ACTIVE", 2000);
        }
    }

    @Override public void onBackPressed() {
        if (arcade != null && arcade.dismissPermissionPanel()) return;
        // Unfinished setup is deliberately NOT saved here.
        finish();
    }

    @Override public void setPrivateScreen(boolean privateScreen) {
        if (privateScreen) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override public void lockRotation(boolean locked) {
        int orientation = locked ? ActivityInfo.SCREEN_ORIENTATION_LOCKED
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if (getRequestedOrientation() != orientation) setRequestedOrientation(orientation);
    }

    @Override public String formatNumber(String raw) {
        if (raw.isEmpty() || raw.equals("+")) return raw;
        String region = Locale.getDefault().getCountry();
        try {
            TelephonyManager manager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String iso = manager == null ? null : manager.getNetworkCountryIso();
            if (iso != null && !iso.isEmpty()) region = iso.toUpperCase(Locale.ROOT);
        } catch (SecurityException ignored) { }
        if (region.isEmpty()) region = "US";
        try {
            String formatted = PhoneNumberUtils.formatNumber(raw, region);
            return formatted != null && !formatted.isEmpty() ? formatted : raw;
        } catch (Exception ignored) { return raw; }
    }

    @Override public boolean saveNumber(String number) {
        if (!isPlausible(number)) return false;
        if (!prefs.edit().putString(PHONE, number).commit()) return false;
        savedNumber = number;
        // Present optional overlay setup only after the full confirmation transition.
        handler.postDelayed(this::offerFloatingButton, 1400);
        return true;
    }

    @Override public boolean isPlausible(String number) {
        if (number == null || !number.matches("\\+?[0-9]{5,15}")) return false;
        // Reject arbitrary strings of one repeated digit and globally invalid prefixes.
        String digits = number.replace("+", "");
        if (digits.matches("([0-9])\\1+")) return false;
        return !number.startsWith("+0") && PhoneNumberUtils.isGlobalPhoneNumber(number);
    }

    @Override public void requestCall() {
        if (savedNumber == null || launchBusy) return;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            int attempts = prefs.getInt(PERMISSION_REQUESTS, 0);
            if (attempts > 0 && !shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)) {
                arcade.showPermissionPanel();
                return;
            }
            prefs.edit().putInt(PERMISSION_REQUESTS, attempts + 1).apply();
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE}, PERMISSIONS);
            return;
        }
        if (callAlreadyActive()) {
            arcade.showTimedMessage("CALL ALREADY ACTIVE", 2000);
            return;
        }
        launchBusy = true;
        arcade.setCallLaunching(true);
        try {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", savedNumber, null));
            startActivity(intent);
            // A failed/no-op handoff must not leave the button frozen forever.
            handler.postDelayed(() -> {
                if (launchBusy) {
                    launchBusy = false;
                    arcade.setCallLaunching(false);
                }
            }, 5000);
        } catch (SecurityException | ActivityNotFoundException ex) {
            launchBusy = false;
            arcade.setCallLaunching(false);
            // Android handles normal call and network errors. No redundant custom toast.
        }
    }

    private boolean callAlreadyActive() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            return false;
        try {
            TelephonyManager telephony = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            return telephony != null && telephony.getCallState() != TelephonyManager.CALL_STATE_IDLE;
        } catch (RuntimeException ignored) { return false; }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == OVERLAY_CALL_PERMISSION) {
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
                startFloatingButton();
            else arcade.showTimedMessage("PHONE PERMISSION REQUIRED", 2000);
            return;
        }
        if (requestCode != PERMISSIONS) return;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            requestCall();
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)) {
            arcade.showPermissionPanel();
        } else {
            arcade.showTimedMessage("PHONE PERMISSION REQUIRED", 2000);
        }
    }

    @Override public void openSettings() {
        arcade.dismissPermissionPanel();
        Intent settings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        try {
            settingsLaunched = true;
            startActivityForResult(settings, SETTINGS);
        } catch (ActivityNotFoundException ignored) { settingsLaunched = false; }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_SETTINGS && overlaySettingsLaunched) {
            overlaySettingsLaunched = false;
            if (Settings.canDrawOverlays(this)) startFloatingButton();
            return;
        }
        if (requestCode == SETTINGS && settingsLaunched) {
            settingsLaunched = false;
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
                arcade.showTimedMessage("READY TO CALL", 2000);
            else arcade.showPermissionPanel();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (arcade != null) {
            arcade.refreshAudioRoute();
            if (launchBusy) { launchBusy = false; arcade.setCallLaunching(false); }
            if (savedNumber != null && !savedNumber.isEmpty() && !overlayPromptShown &&
                    !overlaySettingsLaunched) {
                handler.postDelayed(this::offerFloatingButton, 500);
            }
        }
    }

    /** Asked only from the visible activity, never from the background or at boot. */
    private void offerFloatingButton() {
        if (isFinishing() || isDestroyed() || overlayPromptShown ||
                savedNumber == null || savedNumber.isEmpty()) return;
        overlayPromptShown = true;
        if (prefs.getBoolean(OVERLAY_WANTED, false)) {
            startFloatingButton();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("FLOATING RED BUTTON")
                .setMessage("Keep your red call button on top of other apps. " +
                        "Android will ask you to allow Display over other apps. " +
                        "A notification will let you stop it anytime.")
                .setPositiveButton("ENABLE", (dialog, which) -> {
                    prefs.edit().putBoolean(OVERLAY_WANTED, true).apply();
                    startFloatingButton();
                })
                .setNegativeButton("NOT NOW", (dialog, which) -> { })
                .show();
    }

    private void startFloatingButton() {
        if (savedNumber == null || savedNumber.isEmpty() || isFinishing()) return;
        if (!Settings.canDrawOverlays(this)) {
            try {
                overlaySettingsLaunched = true;
                Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(permission, OVERLAY_SETTINGS);
            } catch (ActivityNotFoundException exception) {
                overlaySettingsLaunched = false;
                arcade.showTimedMessage("ALLOW DISPLAY OVER APPS", 2000);
            }
            return;
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE}, OVERLAY_CALL_PERMISSION);
            return;
        }
        try {
            Intent service = new Intent(this, FloatingCallService.class);
            service.setAction(FloatingCallService.ACTION_START);
            startForegroundService(service);
        } catch (RuntimeException ignored) {
            arcade.showTimedMessage("FLOATING BUTTON UNAVAILABLE", 2000);
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (audio != null) audio.release();
        super.onDestroy();
    }
}
