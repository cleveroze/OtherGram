package org.telegram.othergram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.othergram.badges.OtherGramBadges;
import org.telegram.othergram.network.OtherGramApi;
import org.telegram.othergram.network.OtherGramAuth;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/**
 * Root screen of OtherGram Settings, opened from the regular Telegram settings.
 * Sections stay disabled with an "In development" value until their screens actually work.
 */
public class OtherGramSettingsActivity extends BaseFragment {

    private static final int ID_APPEARANCE = 1;
    private static final int ID_AVATARS = 2;
    private static final int ID_TYPOGRAPHY = 3;
    private static final int ID_CHATS = 4;
    private static final int ID_NAVIGATION = 5;
    private static final int ID_PROFILE = 6;
    private static final int ID_EXPORT_IMPORT = 7;
    private static final int ID_RESET = 8;
    private static final int ID_ACCOUNT = 9;
    private static final int ID_ADMIN = 10;
    private static final int ID_PRIVACY = 11;

    private UniversalRecyclerView listView;
    private boolean connecting;

    @Override
    public void onResume() {
        super.onResume();
        OtherGramApi.refreshMe(() -> {
            if (listView != null) {
                listView.adapter.update(true);
            }
        });
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
        actionBar.setTitle(getString(R.string.OG_Settings));

        FrameLayout contentView = new FrameLayout(context);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.OG_SectionAccount)));
        if (!OtherGramApi.isConfigured()) {
            items.add(UItem.asButton(ID_ACCOUNT, R.drawable.msg_openprofile, getString(R.string.OG_AccountNoServer)).setEnabled(false));
        } else if (connecting) {
            items.add(UItem.asButton(ID_ACCOUNT, R.drawable.msg_openprofile, getString(R.string.OG_AccountConnecting)).setEnabled(false));
        } else if (OtherGramApi.isLoggedIn()) {
            items.add(UItem.asButton(ID_ACCOUNT, R.drawable.msg_openprofile, getString(R.string.OG_AccountDisconnect), getString(R.string.OG_AccountConnected)));
        } else {
            items.add(UItem.asButton(ID_ACCOUNT, R.drawable.msg_openprofile, getString(R.string.OG_AccountConnect)).accent());
        }
        if (OtherGramApi.isLoggedIn() && OtherGramApi.isAdmin()) {
            items.add(UItem.asButton(ID_ADMIN, R.drawable.msg_admins, getString(R.string.OG_AdminPanel)));
        }
        items.add(UItem.asShadow(getString(R.string.OG_AccountInfo)));

        items.add(UItem.asHeader(getString(R.string.OG_SectionCustomization)));
        items.add(UItem.asButton(ID_APPEARANCE, R.drawable.msg_palette, getString(R.string.OG_Appearance)));
        items.add(UItem.asButton(ID_AVATARS, R.drawable.msg_contacts, getString(R.string.OG_Avatars)));
        items.add(UItem.asButton(ID_TYPOGRAPHY, R.drawable.msg_text_outlined, getString(R.string.OG_Typography)));
        items.add(UItem.asButton(ID_CHATS, R.drawable.msg_msgbubble3, getString(R.string.OG_Chats)));
        items.add(section(ID_NAVIGATION, R.drawable.msg_list, R.string.OG_Navigation));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.OG_Privacy)));
        items.add(UItem.asButton(ID_PRIVACY, R.drawable.msg_secret, getString(R.string.OG_PrivacyEntry)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.OG_SectionProfile)));
        items.add(UItem.asButton(ID_PROFILE, R.drawable.msg_openprofile, getString(R.string.OG_ProfileCustomization)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.OG_SectionData)));
        items.add(section(ID_EXPORT_IMPORT, R.drawable.msg_share, R.string.OG_ExportImport));
        items.add(section(ID_RESET, R.drawable.msg_reset, R.string.OG_ResetAll));
        items.add(UItem.asShadow(getString(R.string.OG_InDevelopmentInfo)));
    }

    private static UItem section(int id, int iconResId, int titleResId) {
        return UItem.asButton(id, iconResId, getString(titleResId), getString(R.string.OG_InDevelopment)).setEnabled(false);
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ACCOUNT) {
            if (OtherGramApi.isLoggedIn()) {
                OtherGramApi.logout();
                listView.adapter.update(true);
            } else {
                connecting = true;
                listView.adapter.update(true);
                OtherGramAuth.login(currentAccount, (success, error) -> {
                    connecting = false;
                    if (listView != null) {
                        listView.adapter.update(true);
                    }
                    if (success) {
                        OtherGramBadges.invalidate(getUserConfig().getClientUserId());
                    } else {
                        BulletinFactory.of(this).createErrorBulletin(LocaleController.formatString(R.string.OG_AccountFailed, error)).show();
                    }
                });
            }
        } else if (item.id == ID_ADMIN) {
            presentFragment(new OtherGramAdminActivity());
        } else if (item.id == ID_APPEARANCE) {
            presentFragment(new OtherGramAppearanceActivity());
        } else if (item.id == ID_AVATARS) {
            presentFragment(new OtherGramAvatarsActivity());
        } else if (item.id == ID_PROFILE) {
            presentFragment(new OtherGramProfileActivity());
        } else if (item.id == ID_TYPOGRAPHY) {
            presentFragment(new OtherGramTypographyActivity());
        } else if (item.id == ID_PRIVACY) {
            presentFragment(new OtherGramPrivacyActivity());
        } else if (item.id == ID_CHATS) {
            presentFragment(new OtherGramChatsActivity());
        }
        // Customization sections open their screens here as they are implemented.
    }
}
