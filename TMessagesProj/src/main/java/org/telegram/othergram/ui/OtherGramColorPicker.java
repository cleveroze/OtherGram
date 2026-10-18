package org.telegram.othergram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Locale;

/**
 * Color choice dialog: a palette of calm swatches that fits the OtherGram style plus a HEX field
 * for any custom color, with a live preview.
 */
public class OtherGramColorPicker {

    public interface Callback {
        void onColor(int color);
    }

    private static final int[] PALETTE = {
            0xFFF1E1CC, 0xFFE5CFB5, 0xFFC2A48C, 0xFFA68770, 0xFF7E5F49, 0xFF30241C,
            0xFFFFF7ED, 0xFFEFE3D6, 0xFFD9C5B1, 0xFF8C6A52, 0xFF5E4838, 0xFF241C17,
            0xFFEEF0E6, 0xFFD4DCC5, 0xFFA9B897, 0xFF6F7F5E, 0xFF46523B, 0xFF1D211A,
            0xFFF6E9E7, 0xFFE8CFCB, 0xFFD6A1A8, 0xFFA86F78, 0xFF5C3F43, 0xFF241A1B,
            0xFFECEFF2, 0xFFCFD8E2, 0xFF9FB1C4, 0xFF5E6E80, 0xFF3C4A5A, 0xFF1A1E23,
            0xFFFFFFFF, 0xFFE0E0E0, 0xFF9E9E9E, 0xFF616161, 0xFF2B2B2B, 0xFF000000,
    };
    private static final int COLUMNS = 6;

    /** @param onReset when not null, shows a "Default" button that calls it */
    public static void show(BaseFragment fragment, CharSequence title, int current, Callback onPick, Runnable onReset) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final Theme.ResourcesProvider rp = fragment.getResourceProvider();
        final int[] selected = {current | 0xFF000000};

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), 0);

        final View preview = new View(context);
        final GradientDrawable previewBg = new GradientDrawable();
        previewBg.setCornerRadius(dp(12));
        previewBg.setStroke(1, Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack, rp), 0.15f));
        preview.setBackground(previewBg);
        layout.addView(preview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 0, 0, 12));

        final EditTextBoldCursor hex = new EditTextBoldCursor(context);
        hex.setTextSize(16);
        hex.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        hex.setHintText("#RRGGBB");
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, rp), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, rp), Theme.getColor(Theme.key_text_RedRegular, rp));
        hex.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        hex.setCursorSize(dp(20));
        hex.setCursorWidth(1.5f);

        final Runnable updatePreview = () -> previewBg.setColor(selected[0]);
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                final Integer parsed = parse(s.toString());
                if (parsed != null) {
                    selected[0] = parsed;
                    updatePreview.run();
                }
            }
        });

        for (int row = 0; row < PALETTE.length / COLUMNS; row++) {
            final LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < COLUMNS; col++) {
                final int color = PALETTE[row * COLUMNS + col];
                final View swatch = new View(context);
                final GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(color);
                bg.setStroke(1, Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack, rp), 0.12f));
                swatch.setBackground(bg);
                swatch.setOnClickListener(v -> hex.setText(toHex(color)));
                final FrameLayout cell = new FrameLayout(context);
                cell.addView(swatch, LayoutHelper.createFrame(32, 32, Gravity.CENTER));
                line.addView(cell, LayoutHelper.createLinear(0, 42, 1f));
            }
            layout.addView(line, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        layout.addView(hex, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 12, 0, 8));

        hex.setText(toHex(selected[0]));
        updatePreview.run();

        final AlertDialog.Builder builder = new AlertDialog.Builder(context, rp)
                .setTitle(title)
                .setView(layout)
                .setPositiveButton(getString(R.string.OG_Apply), (dialog, which) -> onPick.onColor(selected[0]))
                .setNegativeButton(getString(R.string.Cancel), null);
        if (onReset != null) {
            builder.setNeutralButton(getString(R.string.OG_Default), (dialog, which) -> onReset.run());
        }
        fragment.showDialog(builder.create());
    }

    public static String toHex(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }

    static Integer parse(String text) {
        String s = text.trim();
        if (!s.startsWith("#")) {
            s = "#" + s;
        }
        if (s.length() != 7) {
            return null;
        }
        try {
            return Color.parseColor(s) | 0xFF000000;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
