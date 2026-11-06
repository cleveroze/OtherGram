package org.telegram.othergram.theme;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SweepGradient;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.othergram.OtherGramConfig;
import org.telegram.othergram.network.OtherGramProfiles;

/**
 * Avatar shape for users and regular groups, and the published avatar ring of other OtherGram users.
 *
 * Telegram draws avatars as images clipped with a corner radius of half their size. Rounded shapes
 * scale that radius; star shapes replace the clip with a path (ImageReceiver and AvatarDrawable ask
 * {@link #shapePath}). Forums keep Telegram's own rounded squares.
 */
public class OtherGramAvatars {

    public static final int SHAPE_CIRCLE = 0;
    public static final int SHAPE_SQUIRCLE = 1;
    public static final int SHAPE_ROUNDED = 2;
    public static final int SHAPE_SQUARE = 3;
    public static final int SHAPE_STAR = 4;
    public static final int SHAPE_MATERIAL_STAR = 5;
    public static final int SHAPE_HEXAGON = 6;
    public static final int SHAPE_OCTAGON = 7;
    // Material 3 Expressive shapes (2.0)
    public static final int SHAPE_COOKIE_4 = 8;
    public static final int SHAPE_COOKIE_6 = 9;
    public static final int SHAPE_COOKIE_12 = 10;
    public static final int SHAPE_CLOVER_4 = 11;
    public static final int SHAPE_CLOVER_8 = 12;
    public static final int SHAPE_SUNNY = 13;
    public static final int SHAPE_SOFT_BURST = 14;
    public static final int SHAPE_BURST = 15;
    public static final int SHAPE_FLOWER = 16;
    public static final int SHAPE_PUFFY = 17;
    public static final int SHAPE_PENTAGON = 18;
    public static final int SHAPE_GEM = 19;
    public static final int SHAPE_HEART = 20;
    // Material 3 Expressive basics (2.1)
    public static final int SHAPE_OVAL = 21;
    public static final int SHAPE_PILL = 22;

    public static final int[] SHAPES = {SHAPE_MATERIAL_STAR, SHAPE_COOKIE_4, SHAPE_COOKIE_6, SHAPE_COOKIE_12, SHAPE_CLOVER_4, SHAPE_CLOVER_8,
            SHAPE_SUNNY, SHAPE_SOFT_BURST, SHAPE_BURST, SHAPE_FLOWER, SHAPE_PUFFY, SHAPE_PENTAGON, SHAPE_GEM, SHAPE_HEART, SHAPE_OVAL, SHAPE_PILL,
            SHAPE_CIRCLE, SHAPE_SQUIRCLE, SHAPE_ROUNDED, SHAPE_SQUARE, SHAPE_STAR, SHAPE_HEXAGON, SHAPE_OCTAGON};
    public static final int[] SHAPE_TITLES = {R.string.OG_AvatarMaterialStar, R.string.OG_ShapeCookie4, R.string.OG_ShapeCookie6, R.string.OG_ShapeCookie12,
            R.string.OG_ShapeClover4, R.string.OG_ShapeClover8, R.string.OG_ShapeSunny, R.string.OG_ShapeSoftBurst, R.string.OG_ShapeBurst,
            R.string.OG_ShapeFlower, R.string.OG_ShapePuffy, R.string.OG_ShapePentagon, R.string.OG_ShapeGem, R.string.OG_ShapeHeart,
            R.string.OG_ShapeOval, R.string.OG_ShapePill,
            R.string.OG_AvatarCircle, R.string.OG_AvatarSquircle, R.string.OG_AvatarRounded, R.string.OG_AvatarSquare,
            R.string.OG_AvatarStar, R.string.OG_AvatarHexagon, R.string.OG_AvatarOctagon};

