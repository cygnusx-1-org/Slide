package me.edgan.redditslide.Views;

import android.view.View;
import android.webkit.WebView;

import me.everything.android.ui.overscroll.adapters.IOverScrollDecoratorAdapter;
import org.jspecify.annotations.NullMarked;

/** Created by Carlos on 8/19/2016. */
@NullMarked
public class WebViewOverScrollDecoratorAdapter implements IOverScrollDecoratorAdapter {

    protected final WebView mView;

    public WebViewOverScrollDecoratorAdapter(WebView view) {
        mView = view;
    }

    @Override
    public View getView() {
        return mView;
    }

    @Override
    public boolean isInAbsoluteStart() {
        return !mView.canScrollHorizontally(-1);
    }

    @Override
    public boolean isInAbsoluteEnd() {
        return !mView.canScrollHorizontally(1);
    }
}
