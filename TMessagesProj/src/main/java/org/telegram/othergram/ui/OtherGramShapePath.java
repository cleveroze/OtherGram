package org.telegram.othergram.ui;

import android.graphics.Path;
import android.graphics.RectF;

/** Turns {@link OtherGramShapes} radii into smooth Android paths. */
public final class OtherGramShapePath {

    private static final float[] xs = new float[OtherGramShapes.N];
    private static final float[] ys = new float[OtherGramShapes.N];
    private static final double[] cap = new double[4];

    /** Point (x, y) and outward normal at arc length {@code s} on a capsule centered at 0, from the top-left. */
    private static void capsulePoint(double s, double a, double b, double straight) {
        final double h = a - b;
        if (s < straight) {
            cap[0] = -h + s; cap[1] = -b; cap[2] = 0; cap[3] = -1;
            return;
        }
        s -= straight;
        if (s < Math.PI * b) {
            final double ang = -Math.PI / 2 + s / b;
            cap[0] = h + b * Math.cos(ang); cap[1] = b * Math.sin(ang); cap[2] = Math.cos(ang); cap[3] = Math.sin(ang);
            return;
        }
        s -= Math.PI * b;
        if (s < straight) {
            cap[0] = h - s; cap[1] = b; cap[2] = 0; cap[3] = 1;
            return;
        }
        s -= straight;
        final double ang = Math.PI / 2 + s / b;
        cap[0] = -h + b * Math.cos(ang); cap[1] = b * Math.sin(ang); cap[2] = Math.cos(ang); cap[3] = Math.sin(ang);
    }

    private OtherGramShapePath() {
    }

    /**
     * Shape centered at (cx, cy) with horizontal/vertical radii rx/ry, turned by {@code rotation} degrees.
     * Points are joined with quadratic curves through their midpoints, so edges stay smooth.
     */
    public static synchronized void build(Path out, float[] radii, float cx, float cy, float rx, float ry, float rotation) {
        final int n = OtherGramShapes.N;
        final double rot = Math.toRadians(rotation);
        for (int i = 0; i < n; i++) {
            final double a = OtherGramShapes.angle(i) + rot;
            xs[i] = (float) (cx + rx * radii[i] * Math.cos(a));
            ys[i] = (float) (cy + ry * radii[i] * Math.sin(a));
        }
        out.reset();
        out.moveTo((xs[n - 1] + xs[0]) / 2f, (ys[n - 1] + ys[0]) / 2f);
        for (int i = 0; i < n; i++) {
            final int next = (i + 1) % n;
            out.quadTo(xs[i], ys[i], (xs[i] + xs[next]) / 2f, (ys[i] + ys[next]) / 2f);
        }
        out.close();
    }

    public static void build(Path out, int shape, RectF bounds, float rotation) {
        build(out, OtherGramShapes.radii(shape), bounds.centerX(), bounds.centerY(), bounds.width() / 2f, bounds.height() / 2f, rotation);
    }

    /**
     * Morph from a pill (the rounded-rect shape of a selected tab) filling {@code r} to {@code target}
     * stretched over the same box. {@code t} = 0 gives the pill, 1 the target shape.
     */
    public static synchronized void morphPill(Path out, RectF r, float[] target, float t) {
        // A wavy pill: the capsule outline gets even scallops along its whole length (Expressive "cookie" edge).
        // {@code target} is kept for API symmetry; the wave count comes from the pill length.
        final int n = OtherGramShapes.N;
        final float a = r.width() / 2f;
        final float b = Math.min(r.height() / 2f, a);
        final float cx = r.centerX();
        final float cy = r.centerY();
        final double straight = 2 * (a - b);
        final double length = 2 * straight + 2 * Math.PI * b;
        final int waves = Math.max(8, (int) Math.round(length / (b * 0.85)));
        for (int i = 0; i < n; i++) {
            final double s = length * i / n;
            final double off = -b * 0.28 * t * (1 - Math.cos(waves * 2 * Math.PI * i / n)) / 2;
            capsulePoint(s, a, b, straight);
            xs[i] = (float) (cx + cap[0] + cap[2] * off);
            ys[i] = (float) (cy + cap[1] + cap[3] * off);
        }
        out.reset();
        out.moveTo((xs[n - 1] + xs[0]) / 2f, (ys[n - 1] + ys[0]) / 2f);
        for (int i = 0; i < n; i++) {
            final int next = (i + 1) % n;
            out.quadTo(xs[i], ys[i], (xs[i] + xs[next]) / 2f, (ys[i] + ys[next]) / 2f);
        }
        out.close();
    }
}
