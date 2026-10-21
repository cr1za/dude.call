package com.cr1za.dudecall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.AccessibilityNodeProvider;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Custom Android Canvas arcade surface. No web view, ads, analytics or tracking. */
public final class ArcadeView extends View {
    interface Events {
        void requestCall();
        boolean saveNumber(String number);
        boolean isPlausible(String number);
        String formatNumber(String raw);
        void openSettings();
        void lockRotation(boolean locked);
        void setPrivateScreen(boolean privateScreen);
    }
    private static final int RED = 0xFFF20D17;
    private static final int WHITE = 0xFFF9F9FC;
    private static final int ENTRY = 0, CONFIRM = 1, MAIN = 2;
    private static final String[] KEYS = {"1","2","3","4","5","6","7","8","9","+","0","X"};
    private static final int V_MAIN = 1, V_INPUT = 2, V_LOCK = 3, V_CONFIRM_NUMBER = 4,
            V_GO = 5, V_SETTINGS = 6, V_KEY = 100;

    private final Events events;
    private final ArcadeAudio audio;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Typeface arcadeTypeface = Typeface.create("sans-serif-black", Typeface.BOLD);
    private final StringBuilder entered = new StringBuilder();
    private final RectF[] keyBounds = new RectF[12];
    private final RectF inputBounds = new RectF();
    private final RectF lockBounds = new RectF();
    private final RectF confirmBounds = new RectF();
    private final RectF numberBounds = new RectF();
    private final RectF goBounds = new RectF();
    private final RectF panelBounds = new RectF();
    private final RectF settingsBounds = new RectF();
    private final RectF mainHitBounds = new RectF();

    private int stage;
    private int insetL, insetT, insetR, insetB;
    private float d;
    private float cx, cy, mainDiameter;
    private long mainRevealStart, releaseStart, overlayPopStart, panelDismissStart, holdGlowFadeStart, fadeOutStart, errorStart,
            lockStart, bounceStart, callGlowFadeStart;
    private int pointerId = -1;
    private int touched = -1; // 1 main, 2..13 keypad, 20 lock, 21 number, 22 confirm, 23 settings
    private boolean holdingMain, mainReleasing, launchGlow, showingPermission,
            lockAnimating, confirmationFading, exceededLimitAnnounced, longDeleting;
    private String message = "";
    private long messageUntil;
    private String inputError = "";
    private int bounceKey = -1;
    private boolean reducedMotion;
    private int accessibilityFocused = Integer.MIN_VALUE;
    private int deletedInHold;
    private final AccessibilityManager a11y;
    private final AccessibilityNodeProvider provider = new VirtualProvider();
    private final Runnable deletion = new Runnable() {
        @Override public void run() {
            if (stage != ENTRY || touched != 13 || pointerId < 0) return;
            longDeleting = true;
            if (entered.length() > 0) {
                deleteLast(false);
                deletedInHold++;
            } else if (deletedInHold == 0) {
                audio.play(ArcadeAudio.Effect.ERROR, reducedMotion);
            }
            postDelayed(this, 120);
        }
    };

    ArcadeView(Context context, Events callbacks, ArcadeAudio sounds, boolean hasNumber) {
        super(context);
        events = callbacks;
        audio = sounds;
        d = getResources().getDisplayMetrics().density;
        stage = hasNumber ? MAIN : ENTRY;
        events.setPrivateScreen(stage != MAIN);
        a11y = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        for (int i = 0; i < keyBounds.length; i++) keyBounds[i] = new RectF();
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        refreshAudioRoute();
        if (stage == MAIN) mainRevealStart = SystemClock.uptimeMillis();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (stage == MAIN && mainRevealStart > 0) {
            post(() -> audio.play(ArcadeAudio.Effect.SPRING, reducedMotion));
            invalidate();
        }
    }

    void refreshAudioRoute() {
        audio.refreshRoute();
        try {
            reducedMotion = Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Exception ignored) { reducedMotion = false; }
        invalidate();
    }

    void setSafeInsets(int left, int top, int right, int bottom) {
        insetL = left; insetT = top; insetR = right; insetB = bottom;
        requestLayout(); invalidate();
    }

    void onDeviceConfigurationChanged() {
        cancelHeldPress();
        invalidate();
    }

    private long now() { return SystemClock.uptimeMillis(); }
    private float dp(float v) { return v * d; }
    private float clamp(float x, float lo, float hi) { return Math.max(lo, Math.min(hi, x)); }
    private float progress(long start, float duration) {
        if (start == 0) return 1f;
        return clamp((now() - start) / duration, 0f, 1f);
    }
    private float ease(float t) { return 1f - (float)Math.pow(1f - t, 3); }
    private float bounce(float t) {
        return 1f - (float)(Math.exp(-5.8f * t) * Math.cos(11f * t));
    }

