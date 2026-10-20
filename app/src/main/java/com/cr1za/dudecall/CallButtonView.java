package com.cr1za.dudecall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Draws a giant, glossy red cartoon button: a tilted 3D disc viewed from above. */
public final class CallButtonView extends View {
    private static final float TILT_DEGREES = -23f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable onPress;
    private boolean trackingTap;
    private boolean pressed;

    public CallButtonView(Context context, Runnable onPress) {
        super(context);
        this.onPress = onPress;
        setBackgroundColor(Color.BLACK);
        setClickable(true);
        setFocusable(true);
        setContentDescription("Call saved phone number");
        // Allows smooth transparent highlights on even older Android devices.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);

        float size = buttonSize();
        float rx = size * 0.50f;
        float ry = size * 0.315f;
        float depth = size * 0.135f;
        float depression = pressed ? depth * 0.66f : 0f;

        canvas.save();
        canvas.translate(getWidth() * 0.5f, getHeight() * 0.5f - depth * 0.25f);
        canvas.rotate(TILT_DEGREES);

        // Ground shadow under the cylinder.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(Color.argb(150, 65, 0, 8));
        paint.setShadowLayer(size * 0.075f, size * 0.025f, size * 0.055f,
                Color.argb(155, 80, 0, 8));
        canvas.drawOval(new RectF(-rx * 0.92f, -ry + depth * 0.62f,
                rx * 1.02f, ry + depth * 1.45f), paint);
        paint.clearShadowLayer();

        // Stack elliptical slices so the front edge reads as a thick 3D cylinder.
        float step = Math.max(1.5f, size / 160f);
        for (float layer = depth; layer >= depression; layer -= step) {
            float progress = 1f - layer / depth;
            paint.setColor(mixColor(0xFF65060E, 0xFFCE202A, progress));
            canvas.drawOval(new RectF(-rx, -ry + layer, rx, ry + layer), paint);
        }

        // Thick upper bevel and main glossy red top face.
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? 0xFF801019 : 0xFF9D111C);
        canvas.drawOval(new RectF(-rx, -ry + depression, rx, ry + depression), paint);

        RectF top = new RectF(-rx * 0.967f, -ry * 0.959f + depression,
                rx * 0.967f, ry * 0.959f + depression);
        paint.setShader(new LinearGradient(-rx * 0.85f, -ry + depression,
                rx * 0.6f, ry + depression,
                new int[]{0xFFFF777B, 0xFFFF3945, 0xFFF01426, 0xFF9E0718},
                new float[]{0f, 0.34f, 0.70f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawOval(top, paint);
        paint.setShader(null);

        // Rim line makes the red surface look like lacquered, rounded plastic.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, size * 0.010f));
        paint.setColor(0x69FFFFFF);
        canvas.drawOval(new RectF(-rx * 0.91f, -ry * 0.90f + depression,
                rx * 0.91f, ry * 0.90f + depression), paint);
        paint.setStyle(Paint.Style.FILL);

        // Broad reflection on the back/upper half of the top disc.
        canvas.save();
        Path clippedTop = new Path();
        clippedTop.addOval(top, Path.Direction.CW);
        canvas.clipPath(clippedTop);
        paint.setShader(new LinearGradient(0, -ry + depression,
                0, ry * 0.25f + depression,
                new int[]{0xA8FFFFFF, 0x2FFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawOval(new RectF(-rx * 0.79f, -ry * 0.78f + depression,
                rx * 0.56f, ry * 0.12f + depression), paint);
        canvas.restore();
        paint.setShader(null);

        // A crisp, custom-drawn white handset. No emoji font or images needed.
        canvas.save();
        canvas.translate(0f, depression - ry * 0.025f);
        float handsetScale = size / 365f;
        canvas.scale(handsetScale, handsetScale * 0.92f);
        canvas.rotate(-5f);
        paint.setColor(0xFFFCFCFD);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(15f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        Path receiver = new Path();
        receiver.moveTo(-47f, -35f);
        receiver.cubicTo(-41f, -10f, -18f, 17f, 6f, 30f);
        receiver.cubicTo(19f, 37f, 34f, 41f, 45f, 42f);
        canvas.drawPath(receiver, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.save();
        canvas.rotate(-30f, -47f, -35f);
        canvas.drawRoundRect(new RectF(-62f, -51f, -32f, -22f), 10f, 10f, paint);
        canvas.restore();
        canvas.save();
        canvas.rotate(24f, 45f, 42f);
        canvas.drawRoundRect(new RectF(30f, 26f, 60f, 57f), 10f, 10f, paint);
        canvas.restore();
        canvas.restore();
        canvas.restore();
    }

    private float buttonSize() {
        float density = getResources().getDisplayMetrics().density;
        return Math.min(Math.min(getWidth() * 0.79f, getHeight() * 0.59f), 380f * density);
    }

    private boolean isWithinButton(float touchX, float touchY) {
        float size = buttonSize();
        float depth = size * 0.135f;
        float x = touchX - getWidth() * 0.5f;
        float y = touchY - (getHeight() * 0.5f - depth * 0.25f);
        double angle = Math.toRadians(-TILT_DEGREES);
        float unrotatedX = (float) (x * Math.cos(angle) - y * Math.sin(angle));
        float unrotatedY = (float) (x * Math.sin(angle) + y * Math.cos(angle));
        float rx = size * 0.52f;
        float ry = size * 0.345f;
        return (unrotatedX * unrotatedX) / (rx * rx)
                + (unrotatedY * unrotatedY) / (ry * ry) <= 1f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                trackingTap = isWithinButton(event.getX(), event.getY());
                pressed = trackingTap;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (trackingTap) {
                    boolean within = isWithinButton(event.getX(), event.getY());
                    if (pressed != within) {
                        pressed = within;
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                boolean shouldClick = trackingTap && isWithinButton(event.getX(), event.getY());
                trackingTap = false;
                pressed = false;
                invalidate();
                if (shouldClick) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                trackingTap = false;
                pressed = false;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        onPress.run();
        return true;
    }

    private static int mixColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.rgb(r, g, b);
    }
}
