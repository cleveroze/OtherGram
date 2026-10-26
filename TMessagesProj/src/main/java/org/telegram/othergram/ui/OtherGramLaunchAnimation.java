package org.telegram.othergram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

import org.telegram.othergram.OtherGramConfig;

/**
 * OtherGram launch animation, drawn in code (4.6 s, like the reference GIF): a thin line in the
 * middle, three dots pop out of it, wavy petals grow from two corners, the dots run a wave, then
 * settle into the OtherGram logo exactly in the center of the screen and the app fades in.
 */
public class OtherGramLaunchAnimation extends View {

    public static final String KEY_ENABLED = "ui_launch_animation";

    private static final long DURATION_MS = 4600;
    private static final long FADE_MS = 280;

    private static final int BACKGROUND = 0xFFE3CEB3;
    private static final int[] DOT_COLORS = {0xFFC4A68A, 0xFFA0876F, 0xFF7C5D46};
    private static final int PETAL_DARK = 0xFFA68A73;
    private static final int PETAL_LIGHT = 0xFFC2A88F;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint petalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path petal = new Path();
    private float t; // seconds since start
    private float lastPetalW = -1;

    public static boolean enabled() {
        return OtherGramConfig.getBoolean(KEY_ENABLED, true);
    }

    /** Shows the animation over the whole window on a cold start; it removes itself when done. */
    public static void show(Activity activity) {
        if (activity == null || !enabled()) {
            return;
        }
        try {
            final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            final OtherGramLaunchAnimation view = new OtherGramLaunchAnimation(activity);
            decor.addView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            view.start();
        } catch (Throwable ignore) {
        }
    }

    private OtherGramLaunchAnimation(Context context) {
        super(context);
        setClickable(true); // the app under it is not touched while the logo plays
        setOnClickListener(v -> finish()); // a tap skips it
    }

    private ValueAnimator animator;
    private boolean finishing;

    private void start() {
        animator = ValueAnimator.ofFloat(0f, DURATION_MS / 1000f);
        animator.setDuration(DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finish();
            }
        });
        animator.start();
    }

    private void finish() {
        if (finishing) {
            return;
        }
        finishing = true;
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
        }
        animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            if (getParent() instanceof ViewGroup) {
                ((ViewGroup) getParent()).removeView(this);
            }
        }).start();
    }

    // ------------------------------------------------------------ easing helpers

    private static float clamp(float x) {
        return Math.max(0f, Math.min(1f, x));
    }

    private static float range(float t, float from, float to) {
        return clamp((t - from) / (to - from));
    }

    private static float overshoot(float x) {
        final float s = 1.7f;
        x -= 1f;
        return x * x * ((s + 1f) * x + s) + 1f;
    }

    private static float easeOut(float x) {
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }

    // ------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        canvas.drawColor(BACKGROUND);
        drawPetals(canvas, w, h);
        drawLineAndDots(canvas, w, h);
    }

    private void drawPetals(Canvas canvas, int w, int h) {
        final float grow = overshoot(range(t, 0.35f, 0.85f));
        if (grow <= 0f) {
            return;
        }
        if (lastPetalW != w) {
            lastPetalW = w;
            buildPetal(w * 0.62f);
        }
        // A slow wobble while the dots dance, calm at the end
        final float wobble = (float) Math.sin(t * 2.4f) * 5f * (1f - range(t, 2.9f, 3.6f));
        petalPaint.setShader(new LinearGradient(-w * 0.62f, -w * 0.62f, w * 0.62f, w * 0.62f, PETAL_DARK, PETAL_LIGHT, Shader.TileMode.CLAMP));

        canvas.save();
        canvas.translate(w * 0.02f, h * 0.02f);
        canvas.rotate(-25f * (1f - grow) + wobble);
        canvas.scale(grow, grow);
        canvas.drawPath(petal, petalPaint);
        canvas.restore();

        canvas.save();
        canvas.translate(w * 0.98f, h * 0.98f);
        canvas.rotate(180f + 25f * (1f - grow) - wobble);
        canvas.scale(grow, grow);
        canvas.drawPath(petal, petalPaint);
        canvas.restore();
    }

    /** A wavy, nine-petal blob around (0, 0), like the shapes in the OtherGram logo. */
    private void buildPetal(float radius) {
        petal.reset();
        final int points = 180;
        for (int i = 0; i <= points; i++) {
            final double a = 2 * Math.PI * i / points;
            final double r = radius * (0.8 + 0.2 * Math.cos(9 * a + 0.6));
            final float x = (float) (r * Math.cos(a));
            final float y = (float) (r * Math.sin(a));
            if (i == 0) petal.moveTo(x, y);
            else petal.lineTo(x, y);
        }
        petal.close();
    }

    private void drawLineAndDots(Canvas canvas, int w, int h) {
        final float cx = w / 2f;
        final float cy = h / 2f; // the logo ends exactly in the middle of the screen
        final float spacing = w * 0.2f;
        final float baseR = w * 0.055f;

        // Thin line the dots come out of
        final float lineAlpha = 1f - range(t, 0.15f, 0.45f);
        if (lineAlpha > 0f) {
            final float lineW = spacing * 2.6f * easeOut(range(t, 0f, 0.12f));
            paint.setColor(DOT_COLORS[1]);
            paint.setAlpha((int) (200 * lineAlpha));
            paint.setStrokeWidth(Math.max(2f, w * 0.006f));
            canvas.drawLine(cx - lineW / 2f, cy, cx + lineW / 2f, cy, paint);
        }

        // Final grow: the dots end a little bigger, like in the logo
        final float settle = easeOut(range(t, 2.9f, 3.4f));
        final float finalScale = 1f + 0.25f * settle;
        // Wave amplitude: strongest in the middle of the dance, gone once settled
        final float wave = range(t, 0.7f, 1.0f) * (1f - settle);

        for (int i = 0; i < 3; i++) {
            final float appear = overshoot(range(t, 0.05f + i * 0.06f, 0.32f + i * 0.06f));
            if (appear <= 0f) {
                continue;
            }
            final double phase = t * 2 * Math.PI * 1.25 - i * 0.9;
            final float bob = (float) Math.sin(phase);
            final float y = cy - bob * baseR * 0.9f * wave;
            // Squash and stretch: wide when low, tall when high
            final float squash = 1f + 0.18f * (float) Math.cos(phase) * wave;
            final float rx = baseR * finalScale * appear * squash;
            final float ry = baseR * finalScale * appear / squash * (0.15f + 0.85f * range(t, 0.05f + i * 0.06f, 0.2f + i * 0.06f));
            final float x = cx + (i - 1) * spacing * (0.85f + 0.15f * settle);
            paint.setColor(DOT_COLORS[i]);
            paint.setAlpha(255);
            canvas.drawOval(x - rx, y - ry, x + rx, y + ry, paint);
        }
    }
}
