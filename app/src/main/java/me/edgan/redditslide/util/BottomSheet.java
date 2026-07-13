package me.edgan.redditslide.util;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import me.edgan.redditslide.R;

/**
 * Drop-in replacement for the deprecated {@code com.cocosw:bottomsheet} library, rendered with the
 * Material {@link BottomSheetDialog} that already ships with the app. Mirrors the slice of the
 * cocosw {@code Builder} API the codebase used: {@link Builder#title}, {@link Builder#sheet},
 * {@link Builder#grid()}, {@link Builder#listener} and {@link Builder#show()}.
 *
 * <p>As in cocosw, the click listener receives the selected item's id through
 * {@code onClick(dialog, which)}. Colors follow the host theme's {@code card_background},
 * {@code fontColor} and {@code tintColor} attributes so the sheet matches every Slide theme.
 */
public class BottomSheet {

    private BottomSheet() {}

    private static final int GRID_COLUMNS = 3;

    private static class Item {
        final int id;
        final Drawable icon;
        final CharSequence title;

        Item(int id, Drawable icon, CharSequence title) {
            this.id = id;
            this.icon = icon;
            this.title = title;
        }
    }

    public static class Builder {
        private final Context context;
        private CharSequence title;
        private boolean grid;
        private DialogInterface.OnClickListener listener;
        private final List<Item> items = new ArrayList<>();

        public Builder(Context context) {
            this.context = context;
        }

        public Builder title(CharSequence title) {
            this.title = title;
            return this;
        }

        public Builder title(int titleRes) {
            this.title = context.getString(titleRes);
            return this;
        }

        public Builder sheet(int id, Drawable icon, CharSequence title) {
            items.add(new Item(id, icon, title));
            return this;
        }

        public Builder sheet(int id, int iconRes, CharSequence title) {
            return sheet(
                    id,
                    ResourcesCompat.getDrawable(context.getResources(), iconRes, context.getTheme()),
                    title);
        }

        /** Inflate a menu resource, adding each visible item as a sheet entry (id/icon/title). */
        public Builder sheet(int menuRes) {
            PopupMenu popup = new PopupMenu(context, new View(context));
            popup.getMenuInflater().inflate(menuRes, popup.getMenu());
            Menu menu = popup.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                MenuItem mi = menu.getItem(i);
                if (mi.isVisible()) {
                    items.add(new Item(mi.getItemId(), mi.getIcon(), mi.getTitle()));
                }
            }
            return this;
        }

        public Builder grid() {
            this.grid = true;
            return this;
        }

        public Builder listener(DialogInterface.OnClickListener listener) {
            this.listener = listener;
            return this;
        }

        public BottomSheetDialog show() {
            final int fontColor = resolveColor(context, R.attr.fontColor, 0xFFFFFFFF);
            final int tintColor = resolveColor(context, R.attr.tintColor, fontColor);
            final int background =
                    resolveColor(
                            context,
                            R.attr.card_background,
                            resolveColor(context, R.attr.activity_background, 0xFF424242));

            final BottomSheetDialog dialog =
                    new BottomSheetDialog(context, R.style.Slide_BottomSheetDialog);

            final LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(background);
            root.setPadding(0, DisplayUtil.dpToPxVertical(8), 0, DisplayUtil.dpToPxVertical(8));

            if (title != null) {
                final TextView tv = new TextView(context);
                tv.setText(title);
                tv.setTextColor(fontColor);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                tv.setMaxLines(2);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                final int padH = DisplayUtil.dpToPxHorizontal(16);
                tv.setPadding(
                        padH,
                        DisplayUtil.dpToPxVertical(12),
                        padH,
                        DisplayUtil.dpToPxVertical(12));
                root.addView(tv);
            }

            final View itemsView =
                    grid
                            ? buildGrid(dialog, fontColor, tintColor)
                            : buildList(dialog, fontColor, tintColor);

            final NestedScrollView scroll = new NestedScrollView(context);
            scroll.addView(itemsView);
            root.addView(
                    scroll,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));

            dialog.setContentView(root);
            dialog.show();