    private void layoutGeometry() {
        float left = insetL + dp(18), right = getWidth() - insetR - dp(18);
        float top = insetT + dp(12), bottom = getHeight() - insetB - dp(12);
        float width = Math.max(dp(130), right - left), height = Math.max(dp(200), bottom - top);
        cx = (left + right) * .5f;
        cy = (top + bottom) * .5f;
        mainDiameter = Math.min(width * .94f, height * 1.05f);
        // Dome hitbox (not the black base); view from 30 degrees above.
        float rx = mainDiameter * .445f, ry = mainDiameter * .287f;
        mainHitBounds.set(cx-rx,cy-ry-dp(19),cx+rx,cy+ry-dp(19));

        boolean landscape = width > height * 1.12f;
        if (landscape) {
            float leftWidth = width * .43f;
            float separation = dp(20);
            float entryLeft = left, entryRight = left + leftWidth;
            float kLeft = entryRight + separation, kRight = right;
            float boxHeight = clamp(height * .22f,dp(54),dp(94));
            inputBounds.set(entryLeft,top + height*.29f,entryRight,top + height*.29f+boxHeight);
            lockBounds.set((entryLeft+entryRight)/2-dp(63),bottom-dp(137),
                    (entryLeft+entryRight)/2+dp(63),bottom-dp(11));
            setupKeys(kLeft,top+dp(10),kRight,bottom-dp(10));
        } else {
            float margin = Math.min(dp(17),width*.05f);
            float boxHeight = clamp(height*.115f,dp(59),dp(112));
            inputBounds.set(left+margin,top + height*.16f,right-margin,top + height*.16f+boxHeight);
            float kTop = top + height*.32f;
            float kBottom = bottom - height*.17f;
            setupKeys(left+margin,kTop,right-margin,kBottom);
            float lockSide = Math.min(dp(128),height*.146f);
            lockBounds.set(cx-lockSide/2,bottom-lockSide-dp(5),cx+lockSide/2,bottom-dp(5));
        }
        float cardWidth = Math.min(width*.87f,dp(490));
        float cardHeight = Math.min(height*.72f,dp(395));
        confirmBounds.set(cx-cardWidth/2,cy-cardHeight/2,cx+cardWidth/2,cy+cardHeight/2);
        numberBounds.set(confirmBounds.left+dp(17),confirmBounds.top+cardHeight*.30f,
                confirmBounds.right-dp(17),confirmBounds.top+cardHeight*.54f);
        float goSide = clamp(cardHeight*.31f,dp(88),dp(130));
        goBounds.set(cx-goSide/2,confirmBounds.bottom-goSide-dp(12),
                cx+goSide/2,confirmBounds.bottom-dp(12));
        float panelWidth = Math.min(landscape ? width*.70f : width*.90f,dp(485));
        float panelHeight = landscape ? Math.min(height*.74f,dp(224)) : Math.min(height*.52f,dp(245));
        panelBounds.set(cx-panelWidth/2,cy-panelHeight/2,cx+panelWidth/2,cy+panelHeight/2);
        float settingsWidth = Math.min(panelWidth*.74f,dp(250));
        settingsBounds.set(cx-settingsWidth/2,panelBounds.bottom-dp(78),
                cx+settingsWidth/2,panelBounds.bottom-dp(23));
    }

    private void setupKeys(float left,float top,float right,float bottom) {
        float areaW = right-left, areaH = bottom-top;
        float keyDia = Math.min(areaW/3.65f,areaH/4.65f);
        float xSpace = areaW / 3f, ySpace = areaH / 4f;
        for (int i=0; i<12;i++) {
            float x=left+(i%3+.5f)*xSpace;
            float y=top+(i/3+.5f)*ySpace;
            keyBounds[i].set(x-keyDia/2,y-keyDia/2,x+keyDia/2,y+keyDia/2);
        }
    }

