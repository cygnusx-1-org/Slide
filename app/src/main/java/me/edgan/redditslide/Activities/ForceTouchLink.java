package me.edgan.redditslide.Activities;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;

import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.Views.ExoVideoView;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.MiscUtil;

/**
 * Created by ccrama on 01/29/2016.
 *
 * <p>This activity is the basis for the possible inclusion of some sort of "Force Touch" preview
 * system for comment links.
 */
public class ForceTouchLink extends BaseActivityAnim {

    @Override
    public void onCreate(Bundle savedInstance) {

        overridePendingTransition(0, 0);
        super.onCreate(savedInstance);
        applyColorTheme();
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_force_touch_content);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        findViewById(android.R.id.content)
                .setOnTouchListener(
                        new View.OnTouchListener() {
                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                if (event.getAction() == MotionEvent.ACTION_POINTER_UP) {
                                    finish();
                                }
                                return false;
                            }
                        });

        final String url = getIntent().getExtras().getString("url");

        ContentType.Type t = ContentType.getContentType(url);

        final ImageView mainImage = (ImageView) findViewById(R.id.image);
        ExoVideoView mainVideo = (ExoVideoView) findViewById(R.id.gif);
        mainVideo.setVisibility(View.GONE);
        switch (t) {
            case REDDIT:
                break;
            case IMGUR:
                break;
            case ALBUM:
                break;
            case REDDIT_GALLERY:
                break;
            case VIDEO:
                break;
            case IMAGE:
                ((Reddit) getApplication()).getImageLoader().displayImage(url, mainImage);

                break;
            case GIF:
                mainVideo.setVisibility(View.VISIBLE);
                new GifUtils.AsyncLoadGif(this, mainVideo, null, null, false, true, "")
                        .execute(url);
                break;
            case LINK:
                break;
        }
    }
}
