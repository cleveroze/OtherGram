package org.telegram.othergram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.othergram.badges.OtherGramBadges;
import org.telegram.othergram.network.OtherGramApi;
import org.telegram.tgnet.TLRPC;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Admin panel. Shown only when the server reported admin rights; every action is checked by
 * the server again (ADMIN_IDS), so a modified client cannot grant anything.
 */
public class OtherGramAdminActivity extends BaseFragment {

    private static final int ID_GRANT = 1;
    private static final int ID_USER_BADGES = 2;
    private static final int ID_CREATE_TYPE = 3;
    private static final int ID_HISTORY = 4;
    private static final int ID_TYPE_BASE = 1000;

    private UniversalRecyclerView listView;
    private final List<JSONObject> badgeTypes = new ArrayList<>();

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
        actionBar.setTitle(getString(R.string.OG_AdminPanel));

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);

        loadBadgeTypes();
        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.OG_AdminUsers)));
        items.add(UItem.asButton(ID_GRANT, R.drawable.msg_add, getString(R.string.OG_AdminGrant)));
        items.add(UItem.asButton(ID_USER_BADGES, R.drawable.msg_contacts, getString(R.string.OG_AdminUserBadges)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.OG_AdminBadgeTypes)));
        for (int i = 0; i < badgeTypes.size(); i++) {
            final JSONObject t = badgeTypes.get(i);
            items.add(UItem.asButton(ID_TYPE_BASE + i, t.optString("title"), t.optString("id")));
        }
        items.add(UItem.asButton(ID_CREATE_TYPE, R.drawable.msg_add, getString(R.string.OG_AdminCreateType)).accent());
        items.add(UItem.asShadow(null));

        items.add(UItem.asButton(ID_HISTORY, R.drawable.msg_log, getString(R.string.OG_AdminHistory)));
        items.add(UItem.asShadow(getString(R.string.OG_AdminInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_GRANT) {
            askUser(user -> pickBadgeType(type -> askLabel(type.optString("title"), label -> grant(user, type, label))));
        } else if (item.id == ID_USER_BADGES) {
            askUser(this::showUserBadges);
        } else if (item.id == ID_CREATE_TYPE) {
            editBadgeType(null);
        } else if (item.id == ID_HISTORY) {
            showHistory();
        } else if (item.id >= ID_TYPE_BASE && item.id - ID_TYPE_BASE < badgeTypes.size()) {
            editBadgeType(badgeTypes.get(item.id - ID_TYPE_BASE));
        }
    }

    // ------------------------------------------------------------ server calls

    private void loadBadgeTypes() {
        OtherGramApi.getJson("/v1/badge-types", (status, response) -> applyBadgeTypes(status, response));
    }

    private void applyBadgeTypes(int status, JSONObject response) {
        if (!checkOk(status, response)) {
            return;
        }
        badgeTypes.clear();
        final JSONArray array = response.optJSONArray("badge_types");
        for (int i = 0; array != null && i < array.length(); i++) {
            badgeTypes.add(array.optJSONObject(i));
        }
        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private void grant(TLRPC.User user, JSONObject type, String label) {
        final JSONObject body = new JSONObject();
        try {
            body.put("badge_id", type.optString("id"));
            body.put("label", label);
        } catch (Exception ignore) {
        }
        OtherGramApi.sendJson("POST", "/v1/admin/users/" + user.id + "/badges", body, (status, response) -> {
            if (checkOk(status, response)) {
                OtherGramBadges.invalidate(user.id);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                        LocaleController.formatString(R.string.OG_AdminGranted, type.optString("title"), UserObject.getUserName(user))).show();
            }
        });
    }

    private void showUserBadges(TLRPC.User user) {
        OtherGramApi.getJson("/v1/admin/users/" + user.id + "/badges", (status, response) -> {
            if (!checkOk(status, response)) {
                return;
            }
            final JSONArray granted = response.optJSONArray("badges");
            final int count = granted != null ? granted.length() : 0;
            if (count == 0) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.OG_AdminNoBadges)).show();
                return;
            }
            final CharSequence[] titles = new CharSequence[count];
            for (int i = 0; i < count; i++) {
                final JSONObject g = granted.optJSONObject(i);
                final String typeTitle = badgeTitle(g.optString("badge_id"));
                final String label = g.isNull("label") ? null : g.optString("label");
                titles[i] = TextUtils.isEmpty(label) || label.equals(typeTitle) ? typeTitle : label + " (" + typeTitle + ")";
            }
            new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(LocaleController.formatString(R.string.OG_AdminUserBadgesTitle, UserObject.getUserName(user)))
                    .setItems(titles, (dialog, which) -> badgeActions(user, granted.optJSONObject(which), titles[which]))
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        });
    }

    private void badgeActions(TLRPC.User user, JSONObject granted, CharSequence title) {
        final String badgeId = granted.optString("badge_id");
        final String currentLabel = granted.isNull("label") ? badgeTitle(badgeId) : granted.optString("label");
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(title)
                .setItems(new CharSequence[]{getString(R.string.OG_AdminEditLabel), getString(R.string.OG_AdminRevoke)}, (dialog, which) -> {
                    if (which == 0) {
                        askLabel(currentLabel, label -> setLabel(user, badgeId, label));
                    } else {
                        revoke(user, badgeId);
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void setLabel(TLRPC.User user, String badgeId, String label) {
        final JSONObject body = new JSONObject();
        try {
            body.put("label", label);
        } catch (Exception ignore) {
        }
        OtherGramApi.sendJson("PUT", "/v1/admin/users/" + user.id + "/badges/" + badgeId + "/label", body, (status, response) -> {
            if (checkOk(status, response)) {
                OtherGramBadges.invalidate(user.id);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.OG_AdminLabelSaved)).show();
            }
        });
    }

    private void revoke(TLRPC.User user, String badgeId) {
        OtherGramApi.sendJson("DELETE", "/v1/admin/users/" + user.id + "/badges/" + badgeId, null, (status, response) -> {
            if (checkOk(status, response)) {
                OtherGramBadges.invalidate(user.id);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.OG_AdminRevoked)).show();
            }
        });
    }

    private void saveBadgeType(String id, boolean isNew, String title, String description, int color) {
        final JSONObject body = new JSONObject();
        try {
            if (isNew) {
                body.put("id", id);
            }
            body.put("title", title);
            body.put("description", description);
            body.put("color", color);
        } catch (Exception ignore) {
        }
        OtherGramApi.sendJson(isNew ? "POST" : "PUT", isNew ? "/v1/admin/badge-types" : "/v1/admin/badge-types/" + id, body, this::applyBadgeTypes);
    }

    private void showHistory() {
        OtherGramApi.getJson("/v1/admin/log", (status, response) -> {
            if (!checkOk(status, response)) {
                return;
            }
            final JSONArray log = response.optJSONArray("log");
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; log != null && i < log.length(); i++) {
                final JSONObject e = log.optJSONObject(i);
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(LocaleController.getInstance().getFormatterStats().format(new Date(e.optLong("created_at") * 1000L)))
                        .append('\n').append(e.optString("action"))
                        .append(" · admin ").append(e.optLong("admin_id"));
                if (!e.isNull("target_user_id")) {
                    sb.append(" → ").append(e.optLong("target_user_id"));
                }
                sb.append('\n').append(e.optString("details"));
            }
            new AlertDialog.Builder(getParentActivity(), resourceProvider)
                    .setTitle(getString(R.string.OG_AdminHistory))
                    .setMessage(sb.length() > 0 ? sb : getString(R.string.OG_AdminHistoryEmpty))
                    .setPositiveButton(getString(R.string.OK), null)
                    .show();
        });
    }

    private boolean checkOk(int status, JSONObject response) {
        if (status >= 200 && status < 300 && response != null) {
            return true;
        }
        final String error = response != null ? response.optString("error") : null;
        BulletinFactory.of(this).createErrorBulletin(LocaleController.formatString(R.string.OG_ServerError, status, TextUtils.isEmpty(error) ? "-" : error)).show();
        return false;
    }

    // ------------------------------------------------------------ dialogs

    private interface UserCallback {
        void onUser(TLRPC.User user);
    }

    private interface TypeCallback {
        void onType(JSONObject type);
    }

    private interface LabelCallback {
        void onLabel(String label);
    }

    /** Caption for the badge pill; empty falls back to the badge type title on the server. */
    private void askLabel(String initial, LabelCallback callback) {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor input = createInput(context, getString(R.string.OG_AdminLabelHint), initial, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
        final FrameLayout container = new FrameLayout(context);
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP, 24, 8, 24, 8));
        new AlertDialog.Builder(context, resourceProvider)
                .setTitle(getString(R.string.OG_AdminLabel))
                .setMessage(getString(R.string.OG_AdminLabelInfo))
                .setView(container)
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> callback.onLabel(input.getText().toString().trim()))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private EditTextBoldCursor createInput(Context context, String hint, String value, int inputType) {
        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(16);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        edit.setHintText(hint);
        edit.setHeaderHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourceProvider));
        edit.setSingleLine(true);
        edit.setFocusable(true);
        edit.setInputType(inputType);
        edit.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourceProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourceProvider), Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
        edit.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        edit.setCursorSize(dp(20));
        edit.setCursorWidth(1.5f);
        if (value != null) {
            edit.setText(value);
        }
        return edit;
    }

    /** Asks for a @username, resolves it through Telegram and returns the user (ids never come from the username server-side). */
    private void askUser(UserCallback callback) {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor input = createInput(context, getString(R.string.OG_AdminUsernameHint), null, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final FrameLayout container = new FrameLayout(context);
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP, 24, 8, 24, 8));
        new AlertDialog.Builder(context, resourceProvider)
                .setTitle(getString(R.string.OG_AdminChooseUser))
                .setView(container)
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                    final String username = input.getText().toString().trim().replace("@", "");
                    if (username.isEmpty()) {
                        return;
                    }
                    getMessagesController().getUserNameResolver().resolve(username, peerId -> {
                        final TLRPC.User user = peerId != null && peerId > 0 ? getMessagesController().getUser(peerId) : null;
                        if (user == null) {
                            BulletinFactory.of(this).createErrorBulletin(getString(R.string.OG_AdminUserNotFound)).show();
                        } else {
                            callback.onUser(user);
                        }
                    });
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void pickBadgeType(TypeCallback callback) {
        if (badgeTypes.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.OG_AdminNoTypes)).show();
            return;
        }
        final CharSequence[] titles = new CharSequence[badgeTypes.size()];
        for (int i = 0; i < titles.length; i++) {
            titles[i] = badgeTypes.get(i).optString("title");
        }
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.OG_AdminChooseBadge))
                .setItems(titles, (dialog, which) -> callback.onType(badgeTypes.get(which)))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void editBadgeType(JSONObject type) {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final boolean isNew = type == null;
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        final EditTextBoldCursor id = createInput(context, getString(R.string.OG_AdminTypeId), isNew ? null : type.optString("id"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        id.setEnabled(isNew);
        final EditTextBoldCursor title = createInput(context, getString(R.string.OG_AdminTypeTitle), isNew ? null : type.optString("title"), InputType.TYPE_CLASS_TEXT);
        final EditTextBoldCursor description = createInput(context, getString(R.string.OG_AdminTypeDescription), isNew ? null : type.optString("description"), InputType.TYPE_CLASS_TEXT);
        final EditTextBoldCursor color = createInput(context, getString(R.string.OG_AdminTypeColor), isNew ? "#A68770" : String.format(Locale.US, "#%06X", type.optInt("color") & 0xFFFFFF), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        for (View v : new View[]{id, title, description, color}) {
            layout.addView(v, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 24, 6, 24, 6));
        }
        new AlertDialog.Builder(context, resourceProvider)
                .setTitle(getString(isNew ? R.string.OG_AdminCreateType : R.string.OG_AdminEditType))
                .setView(layout)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    final int parsedColor;
                    try {
                        parsedColor = Color.parseColor(color.getText().toString().trim()) | 0xFF000000;
                    } catch (IllegalArgumentException e) {
                        BulletinFactory.of(this).createErrorBulletin(getString(R.string.OG_AdminBadColor)).show();
                        return;
                    }
                    saveBadgeType(id.getText().toString().trim(), isNew, title.getText().toString().trim(), description.getText().toString().trim(), parsedColor);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private String badgeTitle(String badgeId) {
        for (JSONObject t : badgeTypes) {
            if (badgeId.equals(t.optString("id"))) {
                return t.optString("title");
            }
        }
        return badgeId;
    }
}
