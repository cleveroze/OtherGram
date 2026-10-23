package org.telegram.othergram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.othergram.OtherGramBackup;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Export and import: a full backup (to Saved Messages or a file) and sharing just the look with
 * friends. Files can be loaded here or simply tapped in any chat.
 */
public class OtherGramBackupActivity extends BaseFragment {

    private static final int ID_BACKUP_SAVED = 1;
    private static final int ID_BACKUP_FILE = 2;
    private static final int ID_STYLE_SHARE = 3;
    private static final int ID_STYLE_SAVED = 4;
    private static final int ID_IMPORT = 5;
    private static final int ID_UNDO = 6;

    private static final int REQUEST_CREATE_FILE = 7701;
    private static final int REQUEST_OPEN_FILE = 7702;

    private UniversalRecyclerView listView;
    private boolean pendingExportAppearanceOnly;

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
        actionBar.setTitle(getString(R.string.OG_ExportImport));

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.OG_BackupHeader)));
        items.add(UItem.asButton(ID_BACKUP_SAVED, R.drawable.msg_saved, getString(R.string.OG_BackupToSaved)));
        items.add(UItem.asButton(ID_BACKUP_FILE, R.drawable.msg_download, getString(R.string.OG_BackupToFile)));
        items.add(UItem.asShadow(getString(R.string.OG_BackupInfo)));

        items.add(UItem.asHeader(getString(R.string.OG_StyleHeader)));
        items.add(UItem.asButton(ID_STYLE_SHARE, R.drawable.msg_share, getString(R.string.OG_StyleShare)));
        items.add(UItem.asButton(ID_STYLE_SAVED, R.drawable.msg_saved, getString(R.string.OG_StyleToSaved)));
        items.add(UItem.asShadow(getString(R.string.OG_StyleInfo)));

        items.add(UItem.asHeader(getString(R.string.OG_ImportHeader)));
        items.add(UItem.asButton(ID_IMPORT, R.drawable.msg2_folder, getString(R.string.OG_ImportFromFile)).accent());
        if (OtherGramBackup.hasRestorePoint()) {
            items.add(UItem.asButton(ID_UNDO, R.drawable.msg_reset, getString(R.string.OG_ImportUndo)));
        }
        items.add(UItem.asShadow(getString(R.string.OG_ImportInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_BACKUP_SAVED:
                sendToSaved(false);
                break;
            case ID_STYLE_SAVED:
                sendToSaved(true);
                break;
            case ID_BACKUP_FILE:
                saveToFile(false);
                break;
            case ID_STYLE_SHARE:
                share();
                break;
            case ID_IMPORT: {
                final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(intent, REQUEST_OPEN_FILE);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                break;
            }
            case ID_UNDO:
                if (OtherGramBackup.undo()) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.OG_ImportUndone)).show();
                }
                listView.adapter.update(true);
                break;
        }
    }

    /** The file goes to Saved Messages, the Telegram cloud: it survives reinstalling OtherGram. */
    private void sendToSaved(boolean appearanceOnly) {
        try {
            final File file = OtherGramBackup.exportToCacheFile(appearanceOnly);
            final long selfId = getUserConfig().getClientUserId();
            SendMessagesHelper.prepareSendingDocument(getAccountInstance(), file.getAbsolutePath(), file.getAbsolutePath(), null,
                    getString(appearanceOnly ? R.string.OG_StyleCaption : R.string.OG_BackupCaption), "application/json", selfId,
                    null, null, null, null, null, true, 0, null, SendMessageChatArguments.EMPTY, false);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.saved_messages, getString(R.string.OG_SentToSaved)).show();
        } catch (Exception e) {
            FileLog.e(e);
            showError();
        }
    }

    private void saveToFile(boolean appearanceOnly) {
        pendingExportAppearanceOnly = appearanceOnly;
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, OtherGramBackup.fileName(appearanceOnly));
        try {
            startActivityForResult(intent, REQUEST_CREATE_FILE);
        } catch (Exception e) {
            FileLog.e(e);
            showError();
        }
    }

    /** Share the look through any app: a chat in OtherGram itself, a messenger, a cloud drive. */
    private void share() {
        try {
            final File file = OtherGramBackup.exportToCacheFile(true);
            final Uri uri = FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", file);
            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.OG_StyleCaption));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getParentActivity().startActivity(Intent.createChooser(intent, getString(R.string.OG_StyleShare)));
        } catch (Exception e) {
            FileLog.e(e);
            showError();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        if (requestCode == REQUEST_CREATE_FILE) {
            final boolean appearanceOnly = pendingExportAppearanceOnly;
            Utilities.globalQueue.postRunnable(() -> {
                boolean ok = false;
                try (OutputStream out = ApplicationLoader.applicationContext.getContentResolver().openOutputStream(uri)) {
                    if (out != null) {
                        out.write(OtherGramBackup.export(appearanceOnly));
                        ok = true;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                final boolean success = ok;
                AndroidUtilities.runOnUIThread(() -> {
                    if (success) {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.OG_SavedToFile)).show();
                    } else {
                        showError();
                    }
                });
            });
        } else if (requestCode == REQUEST_OPEN_FILE) {
            Utilities.globalQueue.postRunnable(() -> {
                byte[] bytes = null;
                try {
                    bytes = OtherGramBackup.readAll(ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri), OtherGramBackup.maxFileBytes());
                } catch (Exception e) {
                    FileLog.e(e);
                }
                final byte[] result = bytes;
                AndroidUtilities.runOnUIThread(() -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (result == null) {
                        showError();
                        return;
                    }
                    OtherGramBackup.showImportDialog(getParentActivity(), result, () -> {
                        if (listView != null) {
                            listView.adapter.update(true);
                        }
                    });
                });
            });
        }
    }

    private void showError() {
        BulletinFactory.of(this).createErrorBulletin(getString(R.string.OG_BackupError)).show();
    }
}
