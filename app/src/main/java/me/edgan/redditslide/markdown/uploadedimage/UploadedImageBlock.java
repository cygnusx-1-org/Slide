package me.edgan.redditslide.markdown.uploadedimage;

import me.edgan.redditslide.markdown.UploadedImage;
import org.commonmark.node.CustomBlock;

/**
 * A commonmark custom block that wraps a Reddit-uploaded image so the rich-text converter can emit
 * it as a top-level {@code img} document element.
 */
public class UploadedImageBlock extends CustomBlock {
    public UploadedImage uploadedImage;

    public UploadedImageBlock(UploadedImage uploadedImage) {
        this.uploadedImage = uploadedImage;
    }
}
