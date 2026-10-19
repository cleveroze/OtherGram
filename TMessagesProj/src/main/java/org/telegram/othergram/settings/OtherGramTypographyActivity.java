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
import org.telegram.messenger.ApplicationLoader;
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
    private static final int ID_IMPORT = 6;
    private static final int ID_DELETE_CUSTOM = 7;
    private static final int REQUEST_IMPORT_FONT = 47102;

    private static final org.telegram.messenger.DispatchQueue fontQueue = new org.telegram.messenger.DispatchQueue("othergramFonts");

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
        final Context ctx = ApplicationLoader.applicationContext;
        items.add(UItem.asButton(ID_HEADINGS, R.drawable.msg_text_outlined, getString(R.string.OG_FontHeadings), OtherGramFonts.title(ctx, OtherGramFonts.get(OtherGramFonts.TARGET_HEADINGS))));
        items.add(UItem.asButton(ID_MESSAGES, R.drawable.msg_msgbubble3, getString(R.string.OG_FontMessages), OtherGramFonts.title(ctx, OtherGramFonts.get(OtherGramFonts.TARGET_MESSAGES))));
        items.add(UItem.asButton(ID_INTERFACE, R.drawable.msg_list, getString(R.string.OG_FontInterface), OtherGramFonts.title(ctx, OtherGramFonts.get(OtherGramFonts.TARGET_INTERFACE))));
        items.add(UItem.asShadow(getString(R.string.OG_FontsInfo)));

        items.add(UItem.asHeader(getString(R.string.OG_MyFonts)));
        items.add(UItem.asButton(ID_IMPORT, R.drawable.msg_add, getString(R.string.OG_ImportFont)).accent());
        if (!OtherGramFonts.customFonts().isEmpty()) {
            items.add(UItem.asButton(ID_DELETE_CUSTOM, R.drawable.msg_delete, getString(R.string.OG_DeleteFont)).red());
        }
        items.add(UItem.asShadow(getString(R.string.OG_ImportFontInfo)));
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
            case ID_IMPORT:
                importFont();
                break;
            case ID_DELETE_CUSTOM:
                deleteFont();
                break;
        }
    }

    private void pickFont(String target, int titleRes) {
        final java.util.List<String> fonts = OtherGramFonts.allFonts();
        final CharSequence[] names = new CharSequence[fonts.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = OtherGramFonts.title(getParentActivity(), fonts.get(i));
        }
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(titleRes))
                .setItems(names, (dialog, which) -> {
                    OtherGramFonts.set(target, fonts.get(which));
                    changed = true;
                    preview.invalidate();
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void importFont() {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt", "application/octet-stream"});
        try {
            activity.startActivityForResult(intent, REQUEST_IMPORT_FONT);
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e(e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_IMPORT_FONT || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final android.net.Uri uri = data.getData();
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        fontQueue.postRunnable(() -> {
            final String font = OtherGramFonts.importFont(context, uri);
            AndroidUtilities.runOnUIThread(() -> {
                if (font == null) {
                    org.telegram.ui.Components.BulletinFactory.of(this).createErrorBulletin(getString(R.string.OG_ImportFontFailed)).show();
                    return;
                }
                org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                        org.telegram.messenger.LocaleController.formatString(R.string.OG_ImportFontDone, OtherGramFonts.title(context, font))).show();
                listView.adapter.update(true);
            });
        });
    }

    private void deleteFont() {
        final java.util.List<String> fonts = OtherGramFonts.customFonts();
        final CharSequence[] names = new CharSequence[fonts.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = OtherGramFonts.title(getParentActivity(), fonts.get(i));
        }
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.OG_DeleteFont))
                .setItems(names, (dialog, which) -> {
                    OtherGramFonts.deleteCustom(fonts.get(which));
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
