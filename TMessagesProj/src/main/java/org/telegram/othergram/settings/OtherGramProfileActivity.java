package org.telegram.othergram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.othergram.network.OtherGramApi;
import org.telegram.othergram.network.OtherGramProfiles;
import org.telegram.othergram.theme.OtherGramAvatars;
import org.telegram.othergram.ui.OtherGramColorPicker;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Own profile customization: banner (image from the gallery or a solid color, with vertical
 * positioning), name, description and accent colors. Published to the OtherGram backend, so other
 * OtherGram users see it; official Telegram is not affected.
 */
public class OtherGramProfileActivity extends BaseFragment {

    private static final int REQUEST_PICK_BANNER = 47101;
    private static final int BANNER_WIDTH = 1280;
    private static final int MAX_BANNER_BYTES = 1_400_000;

    private static final int ID_PICK_IMAGE = 1;
    private static final int ID_BANNER_COLOR = 2;
    private static final int ID_REMOVE_BANNER = 3;
    private static final int ID_NAME_COLOR = 4;
    private static final int ID_BIO_COLOR = 5;
    private static final int ID_ACCENT_COLOR = 6;
    private static final int ID_RESET = 7;
    private static final int ID_CONNECT = 8;
    private static final int ID_RING_STYLE = 9;
    private static final int ID_RING_COLOR = 10;
    private static final int ID_RING_COLOR2 = 11;
    private static final int ID_NAME_COLOR2 = 12;

    private static final String[] RING_STYLES = {null, "solid", "gradient", "dashed", "double"};
    private static final int[] RING_STYLE_TITLES = {R.string.OG_RingNone, R.string.OG_RingSolid, R.string.OG_RingGradient, R.string.OG_RingDashed, R.string.OG_RingDouble};

    private static final DispatchQueue imageQueue = new DispatchQueue("othergramBanner");

    private UniversalRecyclerView listView;
    private PreviewView preview;

    // Editable state, mirrors /v1/me profile
    private String bannerKind;
    private int bannerColor = 0xFFA68770;
    private float bannerOffset = 0.5f;
    private Integer nameColor, nameColor2, bioColor, accentColor;
    private String ringStyle;
    private int ringColor = 0xFFA68770;
    private int ringColor2 = 0xFFE5CFB5;
    private Bitmap bannerBitmap;
    private boolean busy;

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
        actionBar.setTitle(getString(R.string.OG_ProfileCustomization));

