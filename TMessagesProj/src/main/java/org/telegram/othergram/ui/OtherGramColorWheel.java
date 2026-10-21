package org.telegram.othergram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

/**
 * Color wheel: hue around the circle, saturation from the center outwards, and a brightness bar
 * under it. Reports every change while the finger moves.
 */
public class OtherGramColorWheel extends View {

    public interface Listener {
        void onColorChanged(int color);
    }

    private static final int[] HUES = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

    private final float[] hsv = {0f, 0f, 1f};
    private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF valueRect = new RectF();
    private Listener listener;
    private float cx, cy, radius;
    private int touchTarget; // 0 none, 1 wheel, 2 brightness

    public OtherGramColorWheel(Context context) {
        super(context);
        knobPaint.setStyle(Paint.Style.STROKE);
        knobPaint.setStrokeWidth(dp(2.5f));
        knobPaint.setColor(0xFFFFFFFF);
        knobPaint.setShadowLayer(dp(2), 0, 0, 0x66000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // for the knob shadow
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setColor(int color) {
        Color.colorToHSV(color, hsv);
        invalidate();
    }

    public int getColor() {
        return Color.HSVToColor(hsv);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int wheel = Math.min(width, dp(220));
        setMeasuredDimension(width, wheel + dp(44));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        radius = Math.min(w, h - dp(44)) / 2f - dp(10);
        cx = w / 2f;
        cy = radius + dp(10);
        final Shader sweep = new SweepGradient(cx, cy, HUES, null);
        final Shader radial = new RadialGradient(cx, cy, radius, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        wheelPaint.setShader(new ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER));
        valueRect.set(dp(24), h - dp(28), w - dp(24), h - dp(12));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Brightness darkens the whole wheel so it shows the colors actually available
        canvas.drawCircle(cx, cy, radius, wheelPaint);
        if (hsv[2] < 1f) {
            knobFill.setColor(Color.argb((int) (255 * (1f - hsv[2])), 0, 0, 0));
            canvas.drawCircle(cx, cy, radius, knobFill);
        }

        // SweepGradient goes clockwise on screen (y points down), so the hue angle is clockwise too
        final double angle = Math.toRadians(hsv[0]);
        final float kx = (float) (cx + Math.cos(angle) * hsv[1] * radius);
        final float ky = (float) (cy + Math.sin(angle) * hsv[1] * radius);
        knobFill.setColor(getColor());
        canvas.drawCircle(kx, ky, dp(9), knobFill);
        canvas.drawCircle(kx, ky, dp(9), knobPaint);

        final int full = Color.HSVToColor(new float[]{hsv[0], hsv[1], 1f});
        valuePaint.setShader(new LinearGradient(valueRect.left, 0, valueRect.right, 0, 0xFF000000, full, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(valueRect, valueRect.height() / 2f, valueRect.height() / 2f, valuePaint);
        final float vx = valueRect.left + valueRect.width() * hsv[2];
        canvas.drawCircle(vx, valueRect.centerY(), dp(10), knobFill);
        canvas.drawCircle(vx, valueRect.centerY(), dp(10), knobPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (Math.hypot(x - cx, y - cy) <= radius + dp(12)) {
                    touchTarget = 1;
                } else if (y >= valueRect.top - dp(16)) {
                    touchTarget = 2;
                } else {
                    return false;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                // fall through to apply the first point
            case MotionEvent.ACTION_MOVE:
                if (touchTarget == 1) {
                    final double dx = x - cx;
                    final double dy = y - cy;
                    float hue = (float) Math.toDegrees(Math.atan2(dy, dx)); // clockwise, like the drawn wheel
                    if (hue < 0) {
                        hue += 360f;
                    }
                    hsv[0] = hue;
                    hsv[1] = (float) Math.min(1.0, Math.hypot(dx, dy) / radius);
                    if (hsv[2] < 0.05f) {
                        hsv[2] = 1f; // picking a hue on a black wheel would be invisible
                    }
                } else if (touchTarget == 2) {
                    hsv[2] = Math.max(0f, Math.min(1f, (x - valueRect.left) / valueRect.width()));
                }
                invalidate();
                if (listener != null) {
                    listener.onColorChanged(getColor());
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchTarget = 0;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(event);
    }
}
