package org.telegram.othergram.ui;

/**
 * Material 3 Expressive shapes (cookies, clovers, bursts, flowers, rounded polygons, heart...).
 * Every shape is a list of radii sampled around its center, which makes morphing between any two
 * shapes a simple blend of the two lists. Pure math, no Android classes, so it can be tested on a PC;
 * {@link OtherGramShapePath} turns radii into an Android Path.
 */
public final class OtherGramShapes {

    /** Samples around the shape; index 0 points up, angles go clockwise. */
    public static final int N = 144;

    public static final int CIRCLE = 0;
    public static final int COOKIE_4 = 1;
    public static final int COOKIE_6 = 2;
    public static final int COOKIE_7 = 3;
    public static final int COOKIE_9 = 4;
    public static final int COOKIE_12 = 5;
    public static final int CLOVER_4 = 6;
    public static final int CLOVER_8 = 7;
    public static final int SUNNY = 8;
    public static final int VERY_SUNNY = 9;
    public static final int BURST = 10;
    public static final int SOFT_BURST = 11;
    public static final int BOOM = 12;
    public static final int SOFT_BOOM = 13;
    public static final int FLOWER = 14;
    public static final int PUFFY = 15;
    public static final int PENTAGON = 16;
    public static final int GEM = 17;
    public static final int TRIANGLE = 18;
    public static final int DIAMOND = 19;
    public static final int HEART = 20;
    public static final int PUFFY_DIAMOND = 21;
    public static final int COUNT = 22;

    private static final float[][] cache = new float[COUNT][];

    private OtherGramShapes() {
    }

    public static double angle(int i) {
        return 2 * Math.PI * i / N - Math.PI / 2;
    }

    /** Radii of a shape, the largest one is 1. Cached; do not modify the returned array. */
    public static synchronized float[] radii(int shape) {
        if (shape < 0 || shape >= COUNT) {
            shape = CIRCLE;
        }
        if (cache[shape] == null) {
            cache[shape] = normalize(build(shape));
        }
        return cache[shape];
    }

    private static double[] build(int shape) {
        switch (shape) {
            case COOKIE_4: return cookie(4, 0.16);
            case COOKIE_6: return cookie(6, 0.12);
            case COOKIE_7: return cookie(7, 0.11);
            case COOKIE_9: return cookie(9, 0.10);
            case COOKIE_12: return cookie(12, 0.08);
            case CLOVER_4: return smooth(lobes(4, 0.6, 0.55), 2);
            case CLOVER_8: return smooth(lobes(8, 0.74, 0.5), 2);
            case SUNNY: return smooth(ray(star(8, 0.82, 0)), 4);
            case VERY_SUNNY: return smooth(ray(star(8, 0.68, 0)), 2);
            case BURST: return smooth(ray(star(12, 0.72, 0)), 1);
            case SOFT_BURST: return smooth(ray(star(10, 0.74, 0)), 4);
            case BOOM: return ray(star(16, 0.42, 0));
            case SOFT_BOOM: return smooth(petals(16, 0.42, 0.9), 1);
            case FLOWER: return smooth(lobes(8, 0.42, 0.8), 2);
            case PUFFY: return lobes(8, 0.86, 0.35);
            case PENTAGON: return smooth(ray(polygon(5, 0)), 7);
            case GEM: return smooth(ray(polygon(7, 0.2)), 6);
            case TRIANGLE: return smooth(ray(polygon(3, 0)), 10);
            case DIAMOND: return smooth(ray(polygon(4, 0)), 9);
            case HEART: return smooth(ray(heart()), 2);
            case PUFFY_DIAMOND: return smooth(ray(puffyDiamond()), 2);
            default: return circle();
        }
    }

    // ------------------------------------------------------------ generators

    private static double[] circle() {
        final double[] r = new double[N];
        java.util.Arrays.fill(r, 1);
        return r;
    }

    /** Scalloped "cookie": soft waves around the edge. */
    private static double[] cookie(int waves, double depth) {
        final double[] r = new double[N];
        for (int i = 0; i < N; i++) {
            final double t = angle(i) + Math.PI / 2;
            r[i] = 1 - depth * (1 - Math.cos(waves * t)) / 2;
        }
        return r;
    }