        preview = new PreviewView(context);

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);

        loadMe();
        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (!OtherGramApi.isLoggedIn()) {
            items.add(UItem.asButton(ID_CONNECT, R.drawable.msg_openprofile, getString(R.string.OG_ProfileNeedConnect)).accent());
            items.add(UItem.asShadow(getString(R.string.OG_ProfileNeedConnectInfo)));
            return;
        }
        items.add(UItem.asCustom(preview, 220));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.OG_Banner)));
        items.add(UItem.asButton(ID_PICK_IMAGE, R.drawable.msg_photos, getString(R.string.OG_BannerPickImage)).setEnabled(!busy));
        items.add(colorRow(ID_BANNER_COLOR, R.string.OG_BannerColor, "color".equals(bannerKind) ? bannerColor : null));
        if ("image".equals(bannerKind)) {
            items.add(UItem.asIntSlideView(1, 0, Math.round(bannerOffset * 100), 100,
                    val -> String.format(Locale.US, "%d%%", val),
                    val -> {
                        // Only the preview follows the finger; one save goes out after the slider settles.
                        // Rebuilding the list or applying server echoes here made the slider jump back.
                        bannerOffset = val / 100f;
                        offsetDirty = true;
                        preview.invalidate();
                        AndroidUtilities.cancelRunOnUIThread(saveOffsetRunnable);
                        AndroidUtilities.runOnUIThread(saveOffsetRunnable, 700);
                    }));
        }
        if (bannerKind != null) {
            items.add(UItem.asButton(ID_REMOVE_BANNER, R.drawable.msg_delete, getString(R.string.OG_BannerRemove)).red());
        }
        items.add(UItem.asShadow(getString("image".equals(bannerKind) ? R.string.OG_BannerPositionInfo : R.string.OG_BannerInfo)));

        items.add(UItem.asHeader(getString(R.string.OG_AvatarRing)));
        items.add(UItem.asButton(ID_RING_STYLE, R.drawable.msg_contacts, getString(R.string.OG_RingStyle), getString(RING_STYLE_TITLES[ringStyleIndex()])));
        if (ringStyle != null) {
            items.add(colorRow(ID_RING_COLOR, R.string.OG_RingColor, ringColor));
            if ("gradient".equals(ringStyle)) {
                items.add(colorRow(ID_RING_COLOR2, R.string.OG_RingColor2, ringColor2));
            }
        }
        items.add(UItem.asShadow(getString(R.string.OG_AvatarRingInfo)));

        items.add(UItem.asHeader(getString(R.string.OG_ProfileColors)));
        items.add(colorRow(ID_NAME_COLOR, R.string.OG_NameColor, nameColor));
        if (nameColor != null) {
            items.add(colorRow(ID_NAME_COLOR2, R.string.OG_NameColor2, nameColor2));
        }
        items.add(colorRow(ID_BIO_COLOR, R.string.OG_BioColor, bioColor));
        items.add(colorRow(ID_ACCENT_COLOR, R.string.OG_ProfileAccent, accentColor));
        items.add(UItem.asShadow(getString(R.string.OG_ProfileColorsInfo)));

        items.add(UItem.asButton(ID_RESET, R.drawable.msg_reset, getString(R.string.OG_ProfileReset)).red());
        items.add(UItem.asShadow(null));
    }

    private UItem colorRow(int id, int titleRes, Integer color) {
        final UItem item = UItem.asButton(id, Theme.createCircleDrawable(dp(22), color != null ? color : Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourceProvider)), getString(titleRes));
        item.textValue = color != null ? OtherGramColorPicker.toHex(color) : getString(R.string.OG_Default);
        return item;
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_CONNECT:
                finishFragment();
                break;
            case ID_PICK_IMAGE:
                pickImage();
                break;
            case ID_BANNER_COLOR:
                OtherGramColorPicker.show(this, getString(R.string.OG_BannerColor), bannerColor, color -> {
                    bannerKind = "color";
                    bannerColor = color;
                    saveProfile();
                }, null);
                break;
            case ID_REMOVE_BANNER:
                OtherGramApi.sendJson("DELETE", "/v1/me/banner", null, this::applyMe);
                break;
            case ID_NAME_COLOR:
                OtherGramColorPicker.show(this, getString(R.string.OG_NameColor), nameColor != null ? nameColor : textColor(), c -> {
                    nameColor = c;
                    saveProfile();
                }, nameColor != null ? () -> { nameColor = null; nameColor2 = null; saveProfile(); } : null);
                break;
            case ID_NAME_COLOR2:
                OtherGramColorPicker.show(this, getString(R.string.OG_NameColor2), nameColor2 != null ? nameColor2 : 0xFFC2A48C, c -> {
                    nameColor2 = c;
                    saveProfile();
                }, nameColor2 != null ? () -> { nameColor2 = null; saveProfile(); } : null);
                break;
            case ID_BIO_COLOR:
                OtherGramColorPicker.show(this, getString(R.string.OG_BioColor), bioColor != null ? bioColor : textColor(), c -> {
                    bioColor = c;
                    saveProfile();
                }, bioColor != null ? () -> { bioColor = null; saveProfile(); } : null);
                break;
            case ID_ACCENT_COLOR:
                OtherGramColorPicker.show(this, getString(R.string.OG_ProfileAccent), accentColor != null ? accentColor : 0xFFA68770, c -> {
                    accentColor = c;
                    saveProfile();
                }, accentColor != null ? () -> { accentColor = null; saveProfile(); } : null);
                break;
            case ID_RING_STYLE: {
                final CharSequence[] names = new CharSequence[RING_STYLES.length];
                for (int i = 0; i < names.length; i++) {
                    names[i] = getString(RING_STYLE_TITLES[i]);
                }
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), resourceProvider)
                        .setTitle(getString(R.string.OG_RingStyle))
                        .setItems(names, (dialog, which) -> {
                            ringStyle = RING_STYLES[which];
                            saveProfile();
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .show();
                break;
            }
            case ID_RING_COLOR:
                OtherGramColorPicker.show(this, getString(R.string.OG_RingColor), ringColor, c -> {
                    ringColor = c;
                    saveProfile();
                }, null);
                break;
            case ID_RING_COLOR2:
                OtherGramColorPicker.show(this, getString(R.string.OG_RingColor2), ringColor2, c -> {
                    ringColor2 = c;
                    saveProfile();
                }, null);
                break;
            case ID_RESET:
                bannerKind = null;
                ringStyle = null;
                nameColor = nameColor2 = bioColor = accentColor = null;
                OtherGramApi.sendJson("DELETE", "/v1/me/banner", null, (s, r) -> saveProfile());
                break;
        }
    }

    private int ringStyleIndex() {
        for (int i = 1; i < RING_STYLES.length; i++) {
            if (RING_STYLES[i].equals(ringStyle)) {
                return i;
            }
        }
        return 0;
    }

    private int textColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
    }

    // ------------------------------------------------------------ server

    private void loadMe() {
        if (!OtherGramApi.isLoggedIn()) {
            return;
        }
        OtherGramApi.getJson("/v1/me", this::applyMe);
    }

    private void applyMe(int status, JSONObject me) {
        if (status < 200 || status >= 300 || me == null) {
            showError(status, me);
            return;
        }
        OtherGramProfiles.applyMe(me);
        final JSONObject profile = me.optJSONObject("profile");
        final JSONObject banner = profile != null ? profile.optJSONObject("banner") : null;
        bannerKind = banner != null ? banner.optString("kind", null) : null;
        if ("color".equals(bannerKind)) {
            bannerColor = banner.optInt("color", bannerColor);
        } else if ("image".equals(bannerKind)) {
            bannerOffset = (float) banner.optDouble("offset", 0.5);
            if (bannerBitmap == null) {
                loadBannerBitmap(OtherGramApi.BASE_URL + banner.optString("url"));
            }
        } else {
            bannerBitmap = null;
        }
        nameColor = profile != null && !profile.isNull("name_color") ? profile.optInt("name_color") : null;
        nameColor2 = profile != null && !profile.isNull("name_color2") ? profile.optInt("name_color2") : null;
        bioColor = profile != null && !profile.isNull("bio_color") ? profile.optInt("bio_color") : null;
        accentColor = profile != null && !profile.isNull("accent_color") ? profile.optInt("accent_color") : null;
        final JSONObject ring = profile != null ? profile.optJSONObject("ring") : null;
        ringStyle = ring != null ? ring.optString("style", null) : null;
        if (ring != null) {
            ringColor = ring.optInt("color", ringColor);
            ringColor2 = ring.optInt("color2", ringColor2);
        }
        refresh();
    }

    private boolean offsetDirty;
    private final Runnable saveOffsetRunnable = () -> {
        offsetDirty = false;
        saveProfile(false);
    };
    /** Increases with every save; responses of older saves are ignored so they cannot undo newer edits. */
    private int saveSeq;

    private void saveProfile() {
        saveProfile(true);
    }

    private void saveProfile(boolean refreshList) {
        final int seq = ++saveSeq;
        final JSONObject body = new JSONObject();
        try {
            body.put("banner_kind", bannerKind == null ? JSONObject.NULL : bannerKind);
            body.put("banner_color", bannerColor);
            body.put("banner_offset", (double) bannerOffset);
            body.put("name_color", nameColor == null ? JSONObject.NULL : nameColor);
            body.put("name_color2", nameColor == null || nameColor2 == null ? JSONObject.NULL : nameColor2);
            body.put("bio_color", bioColor == null ? JSONObject.NULL : bioColor);
            body.put("accent_color", accentColor == null ? JSONObject.NULL : accentColor);
            body.put("ring_style", ringStyle == null ? JSONObject.NULL : ringStyle);
            body.put("ring_color", ringColor);
            body.put("ring_color2", ringColor2);
        } catch (Exception ignore) {
        }
        if (refreshList) {
            refresh();
        }
        OtherGramApi.sendJson("PUT", "/v1/me/profile", body, (status, response) -> {
            if (seq != saveSeq) {
                return; // a newer edit is on its way
            }
            if (refreshList) {
                applyMe(status, response);
            } else if (status < 200 || status >= 300) {
                showError(status, response);
            } else {
                OtherGramProfiles.applyMe(response); // keep caches fresh without touching the slider
            }
        });
    }

    private void showError(int status, JSONObject response) {
        final String error = response != null ? response.optString("error", "-") : "-";
        BulletinFactory.of(this).createErrorBulletin(LocaleController.formatString(R.string.OG_ServerError, status, error)).show();
    }

    private void refresh() {
        if (listView != null) {
            listView.adapter.update(true);
        }
        if (preview != null) {
            preview.invalidate();
        }
    }

    // ------------------------------------------------------------ banner image

    private void pickImage() {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/png", "image/jpeg", "image/webp"});
        try {
            activity.startActivityForResult(intent, REQUEST_PICK_BANNER);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onFragmentDestroy() {
        // A pending banner position is saved right away instead of being lost
        AndroidUtilities.cancelRunOnUIThread(saveOffsetRunnable);
        if (offsetDirty && OtherGramApi.isLoggedIn()) {
            saveOffsetRunnable.run();
        }
        super.onFragmentDestroy();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK_BANNER || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        busy = true;
        refresh();
        imageQueue.postRunnable(() -> {
            // Re-encode on the device: scales the image down and drops EXIF/metadata before upload
            Bitmap scaled = null;
            byte[] jpeg = null;
            try {
                final BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;
                while (bounds.outWidth / (opts.inSampleSize * 2) >= BANNER_WIDTH) {
                    opts.inSampleSize *= 2;
                }
                Bitmap decoded;
                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    decoded = BitmapFactory.decodeStream(in, null, opts);
                }
                if (decoded != null) {
                    final float k = Math.min(1f, BANNER_WIDTH / (float) decoded.getWidth());
                    scaled = k < 1f ? Bitmap.createScaledBitmap(decoded, Math.round(decoded.getWidth() * k), Math.round(decoded.getHeight() * k), true) : decoded;
                    int quality = 88;
                    do {
                        final ByteArrayOutputStream out = new ByteArrayOutputStream();
                        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out);
                        jpeg = out.toByteArray();
                        quality -= 8;
                    } while (jpeg.length > MAX_BANNER_BYTES && quality > 40);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final Bitmap finalBitmap = scaled;
            final byte[] finalJpeg = jpeg;
            AndroidUtilities.runOnUIThread(() -> {
                if (finalJpeg == null || finalJpeg.length > MAX_BANNER_BYTES) {
                    busy = false;
                    refresh();
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.OG_BannerFailed)).show();
                    return;
                }
                bannerBitmap = finalBitmap;
                bannerKind = "image";
                bannerOffset = 0.5f;
                refresh();
                OtherGramApi.sendBytes("PUT", "/v1/me/banner", finalJpeg, "image/jpeg", (status, response) -> {
                    busy = false;
                    applyMe(status, response);
                    if (status == 200) {
                        saveProfile();
                    }
                });
            });
        });
    }

    private void loadBannerBitmap(String url) {
        imageQueue.postRunnable(() -> {
            Bitmap bitmap = null;
            try (InputStream in = new java.net.URL(url).openStream()) {
                bitmap = BitmapFactory.decodeStream(in);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final Bitmap result = bitmap;
            AndroidUtilities.runOnUIThread(() -> {
                if (result != null && "image".equals(bannerKind)) {
                    bannerBitmap = result;
                    refresh();
                }
            });
        });
    }

    // ------------------------------------------------------------ preview

    private class PreviewView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint bioPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final android.graphics.Path shapePath = new android.graphics.Path();
        // The user's real avatar, drawn in the chosen shape; the ring is drawn from the unsaved edits below
        private final org.telegram.messenger.ImageReceiver avatarReceiver = new org.telegram.messenger.ImageReceiver(this);
        private final org.telegram.ui.Components.AvatarDrawable avatarDrawable = new org.telegram.ui.Components.AvatarDrawable();

        PreviewView(Context context) {
            super(context);
            namePaint.setTypeface(AndroidUtilities.bold());
            namePaint.setTextSize(dp(20));
            bioPaint.setTextSize(dp(14));
            final TLRPC.User self = getUserConfig().getCurrentUser();
            if (self != null) {
                avatarDrawable.setInfo(currentAccount, self);
                avatarReceiver.setForUserOrChat(self, avatarDrawable);
            }
            avatarReceiver.setOtherGramAvatar(true, 0);
            avatarReceiver.setRoundRadius(dp(36));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            avatarReceiver.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            avatarReceiver.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int w = getWidth();
            final int h = getHeight();
            final int bg = Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider);
            canvas.drawColor(bg);
            final float bannerH = h * 0.62f;

            if ("image".equals(bannerKind) && bannerBitmap != null) {
                final float scale = Math.max(w / (float) bannerBitmap.getWidth(), bannerH / bannerBitmap.getHeight());
                final float dw = bannerBitmap.getWidth() * scale;
                final float dh = bannerBitmap.getHeight() * scale;
                final float top = -(dh - bannerH) * bannerOffset;
                canvas.save();
                canvas.clipRect(0, 0, w, bannerH);
                rect.set((w - dw) / 2f, top, (w + dw) / 2f, top + dh);
                canvas.drawBitmap(bannerBitmap, null, rect, bitmapPaint);
                canvas.restore();
            } else if ("color".equals(bannerKind)) {
                paint.setColor(bannerColor);
                canvas.drawRect(0, 0, w, bannerH, paint);
            } else {
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
                canvas.drawRect(0, 0, w, bannerH, paint);
            }
            if (bannerKind != null) {
                paint.setShader(new LinearGradient(0, bannerH * 0.45f, 0, bannerH, bg & 0x00FFFFFF, bg, Shader.TileMode.CLAMP));
                canvas.drawRect(0, bannerH * 0.45f, w, bannerH, paint);
                paint.setShader(null);
            }

            // The user's own avatar in the chosen shape, with the ring being edited
            final float size = dp(72);
            final float cx = w / 2f;
            final float ay = bannerH - size * 0.55f;
            rect.set(cx - size / 2f, ay, cx + size / 2f, ay + size);
            avatarReceiver.setImageCoords(rect.left, rect.top, size, size);
            avatarReceiver.draw(canvas);
            if (ringStyle != null) {
                OtherGramAvatars.drawRing(canvas, rect, ringStyle, ringColor, ringColor2, 255);
            }

            final TLRPC.User self = getUserConfig().getCurrentUser();
            final String name = self != null ? UserObject.getUserName(self) : "OtherGram";
            final float nameW = namePaint.measureText(name);
            final float nameX = cx - nameW / 2f;
            namePaint.setColor(nameColor != null ? nameColor : textColor());
            namePaint.setShader(nameColor != null && nameColor2 != null
                    ? new LinearGradient(nameX, 0, nameX + nameW, 0, nameColor | 0xFF000000, nameColor2 | 0xFF000000, Shader.TileMode.CLAMP)
                    : null);
            canvas.drawText(name, nameX, ay + size + dp(26), namePaint);
            namePaint.setShader(null);

            final String bio = getString(R.string.OG_ProfilePreviewBio);
            bioPaint.setColor(bioColor != null ? bioColor : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            final float bioW = bioPaint.measureText(bio);
            canvas.drawText(bio, cx - bioW / 2f, ay + size + dp(46), bioPaint);
        }
    }
}
