package org.telegram.othergram.badges;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * OtherGram badge: a seal with a wavy edge, a thin inner ring and a white check in the center.
 * Drawn in code so every badge type can have its own color.
 */
public class OtherGramBadgeDrawable extends Drawable {

    private static final int LOBES = 12;
    private static final float LOBE_DEPTH = 0.07f;
    private static final int SEAL_POINTS = 120;

    private final Paint sealPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path sealPath = new Path();
    private final Path checkPath = new Path();

    public OtherGramBadgeDrawable(int color) {
        sealPaint.setColor(color);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(0x96FFF7ED);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setColor(0xFFFFFFFF);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setColor(int color) {
        sealPaint.setColor(color);
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        final float size = Math.min(bounds.width(), bounds.height());
        final float r = size / 2f;
        final float cx = bounds.exactCenterX();
        final float cy = bounds.exactCenterY();

        sealPath.reset();
        for (int i = 0; i < SEAL_POINTS; i++) {
            final double t = 2 * Math.PI * i / SEAL_POINTS;
            final double rr = r * (1 - LOBE_DEPTH + LOBE_DEPTH * Math.cos(LOBES * t));
            final float x = (float) (cx + rr * Math.sin(t));
            final float y = (float) (cy - rr * Math.cos(t));
            if (i == 0) {
                sealPath.moveTo(x, y);
            } else {
                sealPath.lineTo(x, y);
            }
        }
        sealPath.close();

        checkPath.reset();
        checkPath.moveTo(cx - r * 0.30f, cy + r * 0.02f);
        checkPath.lineTo(cx - r * 0.08f, cy + r * 0.24f);
        checkPath.lineTo(cx + r * 0.32f, cy - r * 0.20f);

        ringPaint.setStrokeWidth(Math.max(1f, size * 0.035f));
        checkPaint.setStrokeWidth(size * 0.085f);
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect b = getBounds();
        final float r = Math.min(b.width(), b.height()) / 2f;
        canvas.drawPath(sealPath, sealPaint);
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r * 0.70f, ringPaint);
        canvas.drawPath(checkPath, checkPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        sealPaint.setAlpha(alpha);
        ringPaint.setAlpha(alpha * 0x96 / 255);
        checkPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // Badge colors are part of the badge identity and are not tinted
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
