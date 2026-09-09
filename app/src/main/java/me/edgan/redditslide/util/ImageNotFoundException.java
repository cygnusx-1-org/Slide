package me.edgan.redditslide.util;

import java.io.IOException;
import org.jspecify.annotations.NullMarked;

/**
 * The image is not there, as opposed to not reachable.
 *
 * <p>Universal Image Loader reports every failure as a plain IO error, which leaves a caller unable
 * to tell "this preview no longer exists" from "the train went into a tunnel". Only the first is
 * worth remembering: a card that gives up on a URL forever because the network blinked would show a
 * placeholder until the app is restarted.
 */
@NullMarked
public class ImageNotFoundException extends IOException {

    public ImageNotFoundException(String message) {
        super(message);
    }
}
