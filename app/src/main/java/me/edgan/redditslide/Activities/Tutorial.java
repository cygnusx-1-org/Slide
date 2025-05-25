package me.edgan.redditslide.Activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SecretConstants;
import me.edgan.redditslide.SettingValues;
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
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.QrCodeScannerHelper;
import me.edgan.redditslide.util.MiscUtil;

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Window window = this.getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Palette.getDarkerColor(Color.parseColor("#FF5252")));
        }

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

    @Override
    public void onBackPressed() {
        final int currentItem = binding.tutorialViewPager.getCurrentItem();
        if (currentItem == POS_WELCOME) {
            // If the user is currently looking at the first step, allow the system to handle the
            // Back button. This calls finish() on this activity and pops the back stack.
        } else {
            // Otherwise, select the previous step.
            binding.tutorialViewPager.setCurrentItem(currentItem - 1);
        }
    }

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                final Window window = getActivity().getWindow();
                window.setStatusBarColor(Palette.getDarkerColor(Palette.getDefaultColor()));
            }

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
                                    personalizeBinding.header.setBackgroundColor(
                                            choosemainBinding.picker2.getColor());

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        final Window window = getActivity().getWindow();
                                        window.setStatusBarColor(
                                                Palette.getDarkerColor(
                                                        choosemainBinding.picker2.getColor()));
                                    }
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

                        new AlertDialog.Builder(getContext())
                                .setView(choosemainBinding.getRoot())
                                .show();
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
                                    final int color = accentBinding.picker3.getColor();
                                    ColorPreferences.Theme theme = null;
                                    for (final ColorPreferences.Theme type :
                                            ColorPreferences.Theme.values()) {
                                        if (ContextCompat.getColor(getActivity(), type.getColor())
                                                        == color
                                                && ((Tutorial) getActivity()).back
                                                        == type.getThemeType()) {
                                            theme = type;
                                            break;
                                        }
                                    }
                                    new ColorPreferences(getActivity()).setFontStyle(theme);
                                    finishDialogLayout();
                                });

                        new AlertDialog.Builder(getActivity())
                                .setView(accentBinding.getRoot())
                                .show();
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
                                                final String[] names =
                                                        new ColorPreferences(getActivity())
                                                                .getFontStyle()
                                                                .getTitle()
                                                                .split("_");
                                                final String name = names[names.length - 1];
                                                final String newName = name.replace("(", "");

                                                for (final ColorPreferences.Theme theme :
                                                        ColorPreferences.Theme.values()) {
                                                    if (theme.toString().contains(newName)
                                                            && theme.getThemeType()
                                                                    == pair.second) {
                                                        ((Tutorial) getActivity()).back =
                                                                theme.getThemeType();
                                                        new ColorPreferences(getActivity())
                                                                .setFontStyle(theme);
                                                        finishDialogLayout();
                                                        break;
                                                    }
                                                }
                                            });
                        }

                        new AlertDialog.Builder(getActivity())
                                .setView(themesmallBindingRoot)
                                .show();
                    });

            personalizeBinding.done.setOnClickListener(v1 -> {
                // Add a black overlay view
                View overlayView = new View(getActivity());
                overlayView.setBackgroundColor(Color.BLACK);
                overlayView.setAlpha(1.0f); // Fully opaque black

                // Add overlay to root window
                ViewGroup rootView = (ViewGroup) getActivity().getWindow().getDecorView().getRootView();
                rootView.addView(overlayView, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

                // Show client ID dialog first
                final Context contextThemeWrapper = new ContextThemeWrapper(getContext(),
                        new ColorPreferences(getContext()).getFontStyle().getBaseId());

                // Calculate padding in dp
                int paddingDp = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    16,
                    getResources().getDisplayMetrics()
                );

                // Create a vertical LinearLayout to hold the dialog contents
                LinearLayout dialogContainer = new LinearLayout(contextThemeWrapper);
                dialogContainer.setOrientation(LinearLayout.VERTICAL);
                dialogContainer.setPadding(paddingDp, paddingDp, paddingDp, 0);

                // Add the link TextView
                TextView linkText = new TextView(contextThemeWrapper);
                linkText.setText(R.string.client_id_instructions);
                linkText.setTextColor(new ColorPreferences(getContext()).getColor(""));
                linkText.setPadding(0, 0, 0, paddingDp);
                linkText.setPaintFlags(linkText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                linkText.setOnClickListener(v -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(this.getString(R.string.setup_md_url)));
                    startActivity(browserIntent);
                });
                dialogContainer.addView(linkText);

                // Declare EditText here and make it final
                final EditText input = new EditText(contextThemeWrapper);
                String savedClientId = SettingValues.prefs.getString(SettingValues.PREF_REDDIT_CLIENT_ID_OVERRIDE, "");
                input.setText(savedClientId);
                input.setHint(R.string.enter_client_id);
                input.setSingleLine(true);  // Make input single line

                // Create horizontal layout for input field and camera button
                LinearLayout inputRowLayout = new LinearLayout(contextThemeWrapper);
                inputRowLayout.setOrientation(LinearLayout.HORIZONTAL);
                inputRowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                // Create TextInputLayout for proper error handling
                final TextInputLayout inputLayout = new TextInputLayout(contextThemeWrapper); // Make final
                LinearLayout.LayoutParams inputLayoutParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                inputLayout.setLayoutParams(inputLayoutParams);
                inputLayout.setErrorIconDrawable(null); // Remove error icon
                inputLayout.setErrorEnabled(true);

                inputLayout.addView(input); // Add input to its layout first

                // Add themed QR code scan button (camera icon)
                ImageButton scanQrButton = new ImageButton(contextThemeWrapper);
                scanQrButton.setImageResource(R.drawable.ic_camera);
                // scanQrButton.setBackground(null); // REMOVED: Potential cause of visibility issue
                scanQrButton.setPadding(0,0,0,0); // Remove padding

                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                buttonParams.setMargins(paddingDp, 0, paddingDp, 0);
                scanQrButton.setLayoutParams(buttonParams);

                // Add views to horizontal layout
                inputRowLayout.addView(inputLayout);
                inputRowLayout.addView(scanQrButton);

                // Add horizontal layout to main container
                dialogContainer.addView(inputRowLayout);

                // Add bottom padding view
                View paddingViewBottom = new View(contextThemeWrapper);
                paddingViewBottom.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, paddingDp));
                dialogContainer.addView(paddingViewBottom);

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(contextThemeWrapper)
                        .setTitle(R.string.reddit_client_id_override)
                        .setView(dialogContainer)
                        .setPositiveButton(R.string.btn_ok, null)
                        .setCancelable(false);  // This prevents dismissing when clicking outside

                AlertDialog dialog = builder.create();
                dialog.show();

                // Get the positive button and initially disable it
                Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positiveButton.setEnabled(false);

                // Set click listener for Scan button
                scanQrButton.setOnClickListener(vScan -> {
                    QrCodeScannerHelper.startScan(getActivity(),
                            new QrCodeScannerHelper.EditTextUpdateCallback(input, getContext()));
                });

                // Set click listener for positive button
                positiveButton.setOnClickListener(v -> {
                    String clientId = input.getText().toString().trim();
                    String shortClientId = SecretConstants.getGoogleShortClientID(getContext());

                    // If input is 8 chars, validate against shortClientId
                    if (clientId.length() == 8 && !clientId.equals(shortClientId)) {
                        inputLayout.setError("Invalid Client ID");
                        return;  // Don't proceed
                    }

                    // If we get here, either the input is 22 chars or it matches shortClientId
                    if (clientId.equals(shortClientId)) {
                        clientId = SecretConstants.getGoogleLongClientID(getContext());
                    }

                    // Set the value in memory
                    SettingValues.redditClientIdOverride = clientId;

                    // Save to preferences
                    if (clientId.isEmpty()) {
                        SettingValues.prefs.edit()
                                .remove(SettingValues.PREF_REDDIT_CLIENT_ID_OVERRIDE)
                                .commit();
                    } else {
                        SettingValues.prefs.edit()
                                .putString(SettingValues.PREF_REDDIT_CLIENT_ID_OVERRIDE, clientId)
                                .commit();
                    }

                    // Complete tutorial and restart app
                    Reddit.colors.edit().putString("Tutorial", "S").commit();
                    Reddit.appRestart.edit().apply();
                    Reddit.forceRestart(getActivity(), false);
                    dialog.dismiss();
                });

                // Add text change listener to enable/disable OK button based on input length
                input.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        inputLayout.setError(null);
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        int length = s.toString().trim().length();
                        positiveButton.setEnabled(length == 8 || length == 22);
                    }
                });
            });

            return personalizeBinding.getRoot();
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            personalizeBinding = null;
        }

        private void finishDialogLayout() {
            final Intent intent = new Intent(getActivity(), Tutorial.class);
            intent.putExtra("page", 1);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            getActivity().overridePendingTransition(0, 0);

            getActivity().finish();
            getActivity().overridePendingTransition(0, 0);
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
