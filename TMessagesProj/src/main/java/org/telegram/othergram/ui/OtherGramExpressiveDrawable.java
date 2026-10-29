package org.telegram.othergram.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

/**
 * Material 3 Expressive button background: a shape (a cookie by default) that springs into another
 * shape while pressed, with a small turn. Used for floating action buttons all over OtherGram.
 */
public class OtherGramExpressiveDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] rest;
    private final float[] pressed;
    private float[] current = new float[OtherGramShapes.N];
    private final int color;
    private final int pressedColor;
    private float progress;
    private boolean isPressed;
    private ValueAnimator animator;

    public OtherGramExpressiveDrawable(int color, int pressedColor) {
        this(OtherGramShapes.COOKIE_9, OtherGramShapes.CLOVER_4, color, pressedColor);
    }

    public OtherGramExpressiveDrawable(int restShape, int pressedShape, int color, int pressedColor) {
        this.rest = OtherGramShapes.radii(restShape);
        this.pressed = OtherGramShapes.radii(pressedShape);
        this.color = color;
        this.pressedColor = pressedColor;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(@NonNull int[] state) {
        boolean p = false;
        for (int s : state) {
            if (s == android.R.attr.state_pressed) {
                p = true;
                break;
            }
        }
        if (p == isPressed) {
            return false;
        }
        isPressed = p;
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(progress, p ? 1f : 0f);
        animator.setDuration(p ? 260 : 420);
        animator.setInterpolator(new OvershootInterpolator(p ? 1.2f : 2.2f)); // springy, like Expressive motion
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        animator.start();
        return true;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect b = getBounds();
        final float t = progress;
        current = OtherGramShapes.morph(rest, pressed, Math.max(0f, Math.min(1f, t)), current);
        final float r = Math.min(b.width(), b.height()) / 2f;
        // Overshoot past 1 shows as a little extra turn instead of a broken shape
        OtherGramShapePath.build(path, current, b.exactCenterX(), b.exactCenterY(), r, r, 45f * t);
        paint.setColor(ColorUtils.blendARGB(color, pressedColor, Math.max(0f, Math.min(1f, t))));
        canvas.drawPath(path, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
