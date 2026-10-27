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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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

/** Explicitly enabled 64dp call overlay. STOP and drag-to-REMOVE persist OFF. */
public final class FloatingCallService extends Service {
    static final String ACTION_START = "com.cr1za.dudecall.FLOAT_START";
    static final String ACTION_STOP = "com.cr1za.dudecall.FLOAT_STOP";
    static final String KEY_ENABLED = "overlay_enabled";
    private static final String PREFS = "dude_call_once";
    private static final String NUMBER = "permanent_number";
    private static final String KEY_X = "overlay_x";
    private static final String KEY_Y = "overlay_y";
    private static final String KEY_REL_X = "overlay_relative_x_v13";
    private static final String KEY_REL_Y = "overlay_relative_y_v13";
    private static final String CHANNEL = "dude_call_floating_button";
    private static final int NOTIFICATION_ID = 1212;
    private static volatile boolean visible;

    static boolean isVisible() { return visible; }

    private WindowManager manager;
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams removeParams;
    private ImageView button;
    private RemoveTarget removeTarget;
    private Bitmap artwork;
    private ArcadeAudio audio;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int width, height, touchSlop;
    private boolean dragging, moved, canceled, calling;
    private boolean pressed;
    private int controllingPointer = -1, startX, startY;
    private float downRawX, downRawY;
    private final Runnable beginDrag = () -> {
        if (!pressed || controllingPointer < 0 || button == null) return;
        dragging = true;
        canceled = true;
        resetVisual();
        audio.play(ArcadeAudio.Effect.SOFT_RELEASE, false);
        showRemoveTarget();
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            // Do not let notification STOP or Android recreation silently restore the overlay.
            prefs.edit().putBoolean(KEY_ENABLED, false).commit();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction()) || !prefs.getBoolean(KEY_ENABLED, false) ||
                !Settings.canDrawOverlays(this) || prefs.getString(NUMBER, null) == null ||
                checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        createNotificationChannel();
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
            if (button == null) attachOverlay();
        } catch (RuntimeException failure) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply();
            stopSelf();
        }
        // A system process kill must not silently re-enable a deliberately stopped overlay.
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private int[] dimensions() {
        DisplayMetrics metrics = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(metrics);
        return new int[]{metrics.widthPixels,metrics.heightPixels};
    }

    private void createNotificationChannel() {
        NotificationManager notificationManager =
                (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    "OBIC Floating Button", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("One Button Instant Caller: floating red button with a STOP control");
            ch.setSound(null, null);
            notificationManager.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent main = PendingIntent.getActivity(this, 2, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 3,
                new Intent(this,FloatingCallService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("OBIC floating button")
                .setContentText("Tap to call · hold and drag to move or REMOVE")
                .setContentIntent(main)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,"STOP",stop)
                .build();
    }

    private void attachOverlay() {
        artwork = BitmapFactory.decodeResource(getResources(),R.drawable.floating_arcade_button);
        if (artwork == null) throw new IllegalStateException("Overlay artwork missing");
        width = dp(64);
        height = dp(48); // 64dp-wide artwork, enough height for an accessible visual target.
        int[] screen = dimensions();
        params = new WindowManager.LayoutParams(width,height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        int defaultX = Math.max(0,screen[0]-width-dp(12));
        int defaultY = Math.max(0,screen[1]/2-height/2);
        if (prefs.contains(KEY_REL_X) && prefs.contains(KEY_REL_Y)) {
            params.x = Math.round(prefs.getFloat(KEY_REL_X,1f)*Math.max(0,screen[0]-width));
            params.y = Math.round(prefs.getFloat(KEY_REL_Y,.5f)*Math.max(0,screen[1]-height));
        } else {
            params.x = prefs.getInt(KEY_X,defaultX);
            params.y = prefs.getInt(KEY_Y,defaultY);
        }
        clampPosition();
        button = new ImageView(this);
        button.setImageBitmap(artwork);
        button.setScaleType(ImageView.ScaleType.FIT_XY);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription("Call now. Hold and drag to move or remove floating button.");
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        button.setOnClickListener(v -> onButtonClick());
        button.setOnTouchListener(this::handleTouch);
        audio = new ArcadeAudio(this);
        audio.refreshRoute();
        manager.addView(button,params);
        visible = true;
    }

    private void clampPosition() {
        if (params == null) return;
        int[] screen = dimensions();
        params.x = Math.max(0,Math.min(Math.max(0,screen[0]-width),params.x));
        params.y = Math.max(dp(24),Math.min(Math.max(dp(24),screen[1]-height-dp(25)),params.y));
    }

    private void savePosition() {
        int[] screen=dimensions();
        float x=params.x/(float)Math.max(1,screen[0]-width);
        float y=params.y/(float)Math.max(1,screen[1]-height);
        prefs.edit().putInt(KEY_X,params.x).putInt(KEY_Y,params.y)
                .putFloat(KEY_REL_X,x).putFloat(KEY_REL_Y,y).apply();
    }

    private boolean onRed(float x,float y) {
        if (artwork == null || x < 0 || x >= width || y < 0 || y >= height) return false;
        int px=Math.min(artwork.getWidth()-1,(int)(x*artwork.getWidth()/width));
        int py=Math.min(artwork.getHeight()-1,(int)(y*artwork.getHeight()/height));
        int c=artwork.getPixel(px,py);
        return Color.alpha(c)>=120 && Color.red(c)>95 &&
                Color.red(c)>Color.green(c)*1.5f && Color.red(c)>Color.blue(c)*1.25f;
    }

    private void resetVisual() {
        if (button != null) {
            button.animate().cancel();
            button.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
        }
    }

    private void onButtonClick() {
        if (calling || button == null) return;
        calling = true;
        audio.play(ArcadeAudio.Effect.RELEASE,false);
        // Short release feedback; an accessibility click comes through this same path.
        handler.postDelayed(() -> {
            calling = false;
            if (button != null) placeCall();
        },300);
    }

    private float[] pointerRaw(MotionEvent event,int pointerIndex) {
        // getRawX/Y are pointer-0; offset to the tracked finger for multi-touch.
        return new float[]{event.getRawX()+event.getX(pointerIndex)-event.getX(0),
                event.getRawY()+event.getY(pointerIndex)-event.getY(0)};
    }

    private boolean handleTouch(View view,MotionEvent event) {
        int action=event.getActionMasked();
        if (action==MotionEvent.ACTION_DOWN) {
            // Consume transparent/base taps inside the tiny overlay rectangle;
            // they must not trigger ImageView's default click handler.
            if (calling || !onRed(event.getX(),event.getY())) return true;
            controllingPointer=event.getPointerId(0);
            downRawX=event.getRawX();downRawY=event.getRawY();
            startX=params.x;startY=params.y;
            pressed=true;dragging=false;moved=false;canceled=false;
            button.animate().cancel();
            button.animate().scaleX(.965f).scaleY(.945f).setDuration(60).start();
            audio.play(ArcadeAudio.Effect.PRESS,false);
            handler.removeCallbacks(beginDrag);
            handler.postDelayed(beginDrag,450);
            return true;
        }
        if (controllingPointer < 0) return false;
        if (action==MotionEvent.ACTION_POINTER_DOWN) return true;
        if (action==MotionEvent.ACTION_CANCEL) {
            finishGesture(true);
            return true;
        }
        int index=event.findPointerIndex(controllingPointer);
        if (index < 0) return true;
        float[] coords=pointerRaw(event,index);
        float dx=coords[0]-downRawX,dy=coords[1]-downRawY;
        if (action==MotionEvent.ACTION_MOVE) {
            if (dx*dx+dy*dy>touchSlop*touchSlop) { moved=true; canceled=true; }
            if (dragging) {
                params.x=startX+Math.round(dx);
                params.y=startY+Math.round(dy);
                clampPosition();
                try {manager.updateViewLayout(button,params);} catch (RuntimeException ignored) { }
                if (removeTarget != null) removeTarget.setRemoveHighlighted(overRemove());
            } else if (!onRed(event.getX(index),event.getY(index))) {
                canceled=true;
            }
            return true;
        }
        if (action==MotionEvent.ACTION_UP ||
                (action==MotionEvent.ACTION_POINTER_UP && index==event.getActionIndex())) {
            boolean click=!dragging && !moved && !canceled && onRed(event.getX(index),event.getY(index));
            boolean removed=dragging && overRemove();
            if (dragging && !removed) savePosition();
            finishGesture(!click);
            if (removed) stopByUser();
            else if (click && button != null) button.performClick();
            return true;
        }
        return true;
    }

    private void finishGesture(boolean canceledGesture) {
        handler.removeCallbacks(beginDrag);
        if (pressed && canceledGesture && !dragging && audio != null)
            audio.play(ArcadeAudio.Effect.SOFT_RELEASE,false);
        pressed=false;dragging=false;controllingPointer=-1;
        hideRemoveTarget();
        resetVisual();
    }

    private void showRemoveTarget() {
        if (removeTarget != null) return;
        int[] size=dimensions();
        removeParams=new WindowManager.LayoutParams(dp(132),dp(57),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        removeParams.gravity=Gravity.TOP|Gravity.LEFT;
        removeParams.x=(size[0]-removeParams.width)/2;
        removeParams.y=Math.max(dp(30),size[1]-removeParams.height-dp(55));
        // A translucent, non-touchable hint window avoids blocking background apps.
        removeParams.alpha=.75f;
        removeTarget=new RemoveTarget(this);
        try {manager.addView(removeTarget,removeParams);}
        catch (RuntimeException failure) {removeTarget=null;removeParams=null;}
    }

    private boolean overRemove() {
        if (removeParams == null || params == null) return false;
        float x=params.x+width/2f,y=params.y+height/2f;
        return x>=removeParams.x-dp(14) && x<=removeParams.x+removeParams.width+dp(14) &&
                y>=removeParams.y-dp(15) && y<=removeParams.y+removeParams.height+dp(15);
    }

    private void hideRemoveTarget() {
        if (removeTarget != null) {
            try {manager.removeView(removeTarget);} catch (RuntimeException ignored) { }
            removeTarget=null;removeParams=null;
        }
    }

    private void stopByUser() {
        prefs.edit().putBoolean(KEY_ENABLED,false).commit();
        stopSelf();
    }

    private void placeCall() {
        String number=prefs.getString(NUMBER,null);
        if (number==null || !number.matches("\\+?[0-9]{5,15}")) return;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,"Enable phone-call permission in OBIC",Toast.LENGTH_SHORT).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED) {
            try {
                TelephonyManager t=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);
                if (t != null && t.getCallState()!=TelephonyManager.CALL_STATE_IDLE) {
                    Toast.makeText(this,"CALL ALREADY ACTIVE",Toast.LENGTH_SHORT).show();return;
                }
            } catch (RuntimeException ignored) { }
        }
        if (!CallGate.tryAcquire(prefs)) return;
        try {
            TelecomManager telecom=(TelecomManager)getSystemService(TELECOM_SERVICE);
            if (telecom==null) throw new IllegalStateException("No TelecomManager");
            telecom.placeCall(Uri.fromParts("tel",number,null),new Bundle());
        } catch (RuntimeException failure) {
            CallGate.rollback(prefs);
            Toast.makeText(this,"Unable to place call",Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        hideRemoveTarget();
        handler.removeCallbacks(beginDrag);
        pressed=false;dragging=false;controllingPointer=-1;
        resetVisual();
        if (button != null && params != null) {
            int[] screen=dimensions();
            if (prefs.contains(KEY_REL_X) && prefs.contains(KEY_REL_Y)) {
                params.x=Math.round(prefs.getFloat(KEY_REL_X,1f)*Math.max(0,screen[0]-width));
                params.y=Math.round(prefs.getFloat(KEY_REL_Y,.5f)*Math.max(0,screen[1]-height));
            }
            clampPosition();
            try {manager.updateViewLayout(button,params);} catch (RuntimeException ignored) { }
        }
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hideRemoveTarget();
        if (button != null) {
            button.animate().cancel();
            try {manager.removeView(button);} catch (RuntimeException ignored) { }
            button=null;
        }
        visible=false;
        if (prefs != null) prefs.edit().putBoolean(KEY_ENABLED,false).apply();
        if (audio!=null) {audio.release();audio=null;}
        if (artwork!=null) {artwork.recycle();artwork=null;}
        super.onDestroy();
    }

    private final class RemoveTarget extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean selected;
        RemoveTarget(Context context) {super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setRemoveHighlighted(boolean next) {if (selected!=next){selected=next;invalidate();}}
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float pad=dp(4);
            RectF r=new RectF(pad,pad,getWidth()-pad,getHeight()-pad);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(selected?0xFFB71B28:0xFF36090E);
            c.drawRoundRect(r,dp(26),dp(26),paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));
            paint.setColor(0xFFF20D17);c.drawRoundRect(r,dp(26),dp(26),paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(15));
            paint.setTypeface(android.graphics.Typeface.create("sans-serif-black",1));
            float baseline=getHeight()/2f-(paint.ascent()+paint.descent())/2f;
            c.drawText("×  REMOVE",getWidth()/2f,baseline,paint);
        }
    }
}
