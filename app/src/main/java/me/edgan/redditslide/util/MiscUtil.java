package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


import me.edgan.redditslide.Adapters.ProfileCommentViewHolder;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;

/**
 * Created by TacoTheDank on 03/15/2021.
 *
 * <p>These functions wouldn't really make sense to be anywhere else, so... MiscUtil is meant to be
 * temporary; these functions will ideally eventually go into their own little places.
 */
public class MiscUtil {

    // Used in SubredditView, MainActivity, and CommentPage (ugly-af code moment)
    public static void doSubscribeButtonText(boolean currentlySubbed, TextView subscribe) {
        if (Authentication.didOnline) {
            if (currentlySubbed) {
                subscribe.setText(R.string.unsubscribe_caps);
            } else {
                subscribe.setText(R.string.subscribe_caps);
            }
        } else {
            if (currentlySubbed) {
                subscribe.setText(R.string.btn_remove_from_sublist);
            } else {
                subscribe.setText(R.string.btn_add_to_sublist);
            }
        }
    }

    private static void createAwards(
            final Context mContext,
            final int fontsize,
            final SpannableStringBuilder awarded,
            Bitmap image) {
        final float aspectRatio = (float) (1.00 * image.getWidth() / image.getHeight());
        image =
                Bitmap.createScaledBitmap(
                        image,
                        (int) Math.ceil(fontsize * aspectRatio),
                        (int) Math.ceil(fontsize),
                        true);
        awarded.setSpan(
                new ImageSpan(mContext, image, ImageSpan.ALIGN_BASELINE),
                0,
                2,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        awarded.setSpan(
                new RelativeSizeSpan(0.75f), 3, awarded.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public static void addAwards(
            final Context mContext,
            final int fontsize,
            final ProfileCommentViewHolder holder,
            final Integer awards,
            final int drawable) {
        if (awards > 0) {
            final String timesAwarded = awards == 1 ? "" : "\u200Ax" + awards;
            final SpannableStringBuilder awarded =
                    new SpannableStringBuilder("\u00A0★" + timesAwarded + "\u00A0");
            final Bitmap image = BitmapFactory.decodeResource(mContext.getResources(), drawable);
            createAwards(mContext, fontsize, awarded, image);
            ((TextView) holder.gild).append(awarded);
        }
    }

    public static void addCommentAwards(
            final Context mContext,
            final int fontsize,
            final SpannableStringBuilder titleString,
            final Integer awards,
            final Bitmap image) {
        if (awards > 0) {
            final String timesAwarded = awards == 1 ? "" : "\u200Ax" + awards;
            final SpannableStringBuilder awarded =
                    new SpannableStringBuilder("\u00A0★" + timesAwarded + "\u00A0");
            createAwards(mContext, fontsize, awarded, image);
            titleString.append(awarded);
            titleString.append(" ");
        }
    }

    public static void addSubmissionAwards(
            final Context mContext,
            final int fontsize,
            final SpannableStringBuilder titleString,
            final Integer awards,
            final int drawable) {
        if (awards > 0) {
            final String timesAwarded = awards == 1 ? "" : "\u200Ax" + awards;
            final SpannableStringBuilder awarded =
                    new SpannableStringBuilder("\u00A0★" + timesAwarded + "\u00A0");
            final Bitmap image = BitmapFactory.decodeResource(mContext.getResources(), drawable);
            createAwards(mContext, fontsize, awarded, image);
            titleString.append(" ");
            titleString.append(awarded);
        }
    }

    public static void addSearchAwards(
            final Context mContext,
            final int fontsize,
            final SpannableStringBuilder titleString,
            final Integer awards,
            final int drawable) {
        if (awards > 0) {
            final String timesAwarded = awards == 1 ? "" : "\u200Ax" + awards;
            final SpannableStringBuilder awarded =
                    new SpannableStringBuilder("\u00A0★" + timesAwarded + "\u00A0");
            final Bitmap image = BitmapFactory.decodeResource(mContext.getResources(), drawable);
            createAwards(mContext, fontsize, awarded, image);
            titleString.append(awarded);
            titleString.append(" ");
        }
    }

    public static void setupOldSwipeModeBackground(Context context, android.view.View view) {
        if (SettingValues.oldSwipeMode) {
            // Set an opaque background for the View
            android.util.TypedValue typedValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(R.attr.card_background, typedValue, true);
            view.setBackgroundColor(typedValue.data);
        }
    }

    /**
     * Adjusts button sizes for small screens (360dp or less)
     * @param rootView The root view containing the buttons (null for activity-level)
     * @param activity The activity context
     */
    public static void adjustButtonSizesForSmallScreens(View rootView, Activity activity) {
        if (activity == null) return;

        // Get the smallest screen width in dp (respects Developer Options setting)
        int smallestWidthDp = activity.getResources().getConfiguration().smallestScreenWidthDp;

        if (smallestWidthDp <= 360) {
            // Convert 48dp to pixels
            float density = activity.getResources().getDisplayMetrics().density;
            int buttonSizePx = (int) (48 * density);

            // List of button IDs to resize
            int[] buttonIds = {
                R.id.comments, R.id.more, R.id.save, R.id.speed,
                R.id.rotate, R.id.rotate_left, R.id.rotate_right, R.id.mute, R.id.hq
            };

            // Resize each button
            for (int buttonId : buttonIds) {
                View button;
                if (rootView != null) {
                    button = rootView.findViewById(buttonId);
                } else {
                    button = activity.findViewById(buttonId);
                }

                if (button != null) {
                    ViewGroup.LayoutParams params = button.getLayoutParams();
                    if (params != null) {
                        params.width = buttonSizePx;
                        params.height = buttonSizePx;
                        button.setLayoutParams(params);
                    }
                }
            }
        }
    }
}
