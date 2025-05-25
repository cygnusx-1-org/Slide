package me.edgan.redditslide.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.BuildConfig;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.ClipboardUtil;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.MiscUtil;

/** Created by l3d00m on 11/12/2015. */
public class SettingsAbout extends BaseActivityAnim {

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_about);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_title_about, true, true);

        View privacy = findViewById(R.id.privacy);
        View report = findViewById(R.id.report);
        View libs = findViewById(R.id.libs);
        View changelog = findViewById(R.id.changelog);
        final TextView version = (TextView) findViewById(R.id.version);

        version.setText("Slide v" + BuildConfig.VERSION_NAME);

        // Copy the latest stacktrace with a long click on the version number
        if (BuildConfig.DEBUG) {
            version.setOnLongClickListener(
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View view) {
                            SharedPreferences prefs =
                                    getSharedPreferences("STACKTRACE", Context.MODE_PRIVATE);
                            String stacktrace = prefs.getString("stacktrace", null);
                            if (stacktrace != null) {
                                ClipboardUtil.copyToClipboard(
                                        SettingsAbout.this, "Stacktrace", stacktrace);
                            }
                            prefs.edit().clear().apply();
                            return true;
                        }
                    });
        }
        version.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String versionNumber = version.getText().toString();
                        ClipboardUtil.copyToClipboard(SettingsAbout.this, "Version", versionNumber);
                        Toast.makeText(
                                        SettingsAbout.this,
                                        R.string.settings_about_version_copied_toast,
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                });

        privacy.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinkUtil.openExternally("https://cygnusx-1.org/Slide/privacy.txt");
                    }
                });

        report.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinkUtil.openExternally("https://github.com/edgan/Slide/issues");
                    }
                });
        findViewById(R.id.sub)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                OpenRedditLink.openUrl(
                                        SettingsAbout.this,
                                        "https://reddit.com/r/slidereddit",
                                        true);
                            }
                        });
        findViewById(R.id.rate)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                LinkUtil.launchMarketUri(SettingsAbout.this, R.string.app_package);
                            }
                        });
        changelog.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        LinkUtil.openExternally(
                                "https://github.com/edgan/Slide/blob/master/CHANGELOG.md");
                    }
                });

        libs.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(SettingsAbout.this, SettingsLibs.class);
                        startActivity(i);
                    }
                });
    }
}
