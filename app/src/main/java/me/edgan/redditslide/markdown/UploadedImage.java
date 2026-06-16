package me.edgan.redditslide.markdown;

/**
 * Represents an image that has been uploaded to Reddit's media bucket and is ready to be referenced
 * inline in a comment or self-post body.
 *
 * <p>{@link #imageUrlOrKey} is the S3 object key returned by the media upload; it is the value used
 * as the {@code id} of an {@code img} element when the surrounding markdown is converted to
 * {@code richtext_json}.
 */
public class UploadedImage {
    public String imageName;
    public String imageUrlOrKey;
    private String caption = "";

    public UploadedImage(String imageName, String imageUrlOrKey) {
        this.imageName = imageName;
        this.imageUrlOrKey = imageUrlOrKey;
    }

    public String getCaption() {
        return caption == null ? "" : caption;
    }

    public void setCaption(String caption) {
        this.caption = caption == null ? "" : caption;
    }
}
