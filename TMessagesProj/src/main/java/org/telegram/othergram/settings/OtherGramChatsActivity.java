package org.telegram.othergram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.othergram.OtherGramConfig;
import org.telegram.othergram.chats.OtherGramBubbles;
import org.telegram.othergram.chats.OtherGramChats;
import org.telegram.othergram.network.OtherGramApi;
import org.telegram.othergram.network.OtherGramProfiles;
import org.telegram.othergram.ui.OtherGramColorPicker;
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
import java.util.Locale;

/**
 * Chats: message text size, own bubble style (published so other OtherGram users see it), send
 * animation, exact time with seconds, exact counters and a few extra chat tweaks.
 */
public class OtherGramChatsActivity extends BaseFragment {

    private static final int ID_BUBBLE_STYLE = 1;
    private static final int ID_BUBBLE_COLOR = 2;
    private static final int ID_SEND_ANIMATION = 3;
    private static final int ID_SECONDS = 4;
    private static final int ID_NO_ROUNDING = 5;
    private static final int ID_HIDE_EDITED = 6;
    private static final int ID_HIDE_SHARE = 7;

    private static final String[] BUBBLE_STYLES = {null, OtherGramBubbles.STYLE_SOLID, OtherGramBubbles.STYLE_GLASS};
    private static final int[] BUBBLE_STYLE_TITLES = {R.string.OG_BubbleTelegram, R.string.OG_BubbleSolid, R.string.OG_BubbleGlass};
    // Indexed by OtherGramChats.ANIM_* (values 0..5 in this order)
    private static final int[] ANIM_TITLES = {R.string.OG_AnimTelegram, R.string.OG_AnimSpring, R.string.OG_AnimBounce,
            R.string.OG_AnimJelly, R.string.OG_AnimSlide, R.string.OG_AnimFade};

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
        actionBar.setTitle(getString(R.string.OG_Chats));

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.OG_MessageSize)));
        items.add(UItem.asIntSlideView(1, 12, SharedConfig.fontSize, 30,
                val -> String.format(Locale.US, "%d", val),
                this::setMessageTextSize));
        items.add(UItem.asShadow(getString(R.string.OG_MessageSizeInfo)));

        items.add(UItem.asHeader(getString(R.string.OG_MyBubbles)));
        final String style = OtherGramBubbles.myStyle();
        items.add(UItem.asButton(ID_BUBBLE_STYLE, R.drawable.msg_msgbubble3, getString(R.string.OG_BubbleStyle), getString(BUBBLE_STYLE_TITLES[styleIndex(style)])));
        if (style != null) {
            final UItem color = UItem.asButton(ID_BUBBLE_COLOR, Theme.createCircleDrawable(org.telegram.messenger.AndroidUtilities.dp(22), OtherGramBubbles.myColor()), getString(R.string.OG_BubbleColor));
            color.textValue = OtherGramColorPicker.toHex(OtherGramBubbles.myColor());
            items.add(color);
            items.add(UItem.asIntSlideView(1, 20, OtherGramBubbles.myAlpha(), 100,
                    val -> String.format(Locale.US, "%d%%", val),
                    val -> {
                        OtherGramConfig.putInt(OtherGramBubbles.KEY_ALPHA, val);
                        publishBubble();
                    }));
        }
        items.add(UItem.asShadow(getString(R.string.OG_MyBubblesInfo)));

        items.add(UItem.asHeader(getString(R.string.OG_ChatTweaks)));
        items.add(UItem.asButton(ID_SEND_ANIMATION, R.drawable.msg_msgbubble3, getString(R.string.OG_SendAnimation), getString(ANIM_TITLES[OtherGramChats.sendAnimationStyle()])));
        items.add(UItem.asCheck(ID_SECONDS, getString(R.string.OG_TimeSeconds)).setChecked(OtherGramChats.showSeconds()));
        items.add(UItem.asCheck(ID_NO_ROUNDING, getString(R.string.OG_NoRounding)).setChecked(OtherGramChats.noNumberRounding()));
        items.add(UItem.asCheck(ID_HIDE_EDITED, getString(R.string.OG_HideEdited)).setChecked(OtherGramChats.hideEdited()));
        items.add(UItem.asCheck(ID_HIDE_SHARE, getString(R.string.OG_HideShare)).setChecked(OtherGramChats.hideShareButton()));
        items.add(UItem.asShadow(getString(R.string.OG_ChatTweaksInfo)));
    }

    private static int styleIndex(String style) {
        for (int i = 1; i < BUBBLE_STYLES.length; i++) {
            if (BUBBLE_STYLES[i].equals(style)) {
                return i;
            }
        }
        return 0;
    }

    private void toggle(String key) {
        OtherGramConfig.putBoolean(key, !OtherGramConfig.getBoolean(key, false));
        listView.adapter.update(true);
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_BUBBLE_STYLE: {
                final CharSequence[] names = new CharSequence[BUBBLE_STYLES.length];
                for (int i = 0; i < names.length; i++) {
                    names[i] = getString(BUBBLE_STYLE_TITLES[i]);
                }
                new AlertDialog.Builder(getParentActivity(), resourceProvider)
                        .setTitle(getString(R.string.OG_BubbleStyle))
                        .setItems(names, (dialog, which) -> {
                            if (BUBBLE_STYLES[which] == null) {
                                OtherGramConfig.remove(OtherGramBubbles.KEY_STYLE);
                            } else {
                                OtherGramConfig.putString(OtherGramBubbles.KEY_STYLE, BUBBLE_STYLES[which]);
                            }
                            listView.adapter.update(true);
                            publishBubble();
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .show();
                break;
            }
            case ID_BUBBLE_COLOR:
                OtherGramColorPicker.show(this, getString(R.string.OG_BubbleColor), OtherGramBubbles.myColor(), color -> {
                    // Refuse colors the message text would disappear on (white bubbles in a dark theme etc.)
                    if (!OtherGramBubbles.isReadable(color, Theme.getColor(Theme.key_chat_messageTextOut, resourceProvider))) {
                        showUnreadableColorAlert();
                        return;
                    }
                    OtherGramConfig.putInt(OtherGramBubbles.KEY_COLOR, color);
                    listView.adapter.update(true);
                    publishBubble();
                }, null);
                break;
            case ID_SEND_ANIMATION: {
                final CharSequence[] names = new CharSequence[ANIM_TITLES.length];
                for (int i = 0; i < names.length; i++) {
                    names[i] = getString(ANIM_TITLES[i]);
                }
                new AlertDialog.Builder(getParentActivity(), resourceProvider)
                        .setTitle(getString(R.string.OG_SendAnimation))
                        .setItems(names, (dialog, which) -> {
                            OtherGramConfig.putInt(OtherGramChats.KEY_SEND_ANIMATION_STYLE, OtherGramChats.ANIM_STYLES[which]);
                            listView.adapter.update(true);
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .show();
                break;
            }
            case ID_SECONDS:
                toggle(OtherGramChats.KEY_SECONDS);
                break;
            case ID_NO_ROUNDING:
                toggle(OtherGramChats.KEY_NO_ROUNDING);
                break;
            case ID_HIDE_EDITED:
                toggle(OtherGramChats.KEY_HIDE_EDITED);
                break;
            case ID_HIDE_SHARE:
                toggle(OtherGramChats.KEY_HIDE_SHARE);
                break;
        }
    }

    private void showUnreadableColorAlert() {
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.OG_UnreadableColorTitle))
                .setMessage(getString(Theme.isCurrentThemeDark() ? R.string.OG_UnreadableColorDark : R.string.OG_UnreadableColorLight))
                .setPositiveButton(getString(R.string.OK), null)
                .show();
    }

    /** Same steps as Telegram's own text size setting. */
    private void setMessageTextSize(int size) {
        if (size == SharedConfig.fontSize) {
            return;
        }
        SharedConfig.fontSize = size;
        SharedConfig.fontSizeIsDefault = false;
        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt("fons_size", size).apply();
        Theme.createCommonMessageResources();
    }

    /** Own bubbles change on this phone right away; with OtherGram connected the style is also published. */
    private void publishBubble() {
        if (!OtherGramApi.isLoggedIn()) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.OG_BubbleLocalOnly)).show();
            return;
        }
        final JSONObject changes = new JSONObject();
        try {
            final String style = OtherGramBubbles.myStyle();
            changes.put("bubble_style", style == null ? JSONObject.NULL : style);
            changes.put("bubble_color", OtherGramBubbles.myColor());
            changes.put("bubble_alpha", OtherGramBubbles.myAlpha());
        } catch (Exception ignore) {
        }
        OtherGramProfiles.updateMyProfile(changes, (status, me) -> {
            if (status != 200) {
                final String error = me != null ? me.optString("error", "-") : "-";
                BulletinFactory.of(this).createErrorBulletin(LocaleController.formatString(R.string.OG_ServerError, status, error)).show();
            }
        });
    }
}
