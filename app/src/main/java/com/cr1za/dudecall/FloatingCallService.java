package com.cr1za.dudecall;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

/** An opt-in transparent button that can be dragged over other apps.
 * Uses a visible foreground-service notification, with Stop action.
 * Never starts automatically at boot and never initiates calls without a tap.
 */
public final class FloatingCallService extends Service {
    static final String ACTION_START = "com.cr1za.dudecall.FLOAT_START";
    static final String ACTION_STOP = "com.cr1za.dudecall.FLOAT_STOP";
    private static final String PREFS = "dude_call_once";
    private static final String NUMBER = "permanent_number";
    private static final String KEY_X = "overlay_x";
    private static final String KEY_Y = "overlay_y";
    private static final String KEY_LAST_CALL = "last_widget_call_elapsed";
    private static final String CHANNEL = "dude_call_floating_button";
    private static final int NOTIFICATION_ID = 1212;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private ImageView button;
    private Bitmap image;
    private ArcadeAudio audio;
    private SharedPreferences prefs;
    private int touchSlop;
    private int buttonWidth;
    private int buttonHeight;
    private boolean isPressed;
    private boolean canceled;
    private boolean dragging;
    private boolean launching;
    private int trackedPointer = -1;
    private float downRawX, downRawY;
    private long downTime;
    private int startWindowX, startWindowY;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this) ||
                prefs.getString(NUMBER, null) == null ||
                checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }
        createNotificationChannel();
        try {
            // Started by visible MainActivity only; Android 14+ requires a declared FGS type.
            startForeground(NOTIFICATION_ID, buildNotification());
            if (button == null) attachOverlay();
        } catch (RuntimeException error) {
            stopSelf();
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Floating call button", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Controls the always-on-top dude.call red button");
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent main = PendingIntent.getActivity(this, 2, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, FloatingCallService.class).setAction(ACTION_STOP);
        PendingIntent stopAction = PendingIntent.getService(this, 3, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("dude.call floating button")
                .setContentText("Tap the red button to call; hold and drag to move")
                .setContentIntent(main)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopAction)
                .build();
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private int[] availableDimensions() {
        DisplayMetrics m = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(m);
        return new int[]{m.widthPixels, m.heightPixels};
    }

    private void attachOverlay() {
        image = BitmapFactory.decodeResource(getResources(), R.drawable.floating_arcade_button);
        if (image == null) { stopSelf(); return; }
        buttonWidth = dp(170);
        buttonHeight = Math.round(buttonWidth * (image.getHeight() / (float)image.getWidth()));
        int[] screen = availableDimensions();
        int defaultX = Math.max(dp(8), screen[0] - buttonWidth - dp(12));
        int defaultY = Math.max(dp(60), screen[1]/2 - buttonHeight/2);
        params = new WindowManager.LayoutParams(buttonWidth, buttonHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = prefs.getInt(KEY_X, defaultX);
        params.y = prefs.getInt(KEY_Y, defaultY);
        clampPosition();

        button = new ImageView(this);
        button.setImageBitmap(image);
        button.setScaleType(ImageView.ScaleType.FIT_XY);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription("Call now. Touch and release to call; hold and drag to move.");
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        button.setOnTouchListener(this::handleTouch);
        audio = new ArcadeAudio(this);
        audio.refreshRoute();
        windowManager.addView(button, params);
    }

    /** Reject touches that land on the black base or transparent margins. */
    private boolean touchesRedDome(float x, float y) {
        if (image == null || buttonWidth <= 0 || buttonHeight <= 0) return false;
        int ix = Math.min(image.getWidth()-1, Math.max(0, (int)(x*image.getWidth()/buttonWidth)));
        int iy = Math.min(image.getHeight()-1, Math.max(0, (int)(y*image.getHeight()/buttonHeight)));
        int color = image.getPixel(ix, iy);
        if (Color.alpha(color) < 120) return false;
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return r > 95 && r > g*1.5f && r > b*1.25f;
    }

    private void resetPressVisual() {
        if (button != null) {
            button.animate().cancel();
            button.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        }
    }

    private boolean handleTouch(View view, MotionEvent event) {
        int type = event.getActionMasked();
        if (type == MotionEvent.ACTION_DOWN) {
            if (launching || !touchesRedDome(event.getX(), event.getY())) return false;
            trackedPointer = event.getPointerId(0);
            downRawX = event.getRawX();
            downRawY = event.getRawY();
            startWindowX = params.x;
            startWindowY = params.y;
            downTime = SystemClock.uptimeMillis();
            isPressed = true;
            canceled = false;
            dragging = false;
            button.animate().cancel();
            button.animate().scaleX(.965f).scaleY(.945f).setDuration(65).start();
            audio.play(ArcadeAudio.Effect.PRESS, false);
            return true;
        }
        if (trackedPointer < 0) return false;
        if (type == MotionEvent.ACTION_POINTER_DOWN) {
            // Extra fingers are ignored.
            return true;
        }
        if (type == MotionEvent.ACTION_POINTER_UP) {
            if (event.getPointerId(event.getActionIndex()) == trackedPointer) {
                // The controlling finger lifted before the extra finger.
                if (isPressed) audio.play(ArcadeAudio.Effect.SOFT_RELEASE, false);
                isPressed = false;
                canceled = true;
                trackedPointer = -1;
                resetPressVisual();
            }
            return true;
        }
        if (type == MotionEvent.ACTION_CANCEL) {
            if (isPressed) audio.play(ArcadeAudio.Effect.SOFT_RELEASE, false);
            isPressed = false;
            trackedPointer = -1;
            resetPressVisual();
            return true;
        }
        int pointerIndex = event.findPointerIndex(trackedPointer);
        if (pointerIndex < 0) return true;
        float rawX = event.getRawX() + (event.getX(pointerIndex)-event.getX(0));
        float rawY = event.getRawY() + (event.getY(pointerIndex)-event.getY(0));
        float dx = rawX-downRawX, dy = rawY-downRawY;
        float distanceSq = dx*dx+dy*dy;
        if (type == MotionEvent.ACTION_MOVE) {
            boolean moved = distanceSq > touchSlop*touchSlop*3f;
            if (!dragging && moved && SystemClock.uptimeMillis()-downTime >= 450) {
                dragging = true;
                canceled = true;
                resetPressVisual();
                audio.play(ArcadeAudio.Effect.SOFT_RELEASE, false);
            }
            if (dragging) {
                params.x = startWindowX + Math.round(dx);
                params.y = startWindowY + Math.round(dy);
                clampPosition();
                try {windowManager.updateViewLayout(button, params);} catch (RuntimeException ignored) { }
            } else if (!touchesRedDome(event.getX(pointerIndex), event.getY(pointerIndex))) {
                if (!canceled) audio.play(ArcadeAudio.Effect.SOFT_RELEASE, false);
                canceled = true;
                resetPressVisual();
            }
            return true;
        }
        if (type == MotionEvent.ACTION_UP) {
            boolean shouldCall = isPressed && !dragging && !canceled &&
                    touchesRedDome(event.getX(pointerIndex), event.getY(pointerIndex));
            if (!dragging && !canceled && !shouldCall) audio.play(ArcadeAudio.Effect.SOFT_RELEASE, false);
            if (dragging) {
                prefs.edit().putInt(KEY_X,params.x).putInt(KEY_Y,params.y).apply();
            }
            trackedPointer = -1;
            isPressed = false;
            resetPressVisual();
            if (shouldCall) {
                audio.play(ArcadeAudio.Effect.RELEASE, false);
                launching = true;
                button.postDelayed(() -> {
                    launching = false;
                    if (button != null) placeCall();
                },300);
            }
            return true;
        }
        return true;
    }

    private void clampPosition() {
        if (params == null) return;
        int[] screen = availableDimensions();
        params.x = Math.max(0, Math.min(screen[0]-buttonWidth, params.x));
        params.y = Math.max(dp(30), Math.min(screen[1]-buttonHeight-dp(20), params.y));
    }

    private void placeCall() {
        if (prefs == null) return;
        String number = prefs.getString(NUMBER, null);
        if (number == null || !number.matches("\\+?[0-9]{5,15}")) return;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,"Enable phone-call permission in dude.call",Toast.LENGTH_SHORT).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                TelephonyManager manager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
                if (manager != null && manager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                    Toast.makeText(this,"CALL ALREADY ACTIVE",Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (RuntimeException ignored) { }
        }
        long now = SystemClock.elapsedRealtime();
        long previous = prefs.getLong(KEY_LAST_CALL,0L);
        if (previous > 0 && now >= previous && now-previous < 5000L) return;
        prefs.edit().putLong(KEY_LAST_CALL,now).apply();
        try {
            TelecomManager telecom = (TelecomManager)getSystemService(TELECOM_SERVICE);
            if (telecom == null) throw new IllegalStateException("No TelecomManager");
            telecom.placeCall(Uri.fromParts("tel",number,null),new Bundle());
        } catch (RuntimeException e) {
            prefs.edit().remove(KEY_LAST_CALL).apply();
            Toast.makeText(this,"Unable to place call",Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (button != null && params != null) {
            clampPosition();
            try {windowManager.updateViewLayout(button,params);} catch(RuntimeException ignored) { }
        }
    }

    @Override public void onDestroy() {
        if (button != null && windowManager != null) {
            button.animate().cancel();
            try { windowManager.removeView(button); } catch (RuntimeException ignored) { }
            button = null;
        }
        if (audio != null) { audio.release(); audio = null; }
        if (image != null) { image.recycle(); image = null; }
        super.onDestroy();
    }
}