            // Open fully so long menus don't hide their last items behind the collapsed
            // peek height; short menus are unaffected (they wrap to content height). Skip
            // the collapsed state so a downward drag dismisses instead of snapping to peek.
            final BottomSheetBehavior<?> behavior = dialog.getBehavior();
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            return dialog;
        }

        private View buildList(BottomSheetDialog dialog, int fontColor, int tintColor) {
            final LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);

            final int rowHeight = DisplayUtil.dpToPxVertical(40);
            final int padH = DisplayUtil.dpToPxHorizontal(16);
            final int iconSize = DisplayUtil.dpToPxVertical(24);
            final int iconGap = DisplayUtil.dpToPxHorizontal(28);

            for (final Item item : items) {
                final LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(rowHeight);
                row.setPadding(padH, 0, padH, 0);
                row.setClickable(true);
                row.setBackground(selectableItemBackground(context));

                final ImageView iv = new ImageView(context);
                final LinearLayout.LayoutParams ip =
                        new LinearLayout.LayoutParams(iconSize, iconSize);
                ip.rightMargin = iconGap;
                iv.setLayoutParams(ip);
                applyIcon(iv, item.icon, tintColor);
                row.addView(iv);

                final TextView tv = new TextView(context);
                tv.setText(item.title);
                tv.setTextColor(fontColor);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                tv.setMaxLines(1);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(tv);

                bindClick(row, dialog, item.id);
                list.addView(
                        row,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            return list;
        }

        private View buildGrid(BottomSheetDialog dialog, int fontColor, int tintColor) {
            final LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);

            final int cellPadV = DisplayUtil.dpToPxVertical(16);
            final int iconSize = DisplayUtil.dpToPxVertical(32);

            LinearLayout currentRow = null;
            for (int i = 0; i < items.size(); i++) {
                if (i % GRID_COLUMNS == 0) {
                    currentRow = new LinearLayout(context);
                    currentRow.setOrientation(LinearLayout.HORIZONTAL);
                    column.addView(
                            currentRow,
                            new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT));
                }

                final Item item = items.get(i);
                final LinearLayout cell = new LinearLayout(context);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setClickable(true);
                cell.setBackground(selectableItemBackground(context));
                cell.setPadding(0, cellPadV, 0, cellPadV);

                final ImageView iv = new ImageView(context);
                iv.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
                applyIcon(iv, item.icon, tintColor);
                cell.addView(iv);

                final TextView tv = new TextView(context);
                tv.setText(item.title);
                tv.setTextColor(fontColor);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tv.setGravity(Gravity.CENTER_HORIZONTAL);
                tv.setMaxLines(2);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                final LinearLayout.LayoutParams tp =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                tp.topMargin = DisplayUtil.dpToPxVertical(8);
                cell.addView(tv, tp);

                bindClick(cell, dialog, item.id);
                currentRow.addView(
                        cell,
                        new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }

            // Pad the final row with empty weighted cells so items stay column-aligned.
            final int remainder = items.size() % GRID_COLUMNS;
            if (currentRow != null && remainder != 0) {
                for (int i = remainder; i < GRID_COLUMNS; i++) {
                    currentRow.addView(
                            new View(context),
                            new LinearLayout.LayoutParams(
                                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }
            }
            return column;
        }

        private void bindClick(View view, BottomSheetDialog dialog, int id) {
            view.setOnClickListener(
                    v -> {
                        if (listener != null) {
                            listener.onClick(dialog, id);
                        }
                        dialog.dismiss();
                    });
        }

        private static void applyIcon(ImageView iv, Drawable icon, int tintColor) {
            if (icon == null) {
                iv.setVisibility(View.INVISIBLE);
                return;
            }
            final Drawable d = icon.mutate();
            DrawableCompat.setTint(d, tintColor);
            iv.setImageDrawable(d);
        }
    }

    private static int resolveColor(Context context, int attr, int fallback) {
        final TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) {
                return ContextCompat.getColor(context, tv.resourceId);
            }
            return tv.data;
        }
        return fallback;
    }

    private static Drawable selectableItemBackground(Context context) {
        final TypedValue tv = new TypedValue();
        context.getTheme()
                .resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return ResourcesCompat.getDrawable(context.getResources(), tv.resourceId, context.getTheme());
    }
}
