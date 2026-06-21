package me.edgan.redditslide.util;

import android.widget.EditText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import me.edgan.redditslide.markdown.UploadedImage;

/**
 * Tracks the Reddit-hosted images that have been inserted into a particular editor so the send path
 * can decide whether to submit the body as plain {@code text} or as {@code richtext_json}.
 *
 * <p>Keyed weakly by the editor so entries disappear when the view is gone. The editor toolbar
 * ({@code DoEditorActions}) records images here; the comment/post send sites read them back.
 */
public final class RedditImageUploads {
    private static final Map<EditText, List<UploadedImage>> MAP = new WeakHashMap<>();

    private RedditImageUploads() {}

    public static synchronized void add(EditText editText, UploadedImage image) {
        if (editText == null || image == null) return;
        List<UploadedImage> list = MAP.get(editText);
        if (list == null) {
            list = new ArrayList<>();
            MAP.put(editText, list);
        }
        list.add(image);
    }

    /** Returns a snapshot of the images for the editor (never null). */
    public static synchronized List<UploadedImage> get(EditText editText) {
        List<UploadedImage> list = MAP.get(editText);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /** Returns the images for the editor and clears them (used at send time). */
    public static synchronized List<UploadedImage> consume(EditText editText) {
        List<UploadedImage> list = MAP.remove(editText);
        return list == null ? new ArrayList<>() : list;
    }

    public static synchronized void clear(EditText editText) {
        MAP.remove(editText);
    }
}
