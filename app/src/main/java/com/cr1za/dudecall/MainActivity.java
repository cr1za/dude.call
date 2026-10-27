package com.cr1za.dudecall;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.widget.LinearLayout;
import android.widget.TextView;
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
import android.view.DisplayCutout;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

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
    private static final int OVERLAY_NOTIFICATION_PERMISSION = 45;
    private static final String NOTIFICATION_ASKED = "overlay_notification_asked";
    private static final String OVERLAY_WANTED = "overlay_enabled";
    private static final int RED = Color.rgb(242, 13, 23);

    private SharedPreferences prefs;
    private ArcadeView arcade;
    private ArcadeAudio audio;
    private String savedNumber;
    private boolean launchBusy;
    private boolean settingsLaunched;
    private boolean overlaySettingsLaunched;
    private boolean overlayEnablePending;
    private long undoDeadline;
    private OnBackInvokedCallback modernBackCallback;
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
        if (Build.VERSION.SDK_INT >= 33) {
            modernBackCallback = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, modernBackCallback);
        }
        handleWidgetAction(getIntent());
        arcade.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                DisplayCutout cutout = insets.getDisplayCutout();
                arcade.setSafeInsets(cutout == null ? 0 : cutout.getSafeInsetLeft(),
                        cutout == null ? 0 : cutout.getSafeInsetTop(),
                        cutout == null ? 0 : cutout.getSafeInsetRight(),
                        cutout == null ? 0 : cutout.getSafeInsetBottom());
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

    private void handleBack() {
        if (arcade != null && arcade.dismissPermissionPanel()) return;
        // Unfinished setup is deliberately NOT saved here.
        finish();
    }

    @Override public void onBackPressed() {
        handleBack();
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
        return true;
    }

    @Override public boolean isPlausible(String number) {
        if (number == null || !number.matches("\\+?[0-9]{5,15}")) return false;
        // Reject arbitrary strings of one repeated digit and globally invalid prefixes.
        String digits = number.replace("+", "");
        if (digits.matches("([0-9])\\1+")) return false;
        return !number.startsWith("+0") && PhoneNumberUtils.isGlobalPhoneNumber(number);
    }

    @Override public boolean isFloatingEnabled() {
        return prefs != null && prefs.getBoolean(OVERLAY_WANTED, false) &&
                FloatingCallService.isVisible();
    }

    @Override public boolean isFloatingUndoAvailable() {
        return SystemClock.uptimeMillis() < undoDeadline;
    }

    @Override public void toggleFloating() {
        if (isFloatingEnabled()) {
            disableFloating(true);
        } else if (isFloatingUndoAvailable()) {
            undoDeadline = 0;
            overlayEnablePending = true;
            continueFloatingEnable();
        } else {
            overlayEnablePending = true;
            continueFloatingEnable();
        }
        arcade.invalidate();
    }

    private void disableFloating(boolean offerUndo) {
        overlayEnablePending = false;
        prefs.edit().putBoolean(OVERLAY_WANTED, false).apply();
        undoDeadline = offerUndo ? SystemClock.uptimeMillis() + 3000 : 0;
        Intent stop = new Intent(this, FloatingCallService.class).setAction(FloatingCallService.ACTION_STOP);
        // This also works if the service is already gone.
        try { startService(stop); } catch (RuntimeException ignored) { stopService(stop); }
        arcade.invalidate();
        if (offerUndo) {
            long deadline = undoDeadline;
            handler.postDelayed(() -> {
                if (undoDeadline == deadline) { undoDeadline = 0; arcade.invalidate(); }
            }, 3000);
        }
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
        if (!CallGate.tryAcquire(prefs)) return;
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
            CallGate.rollback(prefs);
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
                continueFloatingEnable();
            else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)) {
                    arcade.showPermissionPanel();
                } else {
                    overlayEnablePending = false;
                    arcade.showTimedMessage("PHONE PERMISSION REQUIRED", 2000);
                }
            }
            return;
        }
        if (requestCode == OVERLAY_NOTIFICATION_PERMISSION) {
            // Notification permission is optional. An in-app OFF control remains available.
            continueFloatingEnable();
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
            if (Settings.canDrawOverlays(this) && overlayEnablePending) continueFloatingEnable();
            else {
                overlayEnablePending = false;
                prefs.edit().putBoolean(OVERLAY_WANTED, false).apply();
                arcade.invalidate();
            }
            return;
        }
        if (requestCode == SETTINGS && settingsLaunched) {
            settingsLaunched = false;
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                if (overlayEnablePending) continueFloatingEnable();
                else arcade.showTimedMessage("READY TO CALL", 2000);
            } else {
                overlayEnablePending = false;
                arcade.showPermissionPanel();
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (arcade != null) {
            arcade.refreshAudioRoute();
            if (launchBusy) { launchBusy = false; arcade.setCallLaunching(false); }
            // Do NOT auto-start, and do not race the service's asynchronous launch.
            // The UI reads the live service state, not just the saved flag.
            arcade.invalidate();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showOverlayPermissionExplanation() {
        if (isFinishing()) return;
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF68121A, 0xFF270408});
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(2), RED);
        body.setBackground(bg);
        TextView title = new TextView(this);
        title.setText("FLOATING BUTTON");
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(android.view.Gravity.CENTER);
        body.addView(title);
        TextView text = new TextView(this);
        text.setText("Allow OBIC to display the red call button over other apps? " +
                "Android requires your permission. You can turn it OFF anytime.");
        text.setTextColor(Color.WHITE);
        text.setTextSize(15);
        text.setGravity(android.view.Gravity.CENTER);
        text.setPadding(0,dp(16),0,dp(16));
        body.addView(text);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(body)
                .setPositiveButton("OPEN SETTINGS", (d, which) -> openOverlaySettings())
                .setNegativeButton("CANCEL", (d, which) -> {
                    overlayEnablePending = false;
                    arcade.invalidate();
                })
                .create();
        dialog.setOnCancelListener(d -> { overlayEnablePending = false; arcade.invalidate(); });
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(RED);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
        });
        dialog.show();
    }

    private void openOverlaySettings() {
        try {
            overlaySettingsLaunched = true;
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_SETTINGS);
        } catch (RuntimeException failure) {
            overlaySettingsLaunched = false;
            overlayEnablePending = false;
            arcade.showTimedMessage("ALLOW DISPLAY OVER APPS", 2000);
        }
    }

    private void continueFloatingEnable() {
        if (!overlayEnablePending || savedNumber == null || isFinishing()) return;
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionExplanation();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            int attempts = prefs.getInt(PERMISSION_REQUESTS, 0);
            if (attempts > 0 && !shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)) {
                arcade.showPermissionPanel();
                return;
            }
            prefs.edit().putInt(PERMISSION_REQUESTS, attempts + 1).apply();
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE}, OVERLAY_CALL_PERMISSION);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                !prefs.getBoolean(NOTIFICATION_ASKED, false)) {
            prefs.edit().putBoolean(NOTIFICATION_ASKED, true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, OVERLAY_NOTIFICATION_PERMISSION);
            return;
        }
        overlayEnablePending = false;
        undoDeadline = 0;
        prefs.edit().putBoolean(OVERLAY_WANTED, true).apply();
        try {
            Intent service = new Intent(this, FloatingCallService.class).setAction(FloatingCallService.ACTION_START);
            startForegroundService(service);
            handler.postDelayed(() -> arcade.invalidate(), 300);
        } catch (RuntimeException e) {
            prefs.edit().putBoolean(OVERLAY_WANTED, false).apply();
            arcade.showTimedMessage("FLOATING BUTTON UNAVAILABLE", 2000);
        }
    }

    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && modernBackCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(modernBackCallback);
        }
        handler.removeCallbacksAndMessages(null);
        if (audio != null) audio.release();
        super.onDestroy();
    }
}
