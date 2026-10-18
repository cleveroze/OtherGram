package org.telegram.othergram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.othergram.theme.OtherGramColors;
import org.telegram.othergram.ui.OtherGramColorPicker;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/**
 * Appearance: ready-made palettes and a color for each group of interface elements.
 * Colors are stored separately for the light and the dark theme; this screen edits the active one.
 */
public class OtherGramAppearanceActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int ID_PRESET_DEFAULT = 1;
    private static final int ID_RESET_ALL = 2;
    private static final int ID_PRESET_BASE = 100;
    private static final int ID_GROUP_BASE = 1000;

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
        actionBar.setTitle(getString(R.string.OG_Appearance));

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = contentView;
    }

    private boolean isDark() {
        return Theme.isCurrentThemeDark();
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final boolean dark = isDark();
        items.add(UItem.asHeader(getString(R.string.OG_Palettes)));
        items.add(UItem.asButton(ID_PRESET_DEFAULT, Theme.createCircleDrawable(dp(22), 0xFFA68770), getString(R.string.OG_PresetVanilla)));
        for (int i = 0; i < OtherGramColors.PRESETS.length; i++) {
            final OtherGramColors.Preset preset = OtherGramColors.PRESETS[i];
            items.add(UItem.asButton(ID_PRESET_BASE + i, Theme.createCircleDrawable(dp(22), preset.previewColor(dark)), getString(preset.titleRes)));
        }
        items.add(UItem.asShadow(getString(dark ? R.string.OG_EditingDark : R.string.OG_EditingLight)));

        items.add(UItem.asHeader(getString(R.string.OG_InterfaceColors)));
        for (int i = 0; i < OtherGramColors.GROUPS.length; i++) {
            final OtherGramColors.Group group = OtherGramColors.GROUPS[i];
            final int color = OtherGramColors.getColor(group);
            final CharSequence value = OtherGramColors.hasOverride(group, dark) ? OtherGramColorPicker.toHex(color) : getString(R.string.OG_Default);
            final UItem row = UItem.asButton(ID_GROUP_BASE + i, Theme.createCircleDrawable(dp(22), color), getString(group.titleRes));
            row.textValue = value;
            items.add(row);
        }
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_RESET_ALL, R.drawable.msg_reset, getString(R.string.OG_ResetColors)).red());
        items.add(UItem.asShadow(null));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        final boolean dark = isDark();
        if (item.id == ID_PRESET_DEFAULT || item.id == ID_RESET_ALL) {
            OtherGramColors.resetAll(dark);
        } else if (item.id >= ID_PRESET_BASE && item.id < ID_PRESET_BASE + OtherGramColors.PRESETS.length) {
            OtherGramColors.applyPreset(OtherGramColors.PRESETS[item.id - ID_PRESET_BASE], dark);
        } else if (item.id >= ID_GROUP_BASE && item.id < ID_GROUP_BASE + OtherGramColors.GROUPS.length) {
            final OtherGramColors.Group group = OtherGramColors.GROUPS[item.id - ID_GROUP_BASE];
            OtherGramColorPicker.show(this, getString(group.titleRes), OtherGramColors.getColor(group),
                    color -> OtherGramColors.setColor(group, dark, color),
                    OtherGramColors.hasOverride(group, dark) ? () -> OtherGramColors.reset(group, dark) : null);
        }
    }
}
