package org.telegram.othergram.badges;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a user's badges inline right after the name, sized to the surrounding text.
 */
public class OtherGramBadgeSpan extends ReplacementSpan {

    private static final float SIZE_TO_TEXT = 0.95f;
    private static final float GAP_TO_SIZE = 0.18f;
    private static final float LEADING_TO_SIZE = 0.28f;

    private final List<OtherGramBadgeDrawable> drawables = new ArrayList<>();

    public OtherGramBadgeSpan(List<OtherGramBadge> badges) {
        for (OtherGramBadge badge : badges) {
            drawables.add(new OtherGramBadgeDrawable(badge.color));
        }
    }

    private static float badgeSize(Paint paint) {
        return paint.getTextSize() * SIZE_TO_TEXT;
    }

    public int getWidth(Paint paint) {
        final float size = badgeSize(paint);
        final int n = drawables.size();
        return (int) Math.ceil(size * LEADING_TO_SIZE + n * size + Math.max(0, n - 1) * size * GAP_TO_SIZE);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            paint.getFontMetricsInt(fm);
        }
        return getWidth(paint);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        final float size = badgeSize(paint);
        final Paint.FontMetrics fm = paint.getFontMetrics();
        final float centerY = y + (fm.ascent + fm.descent) / 2f;
        float left = x + size * LEADING_TO_SIZE;
        for (OtherGramBadgeDrawable drawable : drawables) {
            drawable.setBounds((int) left, (int) (centerY - size / 2f), (int) (left + size), (int) (centerY + size / 2f));
            drawable.setAlpha(paint.getAlpha());
            drawable.draw(canvas);
            left += size * (1 + GAP_TO_SIZE);
        }
    }
}
