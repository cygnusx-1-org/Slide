package me.edgan.redditslide.Fragments;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;

/** Created by ccrama on 6/2/2015. */
public class Image extends Fragment {

    @Nullable String url;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        ViewGroup rootView =
                (ViewGroup) inflater.inflate(R.layout.submission_imagecard, container, false);

        final SubsamplingScaleImageView image = rootView.requireViewById(R.id.image);
        TextView title = rootView.requireViewById(R.id.title);
        TextView desc = rootView.requireViewById(R.id.desc);

        title.setVisibility(View.GONE);
        desc.setVisibility(View.GONE);

        // url is null when arguments were missing in onCreate; skip the load rather than asking
        // the image loader to fetch a null URI.
        if (url != null && !url.isEmpty() && getContext() != null) {
            ((Reddit) getContext().getApplicationContext())
                    .getImageLoader()
                    .loadImage(
                            url,
                            new SimpleImageLoadingListener() {

                                @Override
                                public void onLoadingComplete(
                                        @Nullable String imageUri, @Nullable View view, @Nullable Bitmap loadedImage) {
                                    if (loadedImage == null) {
                                        // A completed load with no bitmap: the loader reports an
                                        // unusable uri that way. ImageSource.bitmap throws on a
                                        // null, so there is nothing to show and nothing to hand it.
                                        return;
                                    }
                                    image.setImage(ImageSource.bitmap(loadedImage));
                                }
                            });
        }

        return rootView;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = this.getArguments();
        if (bundle != null) {
            url = bundle.getString("url");
        }
    }
}
