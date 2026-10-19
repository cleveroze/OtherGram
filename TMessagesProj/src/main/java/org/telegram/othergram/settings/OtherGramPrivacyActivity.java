package org.telegram.othergram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.R;
import org.telegram.othergram.OtherGramConfig;
import org.telegram.othergram.privacy.OtherGramDeletedMessages;
import org.telegram.othergram.privacy.OtherGramGhost;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Ghost mode and keeping deleted messages. Both are off by default. */
public class OtherGramPrivacyActivity extends BaseFragment {

    private static final int ID_NO_READ = 1;
    private static final int ID_NO_TYPING = 2;
    private static final int ID_NO_ONLINE = 3;
    private static final int ID_KEEP_DELETED = 4;
    private static final int ID_CLEAR_DELETED = 5;

    private UniversalRecyclerView listView;

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
        actionBar.setTitle(getString(R.string.OG_Privacy));

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.OG_GhostMode)));
        items.add(UItem.asCheck(ID_NO_READ, getString(R.string.OG_GhostNoRead)).setChecked(OtherGramGhost.hideRead()));
        items.add(UItem.asCheck(ID_NO_TYPING, getString(R.string.OG_GhostNoTyping)).setChecked(OtherGramGhost.hideTyping()));
        items.add(UItem.asCheck(ID_NO_ONLINE, getString(R.string.OG_GhostNoOnline)).setChecked(OtherGramGhost.hideOnline()));
        items.add(UItem.asShadow(getString(R.string.OG_GhostInfo)));

        items.add(UItem.asHeader(getString(R.string.OG_DeletedMessages)));
        items.add(UItem.asCheck(ID_KEEP_DELETED, getString(R.string.OG_KeepDeleted)).setChecked(OtherGramDeletedMessages.isEnabled()));
        items.add(UItem.asButton(ID_CLEAR_DELETED, R.drawable.msg_clear, getString(R.string.OG_ClearDeletedMarks)));
        items.add(UItem.asShadow(getString(R.string.OG_KeepDeletedInfo)));
    }

    private void toggle(String key) {
        OtherGramConfig.putBoolean(key, !OtherGramConfig.getBoolean(key, false));
        listView.adapter.update(true);
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_NO_READ:
                toggle(OtherGramGhost.KEY_NO_READ);
                break;
            case ID_NO_TYPING:
                toggle(OtherGramGhost.KEY_NO_TYPING);
                break;
            case ID_NO_ONLINE:
                toggle(OtherGramGhost.KEY_NO_ONLINE);
                break;
            case ID_KEEP_DELETED:
                toggle(OtherGramDeletedMessages.KEY_ENABLED);
                break;
            case ID_CLEAR_DELETED:
                OtherGramDeletedMessages.clearMarks();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.OG_DeletedMarksCleared)).show();
                break;
        }
    }
}