    /** Library shape behind an avatar shape id, or -1 for shapes drawn by this class. */
    private static int libraryShape(int s) {
        switch (s) {
            case SHAPE_MATERIAL_STAR: return org.telegram.othergram.ui.OtherGramShapes.COOKIE_9;
            case SHAPE_COOKIE_4: return org.telegram.othergram.ui.OtherGramShapes.COOKIE_4;
            case SHAPE_COOKIE_6: return org.telegram.othergram.ui.OtherGramShapes.COOKIE_6;
            case SHAPE_COOKIE_12: return org.telegram.othergram.ui.OtherGramShapes.COOKIE_12;
            case SHAPE_CLOVER_4: return org.telegram.othergram.ui.OtherGramShapes.CLOVER_4;
            case SHAPE_CLOVER_8: return org.telegram.othergram.ui.OtherGramShapes.CLOVER_8;
            case SHAPE_SUNNY: return org.telegram.othergram.ui.OtherGramShapes.SUNNY;
            case SHAPE_SOFT_BURST: return org.telegram.othergram.ui.OtherGramShapes.SOFT_BURST;
            case SHAPE_BURST: return org.telegram.othergram.ui.OtherGramShapes.BURST;
            case SHAPE_FLOWER: return org.telegram.othergram.ui.OtherGramShapes.FLOWER;
            case SHAPE_PUFFY: return org.telegram.othergram.ui.OtherGramShapes.PUFFY;
            case SHAPE_PENTAGON: return org.telegram.othergram.ui.OtherGramShapes.PENTAGON;
            case SHAPE_GEM: return org.telegram.othergram.ui.OtherGramShapes.GEM;
            case SHAPE_HEART: return org.telegram.othergram.ui.OtherGramShapes.HEART;
            case SHAPE_OVAL: return org.telegram.othergram.ui.OtherGramShapes.OVAL;
            case SHAPE_PILL: return org.telegram.othergram.ui.OtherGramShapes.PILL;
            default: return -1;
        }
    }

    private static final String KEY_SHAPE = "avatar_shape";
    private static volatile int shape = -1;

    public static int getShape() {
        if (shape < 0) {
            shape = OtherGramConfig.getInt(KEY_SHAPE, SHAPE_MATERIAL_STAR); // 2.0: Material 3 Expressive cookie by default
        }
        return shape;
    }

