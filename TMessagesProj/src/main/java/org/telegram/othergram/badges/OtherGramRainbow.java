package org.telegram.othergram.badges;

import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Rainbow badges: a shimmering gradient that slowly flows through the pill. Text spans cannot
 * redraw themselves, so the views that show names with badges register here and get invalidated
 * about 30 times a second, but only while a rainbow badge was actually drawn recently.
 */
public class OtherGramRainbow {

    private static final int[] COLORS = {
            0xFFE0475A, 0xFFE8903A, 0xFFD9B52E, 0xFF3DB55A, 0xFF3A8FE0, 0xFF7B55E0, 0xFFD0479A, 0xFFE0475A
    };
    private static final long PERIOD_MS = 2600;
    private static final long FRAME_MS = 33;
    private static final long IDLE_STOP_MS = 1500;

    private static final WeakHashMap<View, Boolean> views = new WeakHashMap<>();
    private static long lastDrawn;
    private static long lastInvalidate;
    private static boolean running;
    private static final Matrix matrix = new Matrix();

    private static final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            final long now = SystemClock.uptimeMillis();
            if (now - lastDrawn > IDLE_STOP_MS || views.isEmpty()) {
                running = false;
                return;
            }
            if (now - lastInvalidate >= FRAME_MS) {
                lastInvalidate = now;
                for (View v : new ArrayList<>(views.keySet())) {
                    if (v != null && v.isAttachedToWindow() && v.isShown()) {
                        v.invalidate();
                    }
                }
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    /** Views that can show names with badges call this once; it costs nothing without rainbow badges. */
    public static void track(View view) {
        if (view != null && Looper.myLooper() == Looper.getMainLooper()) {
            views.put(view, Boolean.TRUE);
        }
    }

    static void markDrawn() {
        lastDrawn = SystemClock.uptimeMillis();
        if (!running && Looper.myLooper() == Looper.getMainLooper()) {
            running = true;
            Choreographer.getInstance().postFrameCallback(frame);
        }
    }

    /** Gradient for a pill starting at {@code left} with height {@code h}, shifted by the current phase. */
    static Shader shader(float left, float h) {
        markDrawn();
        final float period = h * 8f;
        final LinearGradient gradient = new LinearGradient(0, 0, period, 0, COLORS, null, Shader.TileMode.REPEAT);
        final float phase = (SystemClock.uptimeMillis() % PERIOD_MS) / (float) PERIOD_MS;
        matrix.reset();
        matrix.setTranslate(left - phase * period, 0);
        gradient.setLocalMatrix(matrix);
        return gradient;
    }
}
