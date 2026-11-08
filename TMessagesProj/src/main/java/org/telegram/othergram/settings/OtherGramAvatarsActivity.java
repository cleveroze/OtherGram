package org.telegram.othergram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.othergram.theme.OtherGramAvatars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Avatar shape choice with a live preview. */
public class OtherGramAvatarsActivity extends BaseFragment {

    private static final int ID_SHAPE_BASE = 100;

    private UniversalRecyclerView listView;
    private PreviewView preview;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setTitle(getString(R.string.OG_Avatars));

        preview = new PreviewView(context);

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections(org.telegram.messenger.AndroidUtilities.dp(12), org.telegram.messenger.AndroidUtilities.dp(24), false); // Material 3 Expressive: large rounded cards
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(preview, 120));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.OG_AvatarShape)));
        final int current = OtherGramAvatars.getShape();
        for (int i = 0; i < OtherGramAvatars.SHAPES.length; i++) {
            items.add(UItem.asRadio(ID_SHAPE_BASE + i, getString(OtherGramAvatars.SHAPE_TITLES[i])).setChecked(OtherGramAvatars.SHAPES[i] == current));
        }
        items.add(UItem.asShadow(getString(R.string.OG_AvatarShapeInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        final int index = item.id - ID_SHAPE_BASE;
        if (index >= 0 && index < OtherGramAvatars.SHAPES.length) {
            OtherGramAvatars.setShape(OtherGramAvatars.SHAPES[index]);
            preview.animateTo(OtherGramAvatars.SHAPES[index]);
            listView.adapter.update(true);
        }
    }

    /** Three sample avatars in the theme colors; the shape morphs when the selection changes. */
    private class PreviewView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int shownShape = OtherGramAvatars.getShape();
        private float[] fromRadii;
        private float[] toRadii;
        private float[] morphBuffer;
        private float morphT = 1f;
        private android.animation.ValueAnimator animator;

        PreviewView(Context context) {
            super(context);
        }

        void animateTo(int newShape) {
            final int from = shownShape;
            shownShape = newShape;
            final float[] a = OtherGramAvatars.libraryRadii(from);
            final float[] b = OtherGramAvatars.libraryRadii(newShape);
            if (a == null || b == null) {
                fromRadii = null;
                toRadii = null;
                morphT = 1f;
                invalidate();
                return;
            }
            fromRadii = a;
            toRadii = b;
            if (animator != null) {
                animator.cancel();
            }
            animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(320);
            animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            animator.addUpdateListener(v -> {
                morphT = (float) v.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            final int[] colors = {
                    Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                    0xFF8C6A52, 0xFFC2A48C
            };
            final float size = dp(64);
            final float gap = dp(20);
            final float total = colors.length * size + (colors.length - 1) * gap;
            float left = (getWidth() - total) / 2f;
            final float top = (getHeight() - size) / 2f;
            final float r = size / 2f * OtherGramAvatars.radiusFactor();
            final boolean morphing = fromRadii != null && toRadii != null && morphT < 1f;
            for (int color : colors) {
                paint.setColor(color);
                AndroidUtilities.rectTmp.set(left, top, left + size, top + size);
                if (morphing) {
                    morphBuffer = org.telegram.othergram.ui.OtherGramShapes.morph(fromRadii, toRadii, morphT, morphBuffer);
                    org.telegram.othergram.ui.OtherGramShapePath.build(path, morphBuffer,
                            AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY(), size / 2f, size / 2f, 0);
                    canvas.drawPath(path, paint);
                } else if (OtherGramAvatars.hasPathShape()) {
                    OtherGramAvatars.shapePath(AndroidUtilities.rectTmp, path);
                    canvas.drawPath(path, paint);
                } else {
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint);
                }
                left += size + gap;
            }
        }
    }
}