    public static void setShape(int value) {
        shape = value;
        OtherGramConfig.putInt(KEY_SHAPE, value);
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.isValidAccount(a)) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR);
                }
            }
        });
    }

    /** Shapes that are clipped with a path instead of a corner radius. */
    public static boolean hasPathShape() {
        final int s = getShape();
        return s == SHAPE_STAR || s == SHAPE_HEXAGON || s == SHAPE_OCTAGON || libraryShape(s) >= 0;
    }

    /** Share of the circle radius kept by rounded shapes. Path shapes keep the circle as fallback. */
    public static float radiusFactor() {
        switch (getShape()) {
            case SHAPE_SQUIRCLE:
                return 0.62f;
            case SHAPE_ROUNDED:
                return 0.36f;
            case SHAPE_SQUARE:
                return 0f;
            default:
                return 1f;
        }
    }

    public static boolean isCircle() {
        return getShape() == SHAPE_CIRCLE || hasPathShape();
    }

    /** Radius actually used for an avatar that callers asked to round with {@code requested}. */
    public static int adjust(int requested) {
        return isCircle() ? requested : Math.round(requested * radiusFactor());
    }

    /** Fills {@code out} with the current path shape inside {@code r}. */
    public static void shapePath(RectF r, Path out) {
        out.reset();
        final float cx = r.centerX();
        final float cy = r.centerY();
        final float radius = Math.min(r.width(), r.height()) / 2f;
        final int library = libraryShape(getShape());
        if (library >= 0) {
            org.telegram.othergram.ui.OtherGramShapePath.build(out, org.telegram.othergram.ui.OtherGramShapes.radii(library), cx, cy, radius, radius, 0);
            return;
        }
        switch (getShape()) {
            case SHAPE_MATERIAL_STAR:
                // Material You style "cookie" star: smooth scalloped edge
                wavy(out, cx, cy, radius, 9, 0.085f);
                break;
            case SHAPE_STAR:
                roundedStar(out, cx, cy, radius, 5, 0.58f);
                break;
            case SHAPE_HEXAGON:
                roundedPolygon(out, cx, cy, radius, 6, 0.12f);
                break;
            case SHAPE_OCTAGON:
                roundedPolygon(out, cx, cy, radius, 8, 0.1f);
                break;
            default:
                out.addCircle(cx, cy, radius, Path.Direction.CW);
                break;
        }
        out.close();
    }

    private static void wavy(Path out, float cx, float cy, float r, int lobes, float depth) {
        final int n = 144;
        for (int i = 0; i < n; i++) {
            final double t = 2 * Math.PI * i / n;
            final double rr = r * (1 - depth + depth * Math.cos(lobes * t));
            final float x = (float) (cx + rr * Math.sin(t));
            final float y = (float) (cy - rr * Math.cos(t));
            if (i == 0) {
                out.moveTo(x, y);
            } else {
                out.lineTo(x, y);
            }
        }
    }

    /** Star with softened tips: corners are replaced by quadratic curves through each vertex. */
    private static void roundedStar(Path out, float cx, float cy, float r, int points, float innerRatio) {
        final int n = points * 2;
        final float[] xs = new float[n];
        final float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            final double t = Math.PI * i / points;
            final float rr = i % 2 == 0 ? r : r * innerRatio;
            xs[i] = (float) (cx + rr * Math.sin(t));
            ys[i] = (float) (cy - rr * Math.cos(t));
        }
        smoothPolygon(out, xs, ys, 0.22f);
    }

    private static void roundedPolygon(Path out, float cx, float cy, float r, int sides, float rounding) {
        final float[] xs = new float[sides];
        final float[] ys = new float[sides];
        for (int i = 0; i < sides; i++) {
            final double t = 2 * Math.PI * i / sides + Math.PI / sides;
            xs[i] = (float) (cx + r * Math.sin(t));
            ys[i] = (float) (cy - r * Math.cos(t));
        }
        smoothPolygon(out, xs, ys, rounding);
    }

    /** Polygon whose corners are cut at {@code rounding} of each edge and joined with curves through the vertex. */
    private static void smoothPolygon(Path out, float[] xs, float[] ys, float rounding) {
        final int n = xs.length;
        for (int i = 0; i < n; i++) {
            final int prev = (i - 1 + n) % n;
            final int next = (i + 1) % n;
            final float ax = xs[i] + (xs[prev] - xs[i]) * rounding;
            final float ay = ys[i] + (ys[prev] - ys[i]) * rounding;
            final float bx = xs[i] + (xs[next] - xs[i]) * rounding;
            final float by = ys[i] + (ys[next] - ys[i]) * rounding;
            if (i == 0) {
                out.moveTo(ax, ay);
            } else {
                out.lineTo(ax, ay);
            }
            out.quadTo(xs[i], ys[i], bx, by);
        }
    }

    // ------------------------------------------------------------ ring

    private static final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path ringPath = new Path();
    private static final RectF ringRect = new RectF();

    /**
     * Draws the avatar ring the user published (if any) just inside {@code bounds}, following the
     * current avatar shape. Called from ImageReceiver and AvatarDrawable on the UI thread.
     */
    public static void drawRing(Canvas canvas, RectF bounds, long userId, int alpha) {
        final OtherGramProfiles.Profile profile = userId > 0 ? OtherGramProfiles.getProfile(userId) : null;
        if (profile == null || profile.ringStyle == null || bounds.width() < AndroidUtilities.dp(16)) {
            return;
        }
        drawRing(canvas, bounds, profile.ringStyle, profile.ringColor, profile.ringColor2, alpha);
    }

    public static void drawRing(Canvas canvas, RectF bounds, String style, int color, int color2, int alpha) {
        final float size = Math.min(bounds.width(), bounds.height());
        final float stroke = Math.max(AndroidUtilities.dp(1.5f), size * 0.055f);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(stroke);
        ringPaint.setShader(null);
        ringPaint.setPathEffect(null);
        ringPaint.setColor(color | 0xFF000000);
        if ("gradient".equals(style)) {
            ringPaint.setShader(new SweepGradient(bounds.centerX(), bounds.centerY(), new int[]{color | 0xFF000000, color2 | 0xFF000000, color | 0xFF000000}, null));
        } else if ("dashed".equals(style)) {
            ringPaint.setPathEffect(new DashPathEffect(new float[]{stroke * 1.6f, stroke * 1.1f}, 0));
        }
        ringPaint.setAlpha(alpha);
        ringRect.set(bounds);
        ringRect.inset(stroke / 2f, stroke / 2f);
        ringOutline(ringRect);
        canvas.drawPath(ringPath, ringPaint);
        if ("double".equals(style)) {
            ringPaint.setStrokeWidth(stroke * 0.5f);
            ringRect.inset(stroke * 1.4f, stroke * 1.4f);
            ringOutline(ringRect);
            canvas.drawPath(ringPath, ringPaint);
        }
    }

    private static void ringOutline(RectF r) {
        if (hasPathShape()) {
            shapePath(r, ringPath);
        } else {
            ringPath.reset();
            final float rr = r.width() / 2f * radiusFactor();
            ringPath.addRoundRect(r, rr, rr, Path.Direction.CW);
        }
    }
}
