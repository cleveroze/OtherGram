package org.telegram.othergram.badges;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a user's badges after the name as rounded pills: badge color fill, a white check and the
 * badge caption. Sized from the surrounding text; the compact form (chat list) shows one pill with
 * a shortened caption.
 */
public class OtherGramBadgeSpan extends ReplacementSpan {

    private static final float PILL_HEIGHT_TO_TEXT = 0.92f;
    private static final float CAPTION_TO_TEXT = 0.6f;
    private static final float LEADING_TO_HEIGHT = 0.3f;
    private static final float GAP_TO_HEIGHT = 0.2f;
    private static final int COMPACT_MAX_CHARS = 14;

    private final List<OtherGramBadge> badges = new ArrayList<>();
    private final List<String> captions = new ArrayList<>();
    private final TextPaint captionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path checkPath = new Path();
    private final RectF rect = new RectF();

    public OtherGramBadgeSpan(List<OtherGramBadge> badges, boolean compact) {
        for (OtherGramBadge badge : badges) {
            this.badges.add(badge);
            String caption = badge.label;
            if (compact && caption.length() > COMPACT_MAX_CHARS) {
                caption = caption.substring(0, COMPACT_MAX_CHARS - 1).trim() + "…";
            }
            captions.add(caption);
            if (compact) {
                break;
            }
        }
        captionPaint.setTypeface(AndroidUtilities.bold());
        captionPaint.setColor(0xFFFFF7ED);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setColor(0xFFFFFFFF);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    private float pillHeight(Paint paint) {
        return paint.getTextSize() * PILL_HEIGHT_TO_TEXT;
    }

    private float pillWidth(int index, float height) {
        final float pad = height * 0.42f;
        final float check = height * 0.5f;
        final float gap = height * 0.22f;
        return pad + check + gap + captionPaint.measureText(captions.get(index)) + pad;
    }

    private void prepare(Paint paint) {
        captionPaint.setTextSize(paint.getTextSize() * CAPTION_TO_TEXT);
    }

    public int getWidth(Paint paint) {
        prepare(paint);
        final float h = pillHeight(paint);
        float w = h * LEADING_TO_HEIGHT;
        for (int i = 0; i < captions.size(); i++) {
            w += pillWidth(i, h) + (i > 0 ? h * GAP_TO_HEIGHT : 0);
        }
        return (int) Math.ceil(w);
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
        prepare(paint);
        final float h = pillHeight(paint);
        final Paint.FontMetrics fm = paint.getFontMetrics();
        final float centerY = y + (fm.ascent + fm.descent) / 2f;
        final int alpha = paint.getAlpha();
        float left = x + h * LEADING_TO_HEIGHT;
        for (int i = 0; i < captions.size(); i++) {
            final float w = pillWidth(i, h);
            rect.set(left, centerY - h / 2f, left + w, centerY + h / 2f);
            // Slightly deepened badge color keeps the light caption readable on beige badges
            fillPaint.setColor(ColorUtils.blendARGB(badges.get(i).color | 0xFF000000, 0xFF000000, 0.12f));
            fillPaint.setAlpha(alpha);
            canvas.drawRoundRect(rect, h / 2f, h / 2f, fillPaint);

            final float pad = h * 0.42f;
            final float cs = h * 0.5f;
            final float cx = left + pad;
            checkPath.reset();
            checkPath.moveTo(cx, centerY + cs * 0.02f);
            checkPath.lineTo(cx + cs * 0.36f, centerY + cs * 0.36f);
            checkPath.lineTo(cx + cs, centerY - cs * 0.34f);
            checkPaint.setStrokeWidth(Math.max(1.5f, h * 0.11f));
            checkPaint.setAlpha(alpha);
            canvas.drawPath(checkPath, checkPaint);

            captionPaint.setAlpha(alpha);
            final Paint.FontMetrics cfm = captionPaint.getFontMetrics();
            final float baseline = centerY - (cfm.ascent + cfm.descent) / 2f;
            canvas.drawText(captions.get(i), cx + cs + h * 0.22f, baseline, captionPaint);

            left += w + h * GAP_TO_HEIGHT;
        }
    }
}