    /** Round lobes with notches between them (clovers, flower, puffy). */
    private static double[] lobes(int count, double base, double sharpness) {
        final double[] r = new double[N];
        for (int i = 0; i < N; i++) {
            final double t = angle(i) + Math.PI / 2;
            r[i] = base + (1 - base) * Math.pow(Math.abs(Math.cos(count * t / 2)), sharpness);
        }
        return r;
    }

    /** Thin rounded petals (soft boom). */
    private static double[] petals(int count, double base, double sharpness) {
        final double[] r = new double[N];
        for (int i = 0; i < N; i++) {
            final double t = angle(i) + Math.PI / 2;
            r[i] = base + (1 - base) * Math.pow((1 + Math.cos(count * t)) / 2, sharpness);
        }
        return r;
    }

    private static double[][] polygon(int n, double rotation) {
        final double[][] v = new double[n][];
        for (int k = 0; k < n; k++) {
            final double a = rotation + 2 * Math.PI * k / n - Math.PI / 2;
            v[k] = new double[]{Math.cos(a), Math.sin(a)};
        }
        return v;
    }

    private static double[][] star(int points, double inner, double rotation) {
        final double[][] v = new double[points * 2][];
        for (int k = 0; k < points * 2; k++) {
            final double a = rotation + Math.PI * k / points - Math.PI / 2;
            final double r = k % 2 == 0 ? 1 : inner;
            v[k] = new double[]{r * Math.cos(a), r * Math.sin(a)};
        }
        return v;
    }

    private static double[][] heart() {
        final int n = 90;
        final double[][] v = new double[n][];
        for (int k = 0; k < n; k++) {
            final double t = 2 * Math.PI * k / n;
            final double x = 16 * Math.pow(Math.sin(t), 3);
            final double y = -(13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t));
            v[k] = new double[]{x / 17, (y + 2.5) / 17};
        }
        return v;
    }

    /** Four round lobes in a diamond with a smaller bump between each pair. */
    private static double[][] puffyDiamond() {
        final int n = 120;
        final double[][] v = new double[n][];
        for (int k = 0; k < n; k++) {
            final double t = 2 * Math.PI * k / n - Math.PI / 2;
            final double r = 0.72 + 0.2 * Math.pow(Math.abs(Math.cos(2 * t)), 0.6) + 0.08 * Math.pow(Math.abs(Math.cos(4 * t)), 3);
            v[k] = new double[]{r * Math.cos(t), r * Math.sin(t)};
        }
        return v;
    }

    /** Distance from the center to the outline along each sample direction. */
    private static double[] ray(double[][] verts) {
        final double[] r = new double[N];
        for (int i = 0; i < N; i++) {
            final double dx = Math.cos(angle(i));
            final double dy = Math.sin(angle(i));
            double best = Double.MAX_VALUE;
            for (int j = 0; j < verts.length; j++) {
                final double[] p = verts[j];
                final double[] q = verts[(j + 1) % verts.length];
                final double ex = q[0] - p[0];
                final double ey = q[1] - p[1];
                final double den = dx * ey - dy * ex;
                if (Math.abs(den) < 1e-12) {
                    continue;
                }
                final double t = (p[0] * ey - p[1] * ex) / den;
                final double u = (p[0] * dy - p[1] * dx) / den;
                if (t > 1e-9 && u >= -1e-9 && u <= 1 + 1e-9 && t < best) {
                    best = t;
                }
            }
            r[i] = best == Double.MAX_VALUE ? 1 : best;
        }
        return r;
    }

    /** Rounds corners: circular moving average over the samples. */
    private static double[] smooth(double[] r, int window) {
        final double[] out = new double[N];
        for (int i = 0; i < N; i++) {
            double s = 0;
            for (int k = -window; k <= window; k++) {
                s += r[(i + k + N) % N];
            }
            out[i] = s / (2 * window + 1);
        }
        return out;
    }

    private static float[] normalize(double[] r) {
        double max = 0;
        for (double v : r) {
            max = Math.max(max, v);
        }
        final float[] out = new float[N];
        for (int i = 0; i < N; i++) {
            out[i] = (float) (r[i] / max);
        }
        return out;
    }

    /** Blend of two shapes: 0 gives {@code a}, 1 gives {@code b}. */
    public static float[] morph(float[] a, float[] b, float t, float[] out) {
        if (out == null || out.length != N) {
            out = new float[N];
        }
        for (int i = 0; i < N; i++) {
            out[i] = a[i] + (b[i] - a[i]) * t;
        }
        return out;
    }
}
