package me.edgan.redditslide.markdown;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.parser.PostProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes markdown image nodes from the text tree. In the new Reddit-style renderer, giphy
 * reactions and inline images are rendered separately as resolved image blocks from {@code
 * body_html} (see {@code MarkdownImages}); free ("snoomoji") emotes are resolved to {@code ￼}
 * placeholders before parsing. Dropping the image nodes here stops Markwon from also emitting
 * their alt text inline. See issue #179.
 */
public final class ImageDropPostProcessor implements PostProcessor {

    @Override
    public Node process(Node document) {
        final List<Image> images = new ArrayList<>();
        document.accept(
                new AbstractVisitor() {
                    @Override
                    public void visit(Image image) {
                        images.add(image);
                    }
                });
        for (Image image : images) {
            image.unlink();
        }
        return document;
    }
}
