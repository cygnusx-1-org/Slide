package me.edgan.redditslide.Activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.databinding.ActivityTutorialBinding;
import me.edgan.redditslide.databinding.ChooseaccentBinding;
import me.edgan.redditslide.databinding.ChoosemainBinding;
import me.edgan.redditslide.databinding.ChoosethemesmallBinding;
import me.edgan.redditslide.databinding.FragmentPersonalizeBinding;
import me.edgan.redditslide.databinding.FragmentWelcomeBinding;
import me.edgan.redditslide.ui.settings.SettingsBackup;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.QrCodeScannerHelper;

/** Created by ccrama on 3/5/2015. */
public class Tutorial extends AppCompatActivity {
    /** The pages (wizard steps) to show in this demo. */
    private static final int POS_WELCOME = 0;

    private static final int POS_PERSONALIZE = 1;
    private static final int NUM_PAGES = 2;
    private int back;
    private ActivityTutorialBinding binding;

    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, mBackCallback);

        final Resources.Theme theme = getTheme();
        theme.applyStyle(new FontPreferences(this).getCommentFontStyle().getResId(), true);
        theme.applyStyle(new FontPreferences(this).getPostFontStyle().getResId(), true);
        theme.applyStyle(new ColorPreferences(this).getFontStyle().getBaseId(), true);

        binding = ActivityTutorialBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        // The pager adapter, which provides the pages to the view pager widget.
        binding.tutorialViewPager.setAdapter(new TutorialPagerAdapter(getSupportFragmentManager()));

        if (getIntent().hasExtra("page")) {
            binding.tutorialViewPager.setCurrentItem(1);
        }

        final Window window = this.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Palette.getDarkerColor(Color.parseColor("#FF5252")));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LogUtil.v("Checking notification permission on Android 13+");
            int permissionState = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS);
            LogUtil.v("Permission state: " + (permissionState == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));

            if (permissionState != PackageManager.PERMISSION_GRANTED) {
                LogUtil.v("Permission not granted, checking if we should show rationale");

                // Post the permission request to the main handler
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    LogUtil.v("No rationale needed, requesting permission directly");
                    ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                    );
                }, 500); // Half second delay
            }
        }
    }

    /**
     * Insets {@code view} for the system bars (navigation bar / display cutout) on every edge so
     * its content is not drawn underneath them. The view's backgrounds stay full-bleed; only the
     * inner content is inset. See the overload below for how each edge is applied.
     */
    private static void applySystemBarInsets(final View view, final View container) {
        applySystemBarInsets(view, container, true, true, true);
    }

    /**
     * Insets {@code view} for the system bars (navigation bar / display cutout) on the requested
     * edges. Horizontal insets are added to the existing padding; the bottom inset is added to the
     * bottom margin instead, so that a view with a fixed {@code layout_height} still moves clear of
     * the navigation bar rather than having its content clipped away inside an unchanged height
     * (the regression issue #294's fix caused). This requires the view's parent to support margins;
     * every caller's does. Whether the bottom inset can be carried is worked out once here, not on
     * every inset dispatch.
     *
     * <p>Pass {@code false} for an edge the view is not exposed to — a view pinned to the right of
     * its parent gains nothing from a left inset, and the extra padding would only widen its
     * touch target over empty space.
     *
     * <p>{@code container} is the fragment's parent view, used only to seed the first inset pass.
     */
    private static void applySystemBarInsets(
            final View view,
            final View container,
            final boolean applyLeft,
            final boolean applyRight,
            final boolean applyBottom) {
        final int baseLeft = view.getPaddingLeft();
        final int baseTop = view.getPaddingTop();
        final int baseRight = view.getPaddingRight();
        final int baseBottom = view.getPaddingBottom();
        // Decide once how the bottom inset can be carried. Insets are re-delivered on rotation,
        // IME and navigation-mode changes, so deciding inside the listener would repeat the work.
        final ViewGroup.LayoutParams initialParams = view.getLayoutParams();
        final boolean bottomAsMargin =
                applyBottom && initialParams instanceof ViewGroup.MarginLayoutParams;

        final int baseBottomMargin =
                bottomAsMargin ? ((ViewGroup.MarginLayoutParams) initialParams).bottomMargin : 0;

        ViewCompat.setOnApplyWindowInsetsListener(
                view,
                (v, windowInsets) -> {
                    Insets bars =
                            windowInsets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.displayCutout());

                    v.setPadding(
                            applyLeft ? baseLeft + bars.left : baseLeft,
                            baseTop,
                            applyRight ? baseRight + bars.right : baseRight,
                            baseBottom);

                    final ViewGroup.LayoutParams params = v.getLayoutParams();
                    if (bottomAsMargin && params instanceof ViewGroup.MarginLayoutParams) {
                        final ViewGroup.MarginLayoutParams marginParams =
                                (ViewGroup.MarginLayoutParams) params;
                        final int bottomMargin = baseBottomMargin + bars.bottom;
                        if (marginParams.bottomMargin != bottomMargin) {
                            marginParams.bottomMargin = bottomMargin;
                            v.setLayoutParams(marginParams);
                        }
                    }
                    return windowInsets;
                });
        applyInsetsAsEarlyAsPossible(view, container);
    }

    /**
     * Gets the insets onto {@code view} as early as possible. Calling {@link
     * ViewCompat#requestApplyInsets} on a detached view does nothing, and fragment views are still
     * detached while {@code onCreateView} builds them, so fall back to attach time.
     *
     * <p>{@code container} is the fragment's parent, which is already attached and laid out by
     * then. Dispatching its insets straight to {@code view} lets the very first frame be laid out
     * inset, rather than rendering once underneath the navigation bar and correcting a frame later.
     */
    private static void applyInsetsAsEarlyAsPossible(final View view, final View container) {
        if (view.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(view);
            return;
        }

        if (container != null) {
            final WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(container);
            if (rootInsets != null) {
                ViewCompat.dispatchApplyWindowInsets(view, rootInsets);
            }
        }

        view.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(@NonNull View v) {
                        v.removeOnAttachStateChangeListener(this);
                        ViewCompat.requestApplyInsets(v);
                    }

                    @Override
                    public void onViewDetachedFromWindow(@NonNull View v) {}
                });
    }

    // Intercepts Back to step the tutorial pager backwards rather than finishing
    private final OnBackPressedCallback mBackCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    final int currentItem = binding.tutorialViewPager.getCurrentItem();
                    if (currentItem == POS_WELCOME) {
                        // On the first step, Back exits the tutorial.
                        finish();
                    } else {
                        // Otherwise, select the previous step.
                        binding.tutorialViewPager.setCurrentItem(currentItem - 1);
                    }
                }
            };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forward camera permission results to the helper
        if (requestCode == QrCodeScannerHelper.CAMERA_PERMISSION_REQUEST_CODE) {
            QrCodeScannerHelper.handlePermissionsResult(requestCode, grantResults, this);
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            // Handle notification permission result (optional)
            LogUtil.v("Tutorial: Received notification permission result: " + (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED));
        }
    }

    public static class Welcome extends Fragment {
        private FragmentWelcomeBinding welcomeBinding;

        @Override
        public View onCreateView(
                @NonNull LayoutInflater inflater,
                final ViewGroup container,
                Bundle savedInstanceState) {
            welcomeBinding = FragmentWelcomeBinding.inflate(inflater, container, false);
            welcomeBinding.welcomeGetStarted.setOnClickListener(
                    v1 -> ((Tutorial) getActivity()).binding.tutorialViewPager.setCurrentItem(1));

            // Add click listener for restore button
            welcomeBinding.welcomeRestore.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), SettingsBackup.class);
                startActivity(intent);
                getActivity().finish();
            });

            // Keep the bottom buttons above the navigation bar under edge-to-edge (Android 15+).
            applySystemBarInsets(welcomeBinding.bottomButtons, container);

            return welcomeBinding.getRoot();
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            welcomeBinding = null;
        }
    }

    public static class Personalize extends Fragment {
        private FragmentPersonalizeBinding personalizeBinding;

        // Held so the dialog can be dismissed before the activity finishes, which would otherwise
        // leak its window, and so it cannot outlive the fragment's view.
        private AlertDialog activeDialog;

        @Override
        public View onCreateView(
                @NonNull LayoutInflater inflater,
                final ViewGroup container,
                Bundle savedInstanceState) {
            ((Tutorial) getActivity()).back =
                    new ColorPreferences(getContext()).getFontStyle().getThemeType();

            personalizeBinding = FragmentPersonalizeBinding.inflate(inflater, container, false);

            final int getFontColor =
                    getActivity()
                            .getResources()
                            .getColor(new ColorPreferences(getContext()).getFontStyle().getColor());
            BlendModeUtil.tintImageViewAsSrcAtop(
                    personalizeBinding.secondaryColorPreview, getFontColor);
            BlendModeUtil.tintImageViewAsSrcAtop(
                    personalizeBinding.primaryColorPreview, Palette.getDefaultColor());
            personalizeBinding.header.setBackgroundColor(Palette.getDefaultColor());
            final Window window = getActivity().getWindow();
            window.setStatusBarColor(Palette.getDarkerColor(Palette.getDefaultColor()));

            personalizeBinding.primaryColor.setOnClickListener(
                    v -> {
                        final ChoosemainBinding choosemainBinding =
                                ChoosemainBinding.inflate(getActivity().getLayoutInflater());

                        choosemainBinding.title.setBackgroundColor(Palette.getDefaultColor());

                        choosemainBinding.picker.setColors(
                                ColorPreferences.getBaseColors(getContext()));
                        for (final int i : choosemainBinding.picker.getColors()) {
                            for (final int i2 : ColorPreferences.getColors(getContext(), i)) {
                                if (i2 == Palette.getDefaultColor()) {
                                    choosemainBinding.picker.setSelectedColor(i);
                                    choosemainBinding.picker2.setColors(
                                            ColorPreferences.getColors(getContext(), i));
                                    choosemainBinding.picker2.setSelectedColor(i2);
                                    break;
                                }
                            }
                        }

                        choosemainBinding.picker.setOnColorChangedListener(
                                c -> {
                                    choosemainBinding.picker2.setColors(
                                            ColorPreferences.getColors(getContext(), c));
                                    choosemainBinding.picker2.setSelectedColor(c);
                                });

                        choosemainBinding.picker2.setOnColorChangedListener(
                                i -> {
                                    choosemainBinding.title.setBackgroundColor(
                                            choosemainBinding.picker2.getColor());

                                    // The dialog has its own window and can still deliver a colour
                                    // change after the fragment's view is gone.
                                    final Activity activity = getActivity();
                                    if (personalizeBinding == null || activity == null) {
                                        return;
                                    }

                                    personalizeBinding.header.setBackgroundColor(
                                            choosemainBinding.picker2.getColor());

                                    activity.getWindow()
                                            .setStatusBarColor(
                                                    Palette.getDarkerColor(
                                                            choosemainBinding.picker2.getColor()));
                                });

                        choosemainBinding.ok.setOnClickListener(
                                v13 -> {
                                    Reddit.colors
                                            .edit()
                                            .putInt(
                                                    "DEFAULTCOLOR",
                                                    choosemainBinding.picker2.getColor())
                                            .apply();
                                    finishDialogLayout();
                                });

                        showTrackedDialog(new AlertDialog.Builder(getContext())
                                .setView(choosemainBinding.getRoot())
                                );
                    });

            personalizeBinding.secondaryColor.setOnClickListener(
                    v -> {
                        final ChooseaccentBinding accentBinding =
                                ChooseaccentBinding.inflate(getActivity().getLayoutInflater());

                        accentBinding.title.setBackgroundColor(Palette.getDefaultColor());

                        final int[] arrs =
                                new int
                                        [ColorPreferences.getNumColorsFromThemeType(
                                                Constants.DEFAULT_THEME_TYPE)];
                        int i = 0;
                        for (final ColorPreferences.Theme type : ColorPreferences.Theme.values()) {
                            if (type.getThemeType()
                                    == ColorPreferences.ColorThemeOptions.AMOLED.getValue()) {
                                arrs[i] = ContextCompat.getColor(getActivity(), type.getColor());

                                i++;
                            }
                        }

                        accentBinding.picker3.setColors(arrs);
                        accentBinding.picker3.setSelectedColor(
                                new ColorPreferences(getActivity()).getColor(""));

                        accentBinding.ok.setOnClickListener(
                                v12 -> {
                                    // The dialog has its own window and can outlive the fragment.
                                    final Activity activity = getActivity();
                                    if (activity == null) {
                                        return;
                                    }

                                    final int color = accentBinding.picker3.getColor();
                                    ColorPreferences.Theme theme = null;
                                    for (final ColorPreferences.Theme type :
                                            ColorPreferences.Theme.values()) {
                                        if (ContextCompat.getColor(activity, type.getColor())
                                                        == color
                                                && ((Tutorial) activity).back
                                                        == type.getThemeType()) {
                                            theme = type;
                                            break;
                                        }
                                    }
                                    // No theme carries this colour for the current base theme;
                                    // setFontStyle(null) would throw, so keep the existing style.
                                    if (theme != null) {
                                        new ColorPreferences(activity).setFontStyle(theme);
                                    }

                                    finishDialogLayout();
                                });

                        showTrackedDialog(new AlertDialog.Builder(getActivity())
                                .setView(accentBinding.getRoot())
                                );
                    });

            personalizeBinding.baseColor.setOnClickListener(
                    v -> {
                        final ChoosethemesmallBinding themesmallBinding =
                                ChoosethemesmallBinding.inflate(getActivity().getLayoutInflater());
                        final View themesmallBindingRoot = themesmallBinding.getRoot();

                        themesmallBinding.title.setBackgroundColor(Palette.getDefaultColor());

                        for (final Pair<Integer, Integer> pair : ColorPreferences.themePairList) {
                            themesmallBindingRoot
                                    .findViewById(pair.first)
                                    .setOnClickListener(
                                            v14 -> {
                                                // The dialog has its own window and can outlive
                                                // the fragment.
                                                final Activity activity = getActivity();
                                                if (activity == null) {
                                                    return;
                                                }

                                                // Theme titles are <base>_<accent family>, so the
                                                // last segment is the accent to carry over.
                                                final String[] names =
                                                        new ColorPreferences(activity)
                                                                .getFontStyle()
                                                                .getTitle()
                                                                .split("_");
                                                final String accent = names[names.length - 1];

                                                for (final ColorPreferences.Theme theme :
                                                        ColorPreferences.Theme.values()) {
                                                    // Match the accent family as a whole name.
                                                    // "contains" would let blue pick lightblue,
                                                    // which is declared first.
                                                    if (theme.toString().endsWith("_" + accent)
                                                            && theme.getThemeType()
                                                                    == pair.second) {
                                                        ((Tutorial) activity).back =
                                                                theme.getThemeType();
                                                        new ColorPreferences(activity)
                                                                .setFontStyle(theme);
                                                        finishDialogLayout();
                                                        break;
                                                    }
                                                }
                                            });
                        }

                        showTrackedDialog(new AlertDialog.Builder(getActivity())
                                .setView(themesmallBindingRoot)
                                );
                    });

            personalizeBinding.done.setOnClickListener(v1 -> {
                // Add a black overlay view for a clean transition into the restart
                View overlayView = new View(getActivity());
                overlayView.setBackgroundColor(Color.BLACK);
                overlayView.setAlpha(1.0f); // Fully opaque black

                // Add overlay to root window
                ViewGroup rootView = (ViewGroup) getActivity().getWindow().getDecorView().getRootView();
                rootView.addView(overlayView, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

                // Complete tutorial and restart app. The app ships with a default Reddit client ID,
                // so there is no need to prompt for one here.
                Reddit.colors.edit().putString("Tutorial", "S").commit();
                Reddit.forceRestart(getActivity(), false);
            });

            // Keep the Done button above the navigation bar under edge-to-edge (Android 15+). It
            // is pinned to the right, so the left inset would only widen its touch target over the
            // list. The scroll view stops above the button, so it only needs the horizontal insets.
            applySystemBarInsets(personalizeBinding.done, container, false, true, true);
            applySystemBarInsets(personalizeBinding.personalizeScroll, container, true, true, false);

            return personalizeBinding.getRoot();
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            dismissActiveDialog();
            personalizeBinding = null;
        }

        /**
         * Shows {@code builder} and keeps hold of the dialog so it can be dismissed before the
         * activity finishes. Back or an outside tap clears the reference, so a dismissed dialog
         * never keeps the activity alive.
         */
        private void showTrackedDialog(final AlertDialog.Builder builder) {
            dismissActiveDialog();
            activeDialog = DialogUtil.showWithCardBackground(builder);
            // Only clear if this is still the current dialog: dismiss messages are posted, so a
            // stale one can arrive after the next dialog has already been shown.
            activeDialog.setOnDismissListener(
                    dialog -> {
                        if (activeDialog == dialog) {
                            activeDialog = null;
                        }
                    });
        }

        private void dismissActiveDialog() {
            if (activeDialog != null) {
                activeDialog.dismiss();
                activeDialog = null;
            }
        }

        private void finishDialogLayout() {
            // Dismiss before finishing, otherwise the dialog's window leaks with the activity.
            dismissActiveDialog();

            final Activity activity = getActivity();
            if (activity == null) {
                return;
            }

            final Intent intent = new Intent(activity, Tutorial.class);
            intent.putExtra("page", 1);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            activity.overridePendingTransition(0, 0);

            activity.finish();
            activity.overridePendingTransition(0, 0);
        }
    }

    /** A simple pager adapter that represents 5 ScreenSlidePageFragment objects, in sequence. */
    private static class TutorialPagerAdapter extends FragmentStatePagerAdapter {

        TutorialPagerAdapter(final FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                default:
                case POS_WELCOME:
                    return new Welcome();
                case POS_PERSONALIZE:
                    return new Personalize();
            }
        }

        @Override
        public int getCount() {
            return NUM_PAGES;
        }
    }
}
