package org.telegram.othergram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.othergram.theme.OtherGramFonts;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

/**
 * Typography: separate fonts for headings and names, messages and interface text, with a preview.
 * Telegram caches its text paints, so changes apply after restarting the app.
 */
public class OtherGramTypographyActivity extends BaseFragment {

    private static final int ID_HEADINGS = 1;
    private static final int ID_MESSAGES = 2;
    private static final int ID_INTERFACE = 3;
    private static final int ID_TEXT_SIZE = 4;
    private static final int ID_RESTART = 5;

    private UniversalRecyclerView listView;
    private PreviewView preview;
    private boolean changed;

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
        actionBar.setTitle(getString(R.string.OG_Typography));

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
        items.add(UItem.asCustom(preview, 150));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.OG_Fonts)));
        items.add(UItem.asButton(ID_HEADINGS, R.drawable.msg_text_outlined, getString(R.string.OG_FontHeadings), getString(OtherGramFonts.titleRes(OtherGramFonts.get(OtherGramFonts.TARGET_HEADINGS)))));
        items.add(UItem.asButton(ID_MESSAGES, R.drawable.msg_msgbubble3, getString(R.string.OG_FontMessages), getString(OtherGramFonts.titleRes(OtherGramFonts.get(OtherGramFonts.TARGET_MESSAGES)))));
        items.add(UItem.asButton(ID_INTERFACE, R.drawable.msg_list, getString(R.string.OG_FontInterface), getString(OtherGramFonts.titleRes(OtherGramFonts.get(OtherGramFonts.TARGET_INTERFACE)))));
        items.add(UItem.asShadow(getString(R.string.OG_FontsInfo)));
        items.add(UItem.asButton(ID_TEXT_SIZE, R.drawable.msg_photo_text_framed, getString(R.string.OG_TextSize)));
        items.add(UItem.asShadow(getString(R.string.OG_TextSizeInfo)));
        if (changed) {
            items.add(UItem.asButton(ID_RESTART, R.drawable.msg_retry, getString(R.string.OG_RestartNow)).accent());
            items.add(UItem.asShadow(null));
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_HEADINGS:
                pickFont(OtherGramFonts.TARGET_HEADINGS, R.string.OG_FontHeadings);
                break;
            case ID_MESSAGES:
                pickFont(OtherGramFonts.TARGET_MESSAGES, R.string.OG_FontMessages);
                break;
            case ID_INTERFACE:
                pickFont(OtherGramFonts.TARGET_INTERFACE, R.string.OG_FontInterface);
                break;
            case ID_TEXT_SIZE:
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                break;
            case ID_RESTART:
                restartApp();
                break;
        }
    }

    private void pickFont(String target, int titleRes) {
        final CharSequence[] names = new CharSequence[OtherGramFonts.FONTS.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = getString(OtherGramFonts.FONT_TITLES[i]);
        }
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(titleRes))
                .setItems(names, (dialog, which) -> {
                    OtherGramFonts.set(target, OtherGramFonts.FONTS[which]);
                    changed = true;
                    preview.invalidate();
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void restartApp() {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        final Intent launch = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        if (launch == null || launch.getComponent() == null) {
            return;
        }
        activity.startActivity(Intent.makeRestartActivityTask(launch.getComponent()));
        AndroidUtilities.runOnUIThread(() -> Runtime.getRuntime().exit(0), 200);
    }

    /** Sample heading, message and list line rendered with the chosen fonts. */
    private class PreviewView extends View {
        private final TextPaint heading = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        private final TextPaint message = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        private final TextPaint regular = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);

        PreviewView(Context context) {
            super(context);
            heading.setTextSize(dp(20));
            message.setTextSize(dp(16));
            regular.setTextSize(dp(14));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            final Typeface h = OtherGramFonts.preview(OtherGramFonts.get(OtherGramFonts.TARGET_HEADINGS), true);
            final Typeface m = OtherGramFonts.preview(OtherGramFonts.get(OtherGramFonts.TARGET_MESSAGES), false);
            final Typeface r = OtherGramFonts.preview(OtherGramFonts.get(OtherGramFonts.TARGET_INTERFACE), false);
            heading.setTypeface(h != null ? h : Typeface.DEFAULT_BOLD);
            message.setTypeface(m != null ? m : Typeface.DEFAULT);
            regular.setTypeface(r != null ? r : Typeface.DEFAULT);
            heading.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            message.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            regular.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            final float x = dp(21);
            canvas.drawText(getString(R.string.OG_FontPreviewHeading), x, dp(44), heading);
            canvas.drawText(getString(R.string.OG_FontPreviewMessage), x, dp(84), message);
            canvas.drawText(getString(R.string.OG_FontPreviewInterface), x, dp(118), regular);
        }
    }
}
