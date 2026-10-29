package org.telegram.othergram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.R;
import org.telegram.othergram.plugins.OtherGramPluginRuntime;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

/** Settings a running plugin returns from create_settings(), shown as a normal settings list. */
public class OtherGramPluginSettingsActivity extends BaseFragment implements OtherGramPluginRuntime.Listener {

    private static final int ID_BASE = 100;
    private static final int ID_TOGGLE = 1;
    private static final int ID_ERROR = 2;
    private static final int ID_DELETE = 3;

    private final String pluginId;
    private final String title;
    private UniversalRecyclerView listView;
    private JSONArray items = new JSONArray();

    public OtherGramPluginSettingsActivity(String pluginId, String title) {
        this.pluginId = pluginId;
        this.title = title;
    }

    @Override
    public boolean onFragmentCreate() {
        OtherGramPluginRuntime.addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        OtherGramPluginRuntime.removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public void onPluginsChanged() {
        reload();
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
        actionBar.setTitle(title);
        items = OtherGramPluginRuntime.settings(pluginId);
        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections(org.telegram.messenger.AndroidUtilities.dp(12), org.telegram.messenger.AndroidUtilities.dp(24), false); // Material 3 Expressive: large rounded cards
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = contentView;
    }

    private void reload() {
        items = OtherGramPluginRuntime.settings(pluginId);
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private org.telegram.othergram.plugins.OtherGramPlugins.Plugin plugin() {
        for (org.telegram.othergram.plugins.OtherGramPlugins.Plugin p : org.telegram.othergram.plugins.OtherGramPlugins.list()) {
            if (p.id.equals(pluginId)) {
                return p;
            }
        }
        return null;
    }

    private void fillItems(ArrayList<UItem> out, UniversalAdapter adapter) {
        final org.telegram.othergram.plugins.OtherGramPlugins.Plugin p = plugin();
        if (p == null) {
            out.add(UItem.asShadow(getString(R.string.OG_PluginBadFile)));
            return;
        }
        out.add(UItem.asHeader(p.name + (p.version != null ? " " + p.version : "")));
        out.add(UItem.asCheck(ID_TOGGLE, getString(R.string.OG_PluginEnabled)).setChecked(p.enabled));
        final String error = p.enabled ? OtherGramPluginRuntime.error(pluginId) : null;
        if (error != null) {
            out.add(UItem.asButton(ID_ERROR, R.drawable.msg_report, getString(R.string.OG_PluginError)).red());
        }
        final StringBuilder about = new StringBuilder();
        if (p.description != null) about.append(p.description);
        if (p.author != null) about.append(about.length() > 0 ? "\n\n" : "").append(org.telegram.messenger.LocaleController.formatString(R.string.OG_PluginAuthor, p.author));
        final String unsupported = p.enabled ? OtherGramPluginRuntime.unsupported(pluginId) : null;
        if (unsupported != null) {
            about.append(about.length() > 0 ? "\n\n" : "").append(org.telegram.messenger.LocaleController.formatString(R.string.OG_PluginUnsupported, unsupported));
        }
        out.add(UItem.asShadow(about.length() > 0 ? about.toString() : null));
        if (p.enabled && items.length() == 0 && error == null) {
            out.add(UItem.asShadow(getString(R.string.OG_PluginNoSettings)));
        }
        fillPluginItems(out);
        out.add(UItem.asButton(ID_DELETE, R.drawable.msg_delete, getString(R.string.OG_PluginDelete)).red());
        out.add(UItem.asShadow(null));
    }

    private void fillPluginItems(ArrayList<UItem> out) {
        boolean open = false;
        for (int i = 0; i < items.length(); i++) {
            final JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            final String type = item.optString("type");
            final String text = item.optString("text");
            switch (type) {
                case "header":
                    out.add(UItem.asHeader(text));
                    open = true;
                    break;
                case "divider":
                    out.add(UItem.asShadow(text.isEmpty() ? null : text));
                    open = false;
                    break;
                case "switch":
                    out.add(UItem.asCheck(ID_BASE + i, text).setChecked(item.optBoolean("value")));
                    open = true;
                    break;
                case "input": {
                    final String value = item.optString("value");
                    out.add(UItem.asButton(ID_BASE + i, text, value.length() > 20 ? value.substring(0, 19) + "…" : value));
                    open = true;
                    break;
                }
                case "selector": {
                    final JSONArray choices = item.optJSONArray("items");
                    final int index = item.optInt("value");
                    out.add(UItem.asButton(ID_BASE + i, text, choices != null && index >= 0 && index < choices.length() ? choices.optString(index) : ""));
                    open = true;
                    break;
                }
                case "text": {
                    final UItem row = UItem.asButton(ID_BASE + i, text);
                    if (item.optBoolean("red")) row.red();
                    else if (item.optBoolean("accent")) row.accent();
                    out.add(row);
                    open = true;
                    break;
                }
                default:
                    break;
            }
        }
        if (open) {
            out.add(UItem.asShadow(null));
        }
    }

    private void onItemClick(UItem uItem, View view, int position, float x, float y) {
        final org.telegram.othergram.plugins.OtherGramPlugins.Plugin p = plugin();
        if (uItem.id == ID_TOGGLE && p != null) {
            org.telegram.othergram.plugins.OtherGramPlugins.setEnabled(p.id, !p.enabled);
            if (p.enabled) {
                OtherGramPluginRuntime.unload(p.id);
            } else {
                OtherGramPluginRuntime.load(p);
            }
            reload();
            return;
        }
        if (uItem.id == ID_ERROR) {
            new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(getString(R.string.OG_PluginError))
                    .setMessage(String.valueOf(OtherGramPluginRuntime.error(pluginId)))
                    .setPositiveButton(getString(R.string.OK), null)
                    .show();
            return;
        }
        if (uItem.id == ID_DELETE && p != null) {
            OtherGramPluginRuntime.unload(p.id);
            org.telegram.othergram.plugins.OtherGramPlugins.delete(p);
            org.telegram.othergram.plugins.OtherGramPluginBridge.clearSettings(p.id);
            finishFragment();
            return;
        }
        final int index = uItem.id - ID_BASE;
        final JSONObject item = items.optJSONObject(index);
        if (item == null) {
            return;
        }
        switch (item.optString("type")) {
            case "switch":
                act(index, String.valueOf(!item.optBoolean("value")));
                break;
            case "text":
                act(index, null);
                break;
            case "selector": {
                final JSONArray choices = item.optJSONArray("items");
                if (choices == null) return;
                final CharSequence[] names = new CharSequence[choices.length()];
                for (int i = 0; i < names.length; i++) names[i] = choices.optString(i);
                new AlertDialog.Builder(getParentActivity(), resourceProvider)
                        .setTitle(item.optString("text"))
                        .setItems(names, (d, which) -> act(index, String.valueOf(which)))
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .show();
                break;
            }
            case "input": {
                final EditTextBoldCursor input = new EditTextBoldCursor(getParentActivity());
                input.setText(item.optString("value"));
                input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
                input.setBackground(null);
                final FrameLayout container = new FrameLayout(getParentActivity());
                container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 8, 24, 8));
                new AlertDialog.Builder(getParentActivity(), resourceProvider)
                        .setTitle(item.optString("text"))
                        .setView(container)
                        .setPositiveButton(getString(R.string.Save), (d, w) -> act(index, JSONObject.quote(input.getText().toString())))
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .show();
                break;
            }
        }
    }

    private void act(int index, String valueJson) {
        final String error = OtherGramPluginRuntime.settingsAction(pluginId, index, valueJson);
        if (error != null && !error.isEmpty()) {
            final String[] lines = error.trim().split("\n");
            BulletinFactory.of(this).createErrorBulletin(lines[lines.length - 1]).show();
        }
        reload();
    }
}
