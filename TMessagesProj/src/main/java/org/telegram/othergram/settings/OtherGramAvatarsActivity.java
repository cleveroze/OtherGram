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
        listView.setSections();
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
            preview.invalidate();
            listView.adapter.update(true);
        }
    }

    /** Three sample avatars in the theme colors, drawn with the selected shape. */
    private class PreviewView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        PreviewView(Context context) {
            super(context);
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
            for (int color : colors) {
                paint.setColor(color);
                AndroidUtilities.rectTmp.set(left, top, left + size, top + size);
                if (OtherGramAvatars.hasPathShape()) {
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
