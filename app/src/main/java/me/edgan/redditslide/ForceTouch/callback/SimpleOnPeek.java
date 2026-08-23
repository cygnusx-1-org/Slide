package me.edgan.redditslide.ForceTouch.callback;

import org.jspecify.annotations.NullMarked;

/** Wrapper class for if you only need to implement the initialization method */
@NullMarked
public abstract class SimpleOnPeek implements OnPeek {

    @Override
    public void shown() {}

    @Override
    public void dismissed() {}
}