    private void fill(int color) { p.reset(); p.setAntiAlias(true); p.setColor(color); p.setStyle(Paint.Style.FILL); }
    private void oval(Canvas c,RectF r,int color) { fill(color); c.drawOval(r,p); }
    private void rounded(Canvas c,RectF r,float rad,int color) { fill(color); c.drawRoundRect(r,rad,rad,p); }
    private void gradient(Canvas c,RectF r,float radius,int[] colors,float[] stops) {
        fill(Color.WHITE);
        p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,colors,stops,Shader.TileMode.CLAMP));
        c.drawRoundRect(r,radius,radius,p);
        p.setShader(null);
    }
    private void stroke(Canvas c,RectF r,float rad,int color,float width) {
        fill(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);
        c.drawRoundRect(r,rad,rad,p);p.setStyle(Paint.Style.FILL);
    }
    private void text(Canvas c,String s,float x,float centerY,float size,int color,boolean bold) {
        fill(color);p.setTypeface(bold ? arcadeTypeface : Typeface.create("sans-serif",Typeface.NORMAL));
        p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);
        float baseline=centerY-(p.ascent()+p.descent())/2f;
        c.drawText(s,x,baseline,p);
    }
    private float fittedFont(String s,float maxWidth,float proposed,float min) {
        p.setTypeface(arcadeTypeface);p.setTextSize(proposed);
        float w=p.measureText(s);
        return w > maxWidth ? Math.max(min,proposed*maxWidth/w):proposed;
    }
    private void smallGlow(Canvas c,RectF r,int color,float width) {
        fill(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);
        p.setMaskFilter(new android.graphics.BlurMaskFilter(width*1.45f,android.graphics.BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(r,dp(13),dp(13),p);
        p.setMaskFilter(null);p.setStyle(Paint.Style.FILL);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        layoutGeometry();
        if (stage == ENTRY) drawEntry(canvas);
        else if (stage == CONFIRM) drawConfirmation(canvas);
        else drawMain(canvas);
        if (stage == MAIN && (showingPermission ||
                (panelDismissStart > 0 && now() - panelDismissStart < 200))) drawPermission(canvas);
        if (stage == MAIN && !showingPermission && !message.isEmpty() && now() < messageUntil)
            drawMessage(canvas);
        if (stage == MAIN && now() >= messageUntil) message = "";
        
        boolean animating = (stage == CONFIRM && confirmationFading) ||
                (stage == ENTRY && (lockAnimating || now()-bounceStart<210 || now()-errorStart<310)) ||
                (stage == MAIN && (mainReleasing ||
                        now()-callGlowFadeStart<220 || now()-holdGlowFadeStart<220 ||
                        (panelDismissStart>0 && now()-panelDismissStart<220) || (mainRevealStart>0 && now()-mainRevealStart<620) ||
                        (showingPermission && now()-overlayPopStart<210) ||
                        (!message.isEmpty() && now()<messageUntil)));
        if (animating) postInvalidateDelayed(16);
        else if (stage == ENTRY) postInvalidateDelayed(100);
    }

    private void drawEntry(Canvas c) {
        float left=insetL+dp(18),right=getWidth()-insetR-dp(18);
        float top=insetT+dp(12),bottom=getHeight()-insetB-dp(12);
        float width=right-left,height=bottom-top;
        boolean landscape=width>height*1.12f;
        float headX=landscape ? left+width*.215f : cx;
        float headY=landscape ? top+height*.13f : top+height*.09f;
        String heading="WHO SHOULD I CALL?";
        text(c,heading,headX,headY,fittedFont(heading,(landscape?width*.43f:width)*.96f,
                dp(landscape?23:28),dp(15)),RED,true);

        // Dark-red glossy input and silver-black raised frame.
        float shake=0;
        if (!inputError.isEmpty() && now()-errorStart<310) {
            float t=(now()-errorStart)/310f;
            shake=(float)Math.sin(t*Math.PI*8)*dp(8)*(1f-t);
        }
        c.save();c.translate(shake,0);
        smallGlow(c,inputBounds,inputError.isEmpty()?0x447D0A19:0xAAC40D18,dp(4));
        gradient(c,inputBounds,dp(14),new int[]{0xFF691019,0xFF2C050A,0xFF180205},null);
        stroke(c,inputBounds,dp(14),inputError.isEmpty()?0xFF931927:RED,dp(3));
        RectF highlight=new RectF(inputBounds.left+dp(5),inputBounds.top+dp(4),
                inputBounds.right-dp(5),inputBounds.top+dp(7));
        rounded(c,highlight,dp(2),0x78FFFFFF);
        String formatted=events.formatNumber(entered.toString());
        float font=fittedFont(formatted,inputBounds.width()-dp(38),dp(31),dp(17));
        text(c,formatted,inputBounds.centerX(),inputBounds.centerY(),font,WHITE,true);
        if ((now()/300)%2==0) {
            p.setTypeface(arcadeTypeface);p.setTextSize(font);
            float rightEdge=inputBounds.centerX()+p.measureText(formatted)/2f;
            fill(RED); c.drawRoundRect(new RectF(rightEdge+dp(3),inputBounds.centerY()-font*.48f,
                    rightEdge+dp(6),inputBounds.centerY()+font*.48f),dp(1.5f),dp(1.5f),p);
        }
        c.restore();

        if (!inputError.isEmpty()) {
            float y=landscape?inputBounds.bottom+dp(21):inputBounds.bottom+dp(21);
            text(c,inputError,inputBounds.centerX(),y,dp(14),0xFFFF4651,true);
        }
        // Sharp-cornered sunken keypad tray, with recessed side walls.
        float trayLeft=Float.MAX_VALUE,trayRight=0,trayTop=Float.MAX_VALUE,trayBottom=0;
        for(RectF r:keyBounds){trayLeft=Math.min(trayLeft,r.left);trayRight=Math.max(trayRight,r.right);
            trayTop=Math.min(trayTop,r.top);trayBottom=Math.max(trayBottom,r.bottom);}
        float margin=dp(12);
        RectF tray=new RectF(trayLeft-margin,trayTop-margin,trayRight+margin,trayBottom+margin);
        gradient(c,tray,0,new int[]{0xFF2C2C30,0xFF070708,0xFF000000},null);
        fill(0xFF020203);c.drawRect(tray.left+dp(8),tray.top+dp(8),tray.right-dp(8),tray.bottom-dp(8),p);
        fill(0x77FFFFFF);c.drawRect(tray.left+dp(1),tray.top+dp(1),tray.right-dp(1),tray.top+dp(3),p);
        fill(0xFF171719);c.drawRect(tray.left+dp(2),tray.bottom-dp(6),tray.right-dp(2),tray.bottom-dp(2),p);
        for(int i=0;i<12;i++) drawKey(c,i);
        drawRoundedArcadeControl(c,lockBounds,"LOCK IN",touched==20 || lockAnimating,RED);
    }

    private void drawKey(Canvas c,int index) {
        RectF b=keyBounds[index];
        boolean down=touched==index+2;
        float delayed=(bounceKey==index && now()-bounceStart<200) ? 1f-progress(bounceStart,200) : 0;
        float sink=down?dp(4):dp(4)*delayed*(float)Math.sin(progress(bounceStart,200)*Math.PI);
        RectF base=new RectF(b.left,b.top+dp(4),b.right,b.bottom+dp(5));
        oval(c,base,0xFF010102);
        RectF top=new RectF(b.left+dp(2),b.top+sink,b.right-dp(2),b.bottom-dp(3)+sink);
        fill(0xFF101013);p.setShader(new LinearGradient(top.left,top.top,top.right,top.bottom,
                new int[]{0xFF77777D,0xFF28282C,0xFF050506},null,Shader.TileMode.CLAMP));
        c.drawOval(top,p);p.setShader(null);
        fill(0x7FFFFFFF);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));
        c.drawArc(new RectF(top.left+dp(4),top.top+dp(4),top.right-dp(4),top.bottom-dp(4)),200,130,false,p);
        p.setStyle(Paint.Style.FILL);
        String label=KEYS[index];
        if (down) {
            fill(0x88FF1227);p.setShadowLayer(dp(7),0,0,0xFFFF2639);
        } else fill(RED);
        p.setTypeface(arcadeTypeface);p.setTextSize(Math.min(dp(29),b.width()*.52f));p.setTextAlign(Paint.Align.CENTER);
        c.drawText(label,b.centerX(),top.centerY()-(p.ascent()+p.descent())/2f,p);
        p.clearShadowLayer();
    }

    private void drawRoundedArcadeControl(Canvas c,RectF bounds,String label,boolean down,int red) {
        float size=bounds.width();float shift=down?dp(5):0;
        RectF base=new RectF(bounds.left,bounds.top+dp(6),bounds.right,bounds.bottom+dp(5));
        oval(c,base,0xFF560004);
        RectF face=new RectF(bounds.left+dp(1),bounds.top+shift,bounds.right-dp(1),bounds.bottom-dp(6)+shift);
        fill(red);p.setShader(new LinearGradient(face.left,face.top,face.right,face.bottom,
                new int[]{0xFFFF838A,0xFFF20D17,0xFF93020C},new float[]{0f,.46f,1f},Shader.TileMode.CLAMP));
        c.drawOval(face,p);p.setShader(null);
        fill(0x9FFFFFFF);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.8f));
        c.drawArc(new RectF(face.left+dp(7),face.top+dp(6),face.right-dp(7),face.bottom-dp(5)),198,113,false,p);
        p.setStyle(Paint.Style.FILL);
        text(c,label,bounds.centerX(),face.centerY(),fittedFont(label,size*.82f,dp(17),dp(10)),WHITE,true);
    }

    private void drawConfirmation(Canvas c) {
        float fade=confirmationFading ? 1f-progress(fadeOutStart,reducedMotion?160f:500f):1f;
        int alpha=(int)(fade*255);
        c.saveLayerAlpha(0,0,getWidth(),getHeight(),alpha);
        smallGlow(c,confirmBounds,0x445D0010,dp(9));
        gradient(c,confirmBounds,dp(23),new int[]{0xFF720D1C,0xFF37050E,0xFF130205},null);
        stroke(c,confirmBounds,dp(23),0xFFAD1B2A,dp(3));
        text(c,"CALL THIS NUMBER?",cx,confirmBounds.top+confirmBounds.height()*.16f,
                fittedFont("CALL THIS NUMBER?",confirmBounds.width()*.89f,dp(23),dp(14)),WHITE,true);
        // Interactive phone number gets soft, steady (not pulsing) red frame glow.
        smallGlow(c,numberBounds,0x668F0C24,dp(5));
        gradient(c,numberBounds,dp(12),new int[]{0xFF3B0610,0xFF1D0208},null);
        stroke(c,numberBounds,dp(12),0xFFDE2632,dp(2.5f));
        String formatted=events.formatNumber(entered.toString());
        text(c,formatted,cx,numberBounds.centerY(),
                fittedFont(formatted,numberBounds.width()-dp(18),dp(28),dp(14)),WHITE,true);
        drawRoundedArcadeControl(c,goBounds,"LET'S GO!",touched==22,RED);
        c.restore();
        if (confirmationFading && progress(fadeOutStart,reducedMotion?160f:500f)>=1) {
            confirmationFading=false;stage=MAIN;mainRevealStart=now();
            events.setPrivateScreen(false);
            audio.play(ArcadeAudio.Effect.SPRING,reducedMotion);
            invalidate();
        }
    }

    private void drawMain(Canvas c) {
        float reveal=mainRevealStart>0 ? progress(mainRevealStart,reducedMotion?100f:600f):1f;
        float release=mainReleasing ? progress(releaseStart,reducedMotion?85f:300f):1f;
        float depression=holdingMain ? 1f : (mainReleasing ? Math.max(0,1f-bounce(release)) : 0f);
        if (mainReleasing && release>=1) {mainReleasing=false;depression=0;}
        float rise=(mainRevealStart>0 && reveal<1) ? 1f-(reducedMotion?ease(reveal):bounce(reveal)) : 0f;
        float radius=mainDiameter*.445f;
        float ry=mainDiameter*.287f;
        float depth=mainDiameter*.13f;
        float buttonY=cy-dp(19);
        float domedown=depth*.72f*depression + depth*.7f*rise;
        // Glossy near-black standard arcade base with wide raised rim. No shadow beneath it.
        RectF lower=new RectF(cx-radius*1.15f,buttonY-ry*.93f+depth*.88f,
                cx+radius*1.15f,buttonY+ry*1.05f+depth*1.22f);
        fill(0xFF090909);p.setShader(new LinearGradient(lower.left,lower.top,lower.right,lower.bottom,
                new int[]{0xFF494949,0xFF080809,0xFF000000},null,Shader.TileMode.CLAMP));
        c.drawOval(lower,p);p.setShader(null);
        RectF rim=new RectF(cx-radius*1.14f,buttonY-ry*.99f+depth*.28f,
                cx+radius*1.14f,buttonY+ry*1.03f+depth*.48f);
        fill(0xFF19191B);p.setShader(new LinearGradient(rim.left,rim.top,rim.right,rim.bottom,
                new int[]{0xFF707073,0xFF1E1E20,0xFF050505,0xFF272729},
                new float[]{0f,.33f,.78f,1f},Shader.TileMode.CLAMP));
        c.drawOval(rim,p);p.setShader(null);
        fill(0x99FFFFFF);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));
        c.drawArc(new RectF(rim.left+dp(5),rim.top+dp(3),rim.right-dp(5),rim.bottom-dp(7)),
                190,115,false,p);p.setStyle(Paint.Style.FILL);
        // Red sidewall, thick enough to visually sink 7 mm on contact.
        for(float h=depth*.70f;h>=domedown;h-=Math.max(dp(1),mainDiameter/200f)) {
            float v=clamp(h/(depth*.70f),0,1);
            RectF slice=new RectF(cx-radius,buttonY-ry+h,cx+radius,buttonY+ry+h);
            oval(c,slice,blend(0xFFD5131E,0xFF79020A,v));
        }
        RectF top=new RectF(cx-radius,buttonY-ry+domedown,
                cx+radius,buttonY+ry+domedown);
        // Rounded convex dome using off-center radial lacquer highlight.
        fill(RED);
        p.setShader(new RadialGradient(cx-radius*.42f,top.top+ry*.35f,radius*1.7f,
                new int[]{0xFFFF7379,0xFFFF252D,RED,0xFF83040D},
                new float[]{0f,.32f,.70f,1f},Shader.TileMode.CLAMP));
        c.drawOval(top,p);p.setShader(null);
        // Thin curved upper-left reflection, moving naturally with the dome.
        Path glint=new Path();
        glint.moveTo(cx-radius*.76f,buttonY-ry*.36f+domedown);
        glint.cubicTo(cx-radius*.57f,buttonY-ry*.82f+domedown,
                cx-radius*.11f,buttonY-ry*.93f+domedown,
                cx+radius*.18f,buttonY-ry*.84f+domedown);
        fill(0xD8FFFFFF);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.2f));
        p.setStrokeCap(Paint.Cap.ROUND);c.drawPath(glint,p);p.setStyle(Paint.Style.FILL);
        // Curved lacquer outline.
        fill(0x55FFFFFF);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.3f));
        c.drawArc(new RectF(top.left+dp(6),top.top+dp(5),top.right-dp(6),top.bottom-dp(5)),193,140,false,p);
        p.setStyle(Paint.Style.FILL);
        // Edge glow is off at idle, bright steady while held and subtle during call launch.
        float glow=holdingMain?1f:(launchGlow?.40f:0f);
        if (!launchGlow && callGlowFadeStart>0 && now()-callGlowFadeStart<200)
            glow=.40f*(1f-progress(callGlowFadeStart,200));
        if (!holdingMain && holdGlowFadeStart>0 && now()-holdGlowFadeStart<200)
            glow=Math.max(glow,1f-progress(holdGlowFadeStart,200));
        if (glow>0) {
            fill(Color.argb((int)(140*glow),255,14,35));p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(7));p.setMaskFilter(new android.graphics.BlurMaskFilter(dp(12),
                    android.graphics.BlurMaskFilter.Blur.NORMAL));
            c.drawOval(top,p);p.setMaskFilter(null);
            p.setStrokeWidth(dp(2));p.setColor(Color.argb((int)(200*glow),255,49,64));
            c.drawOval(top,p);p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawPermission(Canvas c) {
        int alpha=showingPermission?255:(int)(255*(1f-progress(panelDismissStart,200)));
        c.saveLayerAlpha(0,0,getWidth(),getHeight(),alpha);
        fill(0x80000000); c.drawRect(0,0,getWidth(),getHeight(),p);
        float phase=progress(overlayPopStart,reducedMotion?80f:200f);
        float scale=reducedMotion?1f:.83f+.17f*bounce(phase);
        c.save();c.scale(scale,scale,cx,cy);
        smallGlow(c,panelBounds,0x556D0A18,dp(8));
        gradient(c,panelBounds,dp(18),new int[]{0xFF75131C,0xFF35070D,0xFF190306},null);
        stroke(c,panelBounds,dp(18),0xFF9B2630,dp(2));
        float titleY=panelBounds.top+panelBounds.height()*.29f;
        float size=fittedFont("PHONE PERMISSION REQUIRED",panelBounds.width()-dp(26),dp(19),dp(12));
        if (size<=dp(14) && getWidth()<getHeight()) {
            text(c,"PHONE PERMISSION",cx,titleY-dp(10),dp(18),WHITE,true);
            text(c,"REQUIRED",cx,titleY+dp(17),dp(18),WHITE,true);
        } else {
            text(c,"PHONE PERMISSION REQUIRED",cx,titleY,size,WHITE,true);
        }
        float sink=touched==23?dp(3):0;
        RectF actualSettings = new RectF(settingsBounds);
        actualSettings.offset(0,sink);
        gradient(c,actualSettings,dp(11),new int[]{0xFF640E18,0xFF2F0409},null);
        stroke(c,actualSettings,dp(11),RED,dp(2.4f));
        text(c,"OPEN SETTINGS",actualSettings.centerX(),actualSettings.centerY(),
                fittedFont("OPEN SETTINGS",settingsBounds.width()-dp(16),dp(19),dp(13)),WHITE,true);
        c.restore();
        c.restore();
    }

    private void drawMessage(Canvas c) {
        float y=Math.min(cy+mainDiameter*.38f,getHeight()-insetB-dp(64));
        float w=Math.min(dp(295),getWidth()-insetL-insetR-dp(42));
        RectF msg=new RectF(cx-w/2,y,cx+w/2,y+dp(51));
        gradient(c,msg,dp(12),new int[]{0xFF6A1019,0xFF260509},null);
        stroke(c,msg,dp(12),0xFFB52632,dp(1.8f));
        text(c,message,cx,msg.centerY(),fittedFont(message,msg.width()-dp(15),dp(17),dp(11)),WHITE,true);
    }

    private int blend(int a,int b,float t) {
        t=clamp(t,0,1);
        return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),
                (int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),
                (int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));
    }

    private boolean insideEllipse(RectF r,float x,float y) {
        float dx=(x-r.centerX())/Math.max(1,r.width()/2);
        float dy=(y-r.centerY())/Math.max(1,r.height()/2);
        return dx*dx+dy*dy<=1f;
    }
    private int targetAt(float x,float y) {
        layoutGeometry();
        if (stage==MAIN) {
            if (showingPermission) return settingsBounds.contains(x,y)?23:-10;
            return insideEllipse(mainHitBounds,x,y)?1:-1;
        }
        if (stage==CONFIRM) {
            if (confirmationFading) return -1;
            if (insideEllipse(goBounds,x,y)) return 22;
            if (numberBounds.contains(x,y)) return 21;
            return -1;
        }
        if (lockAnimating) return -1;
        if (insideEllipse(lockBounds,x,y)) return 20;
        for(int i=0;i<12;i++) if(insideEllipse(keyBounds[i],x,y))return i+2;
        return -1;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if (action==MotionEvent.ACTION_DOWN) {
            int target=targetAt(event.getX(),event.getY());
            if (showingPermission && stage==MAIN && target==-10) {
                dismissPermissionPanel();return true; // outside, including exposed dome: dismiss only
            }
            if (stage==MAIN && (launchGlow || mainReleasing)) return true;
            if (target<0) return true;
            pointerId=event.getPointerId(0);touched=target;
            if (target==1) {
                holdGlowFadeStart=0;
                holdingMain=true;mainReleasing=false;
                events.lockRotation(true);
                if(mainRevealStart>0 && progress(mainRevealStart,reducedMotion?100f:600f)<1f)
                    audio.fadeAll(100);
                mainRevealStart=0;
                audio.play(ArcadeAudio.Effect.PRESS,reducedMotion);
            } else if (target>=2 && target<=13) {
                audio.play(ArcadeAudio.Effect.PRESS,reducedMotion);
                if(target==13) {
                    longDeleting=false;deletedInHold=0;
                    removeCallbacks(deletion);postDelayed(deletion,400);
                }
            } else if (target==23) audio.play(ArcadeAudio.Effect.SOFT_RELEASE,reducedMotion);
            invalidate();return true;
        }
        if (pointerId==-1) return true;
        // Second finger is intentionally ignored, including when it leaves.
        if (action==MotionEvent.ACTION_POINTER_DOWN) return true;
        int index=event.findPointerIndex(pointerId);
        if (index<0) return true;
        if (action==MotionEvent.ACTION_MOVE) {
            if (targetAt(event.getX(index),event.getY(index)) != touched) {
                cancelTouch(true);
            }
            return true;
        }
        if (action==MotionEvent.ACTION_CANCEL) {cancelTouch(true);return true;}
        if (action==MotionEvent.ACTION_UP || (action==MotionEvent.ACTION_POINTER_UP &&
                event.getActionIndex()==index)) {
            int chosen=touched;
            boolean within=targetAt(event.getX(index),event.getY(index))==chosen;
            boolean wasLong=longDeleting;
            int removed=deletedInHold;
            pointerId=-1;touched=-1;
            removeCallbacks(deletion);
            if (chosen==1) {
                events.lockRotation(false);
                holdingMain=false;mainReleasing=true;releaseStart=now();holdGlowFadeStart=now();
                if(within) {
                    audio.play(ArcadeAudio.Effect.RELEASE,reducedMotion);
                    postDelayed(() -> {
                        if (stage==MAIN && !showingPermission && !launchGlow) events.requestCall();
                    },reducedMotion?85:300);
                } else audio.play(ArcadeAudio.Effect.SOFT_RELEASE,reducedMotion);
            } else if (within) {
                if (chosen>=2 && chosen<=13) {
                    bounceKey=chosen-2;bounceStart=now();
                    audio.play(ArcadeAudio.Effect.RELEASE,reducedMotion);
                    if(chosen==13 && wasLong) {
                        if(removed>0) announce(removed+" digits deleted");
                    } else pressKey(chosen-2);
                } else if (chosen==20) pressLock();
                else if (chosen==21) {stage=ENTRY;invalidate();}
                else if (chosen==22) pressGo();
                else if (chosen==23) {events.openSettings();}
            } else if(chosen>=2 && chosen<=13) {
                audio.play(ArcadeAudio.Effect.SOFT_RELEASE,reducedMotion);
                if(chosen==13 && removed>0) announce(removed+" digits deleted");
            }
            invalidate();return true;
        }
        return true;
    }

    private void cancelTouch(boolean soft) {
        removeCallbacks(deletion);
        if (pointerId<0) return;
        int chosen=touched;
        int count=deletedInHold;
        pointerId=-1;touched=-1;
        if (chosen==1) {
            events.lockRotation(false);
            holdingMain=false;mainReleasing=true;releaseStart=now();holdGlowFadeStart=now();
        }
        if (soft && (chosen==1 || (chosen>=2 && chosen<=13)))
            audio.play(ArcadeAudio.Effect.SOFT_RELEASE,reducedMotion);
        if(chosen==13 && count>0) announce(count+" digits deleted");
        invalidate();
    }
    void cancelHeldPress() {cancelTouch(false);}

    private void pressKey(int i) {
        String key=KEYS[i];
        inputError="";
        if (key.equals("X")) {
            if (entered.length()==0) audio.play(ArcadeAudio.Effect.ERROR,reducedMotion);
            else deleteLast(true);
        } else if (key.equals("+")) {
            if (entered.length()>0 && entered.charAt(0)=='+') {
                entered.deleteCharAt(0);announce("International prefix removed");
            } else {
                entered.insert(0,'+');announce("International prefix added");
            }
        } else {
            int count=0;for(int n=0;n<entered.length();n++)if(Character.isDigit(entered.charAt(n)))count++;
            if(count>=15) {
                inputError="MAXIMUM 15 DIGITS";
                errorStart=now();
                postDelayed(() -> {if(inputError.equals("MAXIMUM 15 DIGITS")){inputError="";invalidate();}},2000);
                if(!exceededLimitAnnounced){announce("Maximum 15 digits");exceededLimitAnnounced=true;}
            } else {entered.append(key);announce(key);}
        }
        invalidate();
    }
    private void deleteLast(boolean speak) {
        if (entered.length()==0) return;
        char removed=entered.charAt(entered.length()-1);
        entered.deleteCharAt(entered.length()-1);
        exceededLimitAnnounced=false;
        if (speak) announce("Deleted "+(removed=='+'?"plus":removed));
        invalidate();
    }
    private void pressLock() {
        if (!events.isPlausible(entered.toString())) {
            inputError="CHECK PHONE NUMBER";
            errorStart=now();
            audio.play(ArcadeAudio.Effect.ERROR,reducedMotion);
            announce("Check phone number");
            invalidate();return;
        }
        lockAnimating=true;lockStart=now();
        audio.play(ArcadeAudio.Effect.LOCK,reducedMotion);
        postDelayed(()->{
            lockAnimating=false;
            if(stage==ENTRY){stage=CONFIRM;announce("Call this number? Double tap number to edit");}
            invalidate();
        },reducedMotion?100:340);
        invalidate();
    }
    private void pressGo() {
        if (confirmationFading || stage!=CONFIRM) return;
        if (!events.saveNumber(entered.toString())) {
            stage=ENTRY;inputError="CHECK PHONE NUMBER";errorStart=now();invalidate();return;
        }
        confirmationFading=true;fadeOutStart=now();
        // Two identical bright notes start during fade and finish just into spring reveal.
        postDelayed(()->audio.play(ArcadeAudio.Effect.CHIME,reducedMotion),reducedMotion?25:270);
        invalidate();
    }
    void showTimedMessage(String msg,long milliseconds) {
        showingPermission=false;
        message=msg;messageUntil=now()+milliseconds;
        if(msg.equals("READY TO CALL"))announce("Ready to call");
        else announce(msg);
        invalidate();
    }
    void showPermissionPanel() {
        showingPermission=true;panelDismissStart=0;overlayPopStart=now();message="";
        announce("Phone permission required");
        invalidate();
    }
    boolean dismissPermissionPanel() {
        if (!showingPermission) return false;
        showingPermission=false;panelDismissStart=now();invalidate();return true;
    }
    void setCallLaunching(boolean enabled) {
        launchGlow=enabled;
        if (!enabled)callGlowFadeStart=now();
        if (enabled){message="";mainReleasing=false;}
        invalidate();
    }

    private void announce(String message) {
        if (a11y==null || !a11y.isEnabled() || !a11y.isTouchExplorationEnabled())return;
        AccessibilityEvent event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
        event.setPackageName(getContext().getPackageName());
        event.setClassName(getClass().getName());
        event.getText().add(message);
        a11y.sendAccessibilityEvent(event);
    }

    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() {return provider;}

    private final class VirtualProvider extends AccessibilityNodeProvider {
        private List<Integer> visibleIds() {
            List<Integer> ids=new ArrayList<>();
            if (stage==ENTRY) {
                ids.add(V_INPUT);
                for(int i=0;i<12;i++)ids.add(V_KEY+i);
                ids.add(V_LOCK);
            } else if (stage==CONFIRM) {ids.add(V_CONFIRM_NUMBER);ids.add(V_GO);}
            else if(showingPermission) {ids.add(V_SETTINGS);}
            else ids.add(V_MAIN);
            return ids;
        }
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
            layoutGeometry();
            if(id==View.NO_ID) {
                AccessibilityNodeInfo root=AccessibilityNodeInfo.obtain(ArcadeView.this);
                root.setClassName("android.view.ViewGroup");
                for(int v:visibleIds())root.addChild(ArcadeView.this,v);
                return root;
            }
            if(!visibleIds().contains(id))return null;
            AccessibilityNodeInfo n=AccessibilityNodeInfo.obtain();
            n.setSource(ArcadeView.this,id);n.setParent(ArcadeView.this);
            n.setPackageName(getContext().getPackageName());
            n.setClassName("android.widget.Button");
            n.setEnabled(true);n.setClickable(true);n.setFocusable(true);
            n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
            n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
            n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            RectF box;
            String label;
            if(id==V_MAIN) {box=mainHitBounds;label="Call now";}
            else if(id==V_INPUT) {box=inputBounds;label="Phone number "+spokenNumber()+"";}
            else if(id==V_LOCK) {box=lockBounds;label="Lock in";}
            else if(id==V_CONFIRM_NUMBER) {box=numberBounds;label=spokenNumber()+". Double tap to edit";}
            else if(id==V_GO) {box=goBounds;label="Let's go";}
            else if(id==V_SETTINGS) {box=settingsBounds;label="Open Settings";}
            else {box=keyBounds[id-V_KEY];label=KEYS[id-V_KEY].equals("X")?"Delete":
                    KEYS[id-V_KEY].equals("+")?"Toggle plus prefix":KEYS[id-V_KEY];}
            n.setContentDescription(label);
            Rect r=new Rect((int)box.left,(int)box.top,(int)box.right,(int)box.bottom);
            n.setBoundsInParent(r);
            int[] screen=new int[2];getLocationOnScreen(screen);
            r.offset(screen[0],screen[1]);n.setBoundsInScreen(r);
            n.setAccessibilityFocused(accessibilityFocused==id);
            return n;
        }
        private String spokenNumber() {
            if(entered.length()==0)return "empty";
            StringBuilder result=new StringBuilder();
            for(int i=0;i<entered.length();i++) {
                if(i>0)result.append(' ');
                result.append(entered.charAt(i)=='+'?"plus":entered.charAt(i));
            }
            return result.toString();
        }
        @Override public boolean performAction(int id,int action,Bundle args) {
            if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                accessibilityFocused=id;
                sendEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                return true;
            }
            if(action==AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                accessibilityFocused=Integer.MIN_VALUE;
                sendEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                return true;
            }
            if(action!=AccessibilityNodeInfo.ACTION_CLICK)return false;
            if(!visibleIds().contains(id))return false;
            if(id==V_MAIN && !launchGlow && !mainReleasing) {
                audio.play(ArcadeAudio.Effect.PRESS,reducedMotion);
                audio.play(ArcadeAudio.Effect.RELEASE,reducedMotion);
                mainReleasing=true;releaseStart=now();
                postDelayed(()->{if(stage==MAIN&&!launchGlow)events.requestCall();},reducedMotion?85:300);
            } else if(id>=V_KEY && id<V_KEY+12) {
                int key=id-V_KEY;
                audio.play(ArcadeAudio.Effect.PRESS,reducedMotion);
                audio.play(ArcadeAudio.Effect.RELEASE,reducedMotion);
                pressKey(key);
            } else if(id==V_LOCK)pressLock();
            else if(id==V_CONFIRM_NUMBER)stage=ENTRY;
            else if(id==V_GO)pressGo();
            else if(id==V_SETTINGS)events.openSettings();
            invalidate();sendEvent(id,AccessibilityEvent.TYPE_VIEW_CLICKED);
            return true;
        }
        private void sendEvent(int id,int type) {
            if(a11y==null||!a11y.isEnabled())return;
            AccessibilityEvent e=AccessibilityEvent.obtain(type);
            e.setSource(ArcadeView.this,id);
            e.setPackageName(getContext().getPackageName());
            a11y.sendAccessibilityEvent(e);
        }
    }
}
