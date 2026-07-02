package me.edgan.redditslide.util;

import static me.edgan.redditslide.ui.settings.SettingsHandlingFragment.LinkHandlingMode;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.core.content.ContextCompat;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Locale;
import me.edgan.redditslide.Activities.Crosspost;
import me.edgan.redditslide.Activities.MakeExternal;
import me.edgan.redditslide.Activities.ReaderMode;
import me.edgan.redditslide.Activities.Website;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.SubmissionViews.PopulateBase;
import net.dean.jraw.models.Submission;
import org.apache.commons.text.StringEscapeUtils;

public class LinkUtil {

    private static CustomTabsSession mCustomTabsSession;
    private static CustomTabsClient mClient;
    private static CustomTabsServiceConnection mConnection;

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_COLOR = "color";
    public static final String ADAPTER_POSITION = "adapter_position";

    private LinkUtil() {}

    /**
     * Attempts to open the {@code url} in a custom tab. If no custom tab activity can be found,
     * falls back to opening externally
     *
     * @param url URL to open
     * @param color Color to provide to the browser UI if applicable
     * @param contextActivity The current activity
     * @param packageName The package name recommended to use for connecting to custom tabs related
     *     components.
     */
    public static void openCustomTab(
            @NonNull String url,
            int color,
            @NonNull Activity contextActivity,
            @NonNull String packageName) {
        Intent intent = new Intent(contextActivity, MakeExternal.class);
        intent.putExtra(LinkUtil.EXTRA_URL, url);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(contextActivity, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        CustomTabsIntent.Builder builder =
                new CustomTabsIntent.Builder(getSession())
                        .setDefaultColorSchemeParams(
                                new CustomTabColorSchemeParams.Builder()
                                        .setToolbarColor(color)
                                        .build())
                        .setShowTitle(true)
                        .setStartAnimations(contextActivity, R.anim.slide_up_fade_in, 0)
                        .setExitAnimations(contextActivity, 0, R.anim.slide_down_fade_out)
                        .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                        .addMenuItem(
                                contextActivity.getString(R.string.open_links_externally),
                                pendingIntent)
                        .setCloseButtonIcon(
                                DrawableUtil.drawableToBitmap(
                                        ContextCompat.getDrawable(
                                                contextActivity, R.drawable.ic_arrow_back)));
        try {
            CustomTabsIntent customTabsIntent = builder.build();

            customTabsIntent.intent.setPackage(packageName);
            customTabsIntent.launchUrl(
                    contextActivity, formatURL(StringEscapeUtils.unescapeHtml4(url)));
        } catch (ActivityNotFoundException anfe) {
            Log.w(LogUtil.getTag(), "Unknown url: " + anfe);
            openExternally(url);
        }
    }

    public static void openUrl(
            @NonNull String url,
            int color,
            @NonNull Activity contextActivity,
            @Nullable Integer adapterPosition,
            @Nullable Submission submission) {
        // Check if it's a video content type first
        ContentType.Type contentType = ContentType.getContentType(url);

        if (!(contextActivity instanceof ReaderMode)
                && contentType != ContentType.Type.VIDEO  // Skip reader mode for videos
                && ((SettingValues.readerMode && !SettingValues.readerNight)
                        || SettingValues.readerMode
                                && SettingValues.readerNight
                                && SettingValues.isNight())) {
            Intent i = new Intent(contextActivity, ReaderMode.class);
            openIntentThemed(i, url, color, contextActivity, adapterPosition, submission);
        } else if (contentType == ContentType.Type.VIDEO && (url.contains("youtube.com") || url.contains("youtu.be"))) {
            // Special handling for YouTube videos - open in YouTube app
            Intent intent = new Intent(Intent.ACTION_VIEW, formatURL(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            contextActivity.startActivity(intent);
        } else if (SettingValues.linkHandlingMode == LinkHandlingMode.EXTERNAL.getValue()) {
            openExternally(url);
        } else {
            String packageName = CustomTabsHelper.getPackageNameToUse(contextActivity);
            if (SettingValues.linkHandlingMode == LinkHandlingMode.CUSTOM_TABS.getValue()
                    && packageName != null) {
                openCustomTab(url, color, contextActivity, packageName);
            } else {
                Intent i = new Intent(contextActivity, Website.class);
                openIntentThemed(i, url, color, contextActivity, adapterPosition, submission);
            }
        }
    }

    private static void openIntentThemed(
            @NonNull Intent intent,
            @NonNull String url,
            int color,
            @NonNull Activity contextActivity,
            @Nullable Integer adapterPosition,
            @Nullable Submission submission) {
        intent.putExtra(EXTRA_URL, url);
        if (adapterPosition != null && submission != null) {
            PopulateBase.addAdaptorPosition(
                    intent, submission, adapterPosition, contextActivity);
        }
        intent.putExtra(EXTRA_COLOR, color);
        contextActivity.startActivity(intent);
    }

    /**
     * Corrects mistakes users might make when typing URLs, e.g. case sensitivity in the scheme and
     * converts to Uri
     *
     * @param url URL to correct
     * @return corrected as a Uri
     */
    public static Uri formatURL(String url) {
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        if (url.startsWith("/")) {
            url = "https://reddit.com" + url;
        }
        if (!url.contains("://") && !url.startsWith("mailto:")) {
            url = "http://" + url;
        }

        Uri uri = Uri.parse(url);
        return uri.normalizeScheme();
    }

    public static boolean tryOpenWithVideoPlugin(@NonNull String url) {
        if (Reddit.videoPlugin) {
            try {
                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                sharingIntent.setClassName(
                        Reddit.getAppContext().getString(R.string.youtube_plugin_package),
                        Reddit.getAppContext().getString(R.string.youtube_plugin_class));
                sharingIntent.putExtra("url", removeUnusedParameters(url));
                sharingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Reddit.getAppContext().startActivity(sharingIntent);
                return true;

            } catch (Exception ignored) {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Opens the {@code url} using the method the user has set in their preferences (custom tabs,
     * internal, external) falling back as needed
     *
     * @param url URL to open
     * @param color Color to provide to the browser UI if applicable
     * @param contextActivity The current activity
     */
    public static void openUrl(@NonNull String url, int color, @NonNull Activity contextActivity) {
        openUrl(url, color, contextActivity, null, null);
    }

    /**
     * Opens the {@code uri} externally or shows an application chooser if it is set to open in this
     * application
     *
     * @param url URL to open
     */
    public static void openExternally(String url) {
        if (url == null) {
            LogUtil.e("Attempted to open null URL externally");
            return;
        }

        url = StringEscapeUtils.unescapeHtml4(CompatUtil.fromHtml(url).toString());
        Uri uri = formatURL(url);

        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        overridePackage(intent);
        Reddit.getAppContext().startActivity(intent);
    }

    public static CustomTabsSession getSession() {
        if (mClient == null) {
            mCustomTabsSession = null;
        } else if (mCustomTabsSession == null) {
            mCustomTabsSession =
                    mClient.newSession(
                            new CustomTabsCallback() {
                                @Override
                                public void onNavigationEvent(int navigationEvent, Bundle extras) {
                                    Log.w(
                                            LogUtil.getTag(),
                                            "onNavigationEvent: Code = " + navigationEvent);
                                }
                            });
        }
        return mCustomTabsSession;
    }

    public static void copyUrl(String url, Context context) {
        url = StringEscapeUtils.unescapeHtml4(CompatUtil.fromHtml(url).toString());
        ClipboardUtil.copyToClipboard(context, "Link", url);
        Toast.makeText(context, R.string.submission_link_copied, Toast.LENGTH_SHORT).show();
    }

    public static void crosspost(Submission submission, Activity mContext) {
        Crosspost.toCrosspost = submission;
        mContext.startActivity(new Intent(mContext, Crosspost.class));
    }

    public static void overridePackage(Intent intent) {
        PackageManager pm = Reddit.getAppContext().getPackageManager();

        // Get default handler for the intent
        ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
        String packageName = resolveInfo != null ? resolveInfo.activityInfo.packageName : null;

        // Get default browser package
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://ccrama.me/"));
        ResolveInfo browserResolveInfo = pm.resolveActivity(browserIntent, 0);
        String browserPackageName =
                browserResolveInfo != null ? browserResolveInfo.activityInfo.packageName : null;

        // If we couldn't resolve either, return without modifying the intent
        if (packageName == null || browserPackageName == null) {
            return;
        }

        String packageToSet = packageName;

        // If the default handler is our app, use the browser instead
        if (packageName.equals(Reddit.getAppContext().getPackageName())) {
            packageToSet = browserPackageName;
        }

        // Check if user has selected a specific browser
        if (packageToSet.equals(browserPackageName)
                && SettingValues.selectedBrowser != null
                && !SettingValues.selectedBrowser.isEmpty()) {
            try {
                pm.getPackageInfo(SettingValues.selectedBrowser, PackageManager.GET_ACTIVITIES);
                packageToSet = SettingValues.selectedBrowser;
            } catch (PackageManager.NameNotFoundException ignored) {
                // Selected browser not found, stick with default browser
            }
        }

        // Only set package if it's different from the original handler
        if (!packageToSet.equals(packageName)) {
            intent.setPackage(packageToSet);
        }
    }

    public static String removeUnusedParameters(String url) {
        String returnUrl = url;
        try {
            String[] urlParts = url.split("\\?");
            if (urlParts.length > 1) {
                String[] paramArray = urlParts[1].split("&");
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(urlParts[0]);
                for (int i = 0; i < paramArray.length; i++) {
                    String[] paramPairArray = paramArray[i].split("=");
                    if (paramPairArray.length > 1) {
                        if (i == 0) {
                            stringBuilder.append("?");
                        } else {
                            stringBuilder.append("&");
                        }
                        stringBuilder.append(URLDecoder.decode(paramPairArray[0], "UTF-8"));
                        stringBuilder.append("=");
                        stringBuilder.append(URLDecoder.decode(paramPairArray[1], "UTF-8"));
                    }
                }
                returnUrl = stringBuilder.toString();
            }
            return returnUrl;
        } catch (UnsupportedEncodingException ignored) {
            return returnUrl;
        }
    }

    public static void setTextWithLinks(final String s, final SpoilerRobotoTextView text) {
        final String[] parts = s.split("\\s+");

        final StringBuilder b = new StringBuilder();
        for (final String item : parts)
            try {
                final URL url = new URL(item);
                b.append(" <a href=\"").append(url).append("\">").append(url).append("</a>");
            } catch (MalformedURLException e) {
                b.append(" ").append(item);
            }
        text.setTextHtml(b.toString(), "no sub");
    }

    public static void launchMarketUri(final Context context, final @StringRes int resId) {
        try {
            launchMarketUriIntent(context, "market://details?id=", resId);
        } catch (ActivityNotFoundException anfe) {
            launchMarketUriIntent(context, "http://play.google.com/store/apps/details?id=", resId);
        }
    }

    private static void launchMarketUriIntent(
            final Context context, final String uriString, final @StringRes int resId) {
        context.startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(uriString + context.getString(resId))));
    }

    /** Bottom sheet for a raw image/gif link: open externally, share, share image, save. */
    public static void showImageLinkBottomSheet(
            final Activity activity,
            final String contentUrl,
            final boolean isGif,
            final Runnable saveAction) {
        int[] attrs = new int[] {R.attr.tintColor};
        TypedArray ta = activity.obtainStyledAttributes(attrs);
        int color = ta.getColor(0, Color.WHITE);
        Drawable external =
                BlendModeUtil.getTintedDrawable(activity, R.drawable.ic_open_in_browser, color);
        Drawable share = BlendModeUtil.getTintedDrawable(activity, R.drawable.ic_share, color);
        Drawable image = BlendModeUtil.getTintedDrawable(activity, R.drawable.ic_image, color);
        Drawable save = BlendModeUtil.getTintedDrawable(activity, R.drawable.ic_download, color);
        ta.recycle();

        BottomSheet.Builder b = new BottomSheet.Builder(activity).title(contentUrl);
        b.sheet(2, external, activity.getString(R.string.open_externally));
        b.sheet(5, share, activity.getString(R.string.submission_link_share));
        if (!isGif) b.sheet(3, image, activity.getString(R.string.share_image));
        String lcUrl = contentUrl == null ? "" : contentUrl.toLowerCase(Locale.ENGLISH);
        int q = lcUrl.indexOf('?');
        String path = q < 0 ? lcUrl : lcUrl.substring(0, q);
        boolean isVideo = path.endsWith(".mp4") || lcUrl.contains("format=mp4");
        b.sheet(
                4,
                save,
                activity.getString(
                        isVideo ? R.string.submission_save_video : R.string.submission_save_image));
        b.listener(
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case (2):
                                LinkUtil.openExternally(contentUrl);
                                break;
                            case (3):
                                ShareUtil.shareImage(contentUrl, activity);
                                break;
                            case (5):
                                Reddit.defaultShareText("", contentUrl, activity);
                                break;
                            case (4):
                                saveAction.run();
                                break;
                        }
                    }
                });

        b.show();
    }


    /**
     * Bottom sheet for a plain link: open externally, share, copy. {@code themedContext} resolves
     * the tint attr (a view's context may carry subreddit theming the activity lacks).
     */
    public static void showLinkBottomSheet(
            final Activity activity, final Context themedContext, final String url) {
        BottomSheet.Builder b = new BottomSheet.Builder(activity).title(url).grid();
        int[] attrs = new int[] {R.attr.tintColor};
        TypedArray ta = themedContext.obtainStyledAttributes(attrs);

        int color = ta.getColor(0, Color.WHITE);
        Drawable open = BlendModeUtil.getTintedDrawable(themedContext, R.drawable.ic_open_in_new, color);
        Drawable share = BlendModeUtil.getTintedDrawable(themedContext, R.drawable.ic_share, color);
        Drawable copy = BlendModeUtil.getTintedDrawable(themedContext, R.drawable.ic_content_copy, color);

        ta.recycle();

        b.sheet(R.id.open_link, open, activity.getString(R.string.open_externally));
        b.sheet(R.id.share_link, share, activity.getString(R.string.share_link));
        b.sheet(R.id.copy_link, copy, activity.getString(R.string.submission_link_copy));
        b.listener(
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which) {
                                    case R.id.open_link:
                                        LinkUtil.openExternally(url);
                                        break;
                                    case R.id.share_link:
                                        Reddit.defaultShareText("", url, activity);
                                        break;
                                    case R.id.copy_link:
                                        LinkUtil.copyUrl(url, activity);
                                        break;
                                }
                            }
                        })
                .show();
    }

}
