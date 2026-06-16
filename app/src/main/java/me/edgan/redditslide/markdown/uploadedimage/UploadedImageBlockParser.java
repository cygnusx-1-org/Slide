package me.edgan.redditslide.markdown.uploadedimage;

import androidx.annotation.Nullable;

import org.commonmark.node.Block;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.edgan.redditslide.markdown.UploadedImage;

/**
 * Recognizes a line of the form {@code ![caption](key)} where {@code key} matches a previously
 * uploaded Reddit image, and promotes it to a block-level {@link UploadedImageBlock}. This is what
 * lets an inserted image become a top-level {@code img} element in {@code richtext_json} instead of
 * an inline image (which Reddit does not render).
 */
public class UploadedImageBlockParser extends AbstractBlockParser {
    private final UploadedImageBlock uploadedImageBlock;

    UploadedImageBlockParser(UploadedImage uploadedImage) {
        this.uploadedImageBlock = new UploadedImageBlock(uploadedImage);
    }

    @Override
    public Block getBlock() {
        return uploadedImageBlock;
    }

    @Override
    public BlockContinue tryContinue(ParserState parserState) {
        return null;
    }

    public static class Factory extends AbstractBlockParserFactory {
        // Allow keys with the characters Reddit media keys use (word chars, dot, dash, slash); the
        // captured key still has to equal a known uploaded image, so a loose class is safe.
        private final Pattern pattern = Pattern.compile("!\\[(.*)]\\(([\\w./-]+)\\)");

        @Nullable private Map<String, UploadedImage> uploadedImageMap;

        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
            if (uploadedImageMap == null || uploadedImageMap.isEmpty()) {
                return BlockStart.none();
            }

            String line = state.getLine().toString();
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String id = matcher.group(2);
                UploadedImage uploadedImage = uploadedImageMap.get(id);
                if (uploadedImage != null) {
                    String caption = matcher.group(1);
                    uploadedImage.setCaption(caption);
                    return BlockStart.of(new UploadedImageBlockParser(uploadedImage));
                }
            }
            return BlockStart.none();
        }

        public void setUploadedImages(@Nullable List<UploadedImage> uploadedImages) {
            if (uploadedImages == null) {
                return;
            }

            uploadedImageMap = new HashMap<>();
            for (UploadedImage u : uploadedImages) {
                uploadedImageMap.put(u.imageUrlOrKey, u);
            }
        }
    }
}
