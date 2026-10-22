package org.telegram.othergram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.othergram.OtherGramConfig;
import org.telegram.othergram.theme.OtherGramMaterial;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/**
 * Material You: a minimal Material 3 look for the whole interface with tonal palettes (from the
 * wallpaper on Android 12+, or ready-made ones) and Material motion. Chat bubbles are not touched.
 */
public class OtherGramMaterialActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_ENABLED = 1;
    private static final int ID_MINIMAL = 2;
    private static final int ID_MOTION = 3;
    private static final int ID_PALETTE_BASE = 100;

    private UniversalRecyclerView listView;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme && listView != null) {
            listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
            listView.adapter.update(false);
        }
    }

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
        actionBar.setTitle(getString(R.string.OG_MaterialYou));

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final boolean enabled = OtherGramMaterial.enabled();
        items.add(UItem.asCheck(ID_ENABLED, getString(R.string.OG_MaterialEnable)).setChecked(enabled));
        items.add(UItem.asShadow(getString(R.string.OG_MaterialInfo)));

        items.add(UItem.asHeader(getString(R.string.OG_MaterialPalette)));
        final boolean dark = Theme.isCurrentThemeDark();
        final String current = OtherGramMaterial.palette();
        for (int i = 0; i < OtherGramMaterial.PALETTES.length; i++) {
            final String palette = OtherGramMaterial.PALETTES[i];
            final boolean available = !OtherGramMaterial.PALETTE_DYNAMIC.equals(palette) || OtherGramMaterial.dynamicAvailable();
            final OtherGramMaterial.Scheme scheme = OtherGramMaterial.scheme(OtherGramMaterial.seed(palette), dark);
            final UItem row = UItem.asButton(ID_PALETTE_BASE + i, Theme.createCircleDrawable(dp(22), scheme.primary),
                    getString(OtherGramMaterial.PALETTE_TITLES[i]));
            row.textValue = !available ? getString(R.string.OG_MaterialDynamicUnavailable) : palette.equals(current) ? "✓" : null;
            row.setEnabled(enabled && available);
            items.add(row);
        }
        items.add(UItem.asShadow(getString(R.string.OG_MaterialPaletteInfo)));

        items.add(UItem.asHeader(getString(R.string.OG_MaterialStyle)));
        items.add(UItem.asCheck(ID_MINIMAL, getString(R.string.OG_MaterialMinimal)).setChecked(OtherGramMaterial.minimal()).setEnabled(enabled));
        items.add(UItem.asCheck(ID_MOTION, getString(R.string.OG_MaterialMotion)).setChecked(OtherGramConfig.getBoolean(OtherGramMaterial.KEY_MOTION, true)).setEnabled(enabled));
        items.add(UItem.asShadow(getString(R.string.OG_MaterialStyleInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ENABLED) {
            OtherGramConfig.putBoolean(OtherGramMaterial.KEY_ENABLED, !OtherGramMaterial.enabled());
        } else if (!OtherGramMaterial.enabled()) {
            return;
        } else if (item.id == ID_MINIMAL) {
            OtherGramConfig.putBoolean(OtherGramMaterial.KEY_MINIMAL, !OtherGramMaterial.minimal());
        } else if (item.id == ID_MOTION) {
            OtherGramConfig.putBoolean(OtherGramMaterial.KEY_MOTION, !OtherGramConfig.getBoolean(OtherGramMaterial.KEY_MOTION, true));
            listView.adapter.update(true);
            return;
        } else if (item.id >= ID_PALETTE_BASE && item.id < ID_PALETTE_BASE + OtherGramMaterial.PALETTES.length) {
            final String palette = OtherGramMaterial.PALETTES[item.id - ID_PALETTE_BASE];
            if (OtherGramMaterial.PALETTE_DYNAMIC.equals(palette) && !OtherGramMaterial.dynamicAvailable()) {
                return;
            }
            OtherGramConfig.putString(OtherGramMaterial.KEY_PALETTE, palette);
        } else {
            return;
        }
        Theme.refreshThemeColors();
        listView.adapter.update(true);
    }
}
