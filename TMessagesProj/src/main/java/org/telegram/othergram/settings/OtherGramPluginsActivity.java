package org.telegram.othergram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.othergram.OtherGramBackup;
import org.telegram.othergram.plugins.OtherGramPlugins;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Plugins tab: installed ".plugin" files (exteraGram format), importing them from the phone, and
 * switching them on or off. Using it requires agreeing that plugins may be harmful or break OtherGram.
 */
public class OtherGramPluginsActivity extends BaseFragment implements org.telegram.othergram.plugins.OtherGramPluginRuntime.Listener {

    @Override
    public boolean onFragmentCreate() {
        org.telegram.othergram.plugins.OtherGramPluginRuntime.addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        org.telegram.othergram.plugins.OtherGramPluginRuntime.removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public void onPluginsChanged() {
        reload();
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private static final int ID_INSTALL = 1;
    private static final int ID_PLUGIN_BASE = 1000;
    private static final int REQUEST_OPEN_PLUGIN = 7801;

    private UniversalRecyclerView listView;
    private List<OtherGramPlugins.Plugin> plugins = new ArrayList<>();

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
        actionBar.setTitle(getString(R.string.OG_Plugins));

        plugins = OtherGramPlugins.list();
        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections(org.telegram.messenger.AndroidUtilities.dp(12), org.telegram.messenger.AndroidUtilities.dp(24), false); // Material 3 Expressive: large rounded cards
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = contentView;
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen && !backward && !OtherGramPlugins.hasConsent()) {
            showConsent(null);
        }
    }

    /** The user must accept the risks once before installing anything. */
    private void showConsent(Runnable onAccepted) {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.OG_PluginsConsentTitle))
                .setMessage(getString(R.string.OG_PluginsConsent))
                .setPositiveButton(getString(R.string.OG_PluginsAgree), (d, w) -> {
                    OtherGramPlugins.setConsent(true);
                    if (onAccepted != null) {
                        onAccepted.run();
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), (d, w) -> {
                    if (!OtherGramPlugins.hasConsent()) {
                        finishFragment();
                    }
                })
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        showDialog(dialog);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(getString(R.string.OG_PluginsWarning)));
        items.add(UItem.asButton(ID_INSTALL, R.drawable.msg_download, getString(R.string.OG_PluginsInstall)).accent());
        items.add(UItem.asShadow(getString(R.string.OG_PluginsInstallInfo)));

        items.add(UItem.asHeader(getString(R.string.OG_PluginsInstalled)));
        if (plugins.isEmpty()) {
            items.add(UItem.asButton(-1, R.drawable.msg_bots, getString(R.string.OG_PluginsEmpty)).setEnabled(false));
        }
        for (int i = 0; i < plugins.size(); i++) {
            final OtherGramPlugins.Plugin p = plugins.get(i);
            items.add(UItem.asButton(ID_PLUGIN_BASE + i, R.drawable.msg_bots, p.name,
                    getString(!p.enabled ? R.string.OG_Off : org.telegram.othergram.plugins.OtherGramPluginRuntime.error(p.id) != null ? R.string.OG_PluginError : R.string.OG_On)));
        }
        items.add(UItem.asShadow(getString(R.string.OG_PluginsEngineInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_INSTALL) {
            if (!OtherGramPlugins.hasConsent()) {
                showConsent(this::openPicker);
            } else {
                openPicker();
            }
        } else if (item.id >= ID_PLUGIN_BASE && item.id < ID_PLUGIN_BASE + plugins.size()) {
            final OtherGramPlugins.Plugin p = plugins.get(item.id - ID_PLUGIN_BASE);
            presentFragment(new OtherGramPluginSettingsActivity(p.id, p.name));
        }
    }

    private void openPicker() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_OPEN_PLUGIN);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void showPlugin(OtherGramPlugins.Plugin p) {
        final StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(p.description)) {
            sb.append(p.description).append("\n\n");
        }
        if (!TextUtils.isEmpty(p.author)) {
            sb.append(LocaleController.formatString(R.string.OG_PluginAuthor, p.author)).append('\n');
        }
        if (!TextUtils.isEmpty(p.version)) {
            sb.append(LocaleController.formatString(R.string.OG_PluginVersion, p.version)).append('\n');
        }
        sb.append("ID: ").append(p.id);
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(p.name)
                .setMessage(sb.toString())
                .setPositiveButton(getString(p.enabled ? R.string.OG_PluginDisable : R.string.OG_PluginEnable), (d, w) -> {
                    OtherGramPlugins.setEnabled(p.id, !p.enabled);
                    reload();
                })
                .setNeutralButton(getString(R.string.Delete), (d, w) -> {
                    OtherGramPlugins.delete(p);
                    reload();
                })
                .setNegativeButton(getString(R.string.Close), null)
                .show();
    }

    private void reload() {
        plugins = OtherGramPlugins.list();
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_OPEN_PLUGIN || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            OtherGramPlugins.Plugin installed = null;
            try {
                final byte[] bytes = OtherGramBackup.readAll(ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri), 2L * 1024 * 1024);
                installed = OtherGramPlugins.install(bytes);
            } catch (Exception e) {
                FileLog.e(e);
            }
            final OtherGramPlugins.Plugin result = installed;
            AndroidUtilities.runOnUIThread(() -> {
                if (result == null) {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.OG_PluginBadFile)).show();
                } else {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.done, LocaleController.formatString(R.string.OG_PluginInstalled, result.name)).show();
                }
                reload();
            });
        });
    }
}
