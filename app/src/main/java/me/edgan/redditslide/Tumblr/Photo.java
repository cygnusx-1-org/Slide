package me.edgan.redditslide.Tumblr;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"caption", "alt_sizes", "original_size"})
public class Photo {

    @JsonProperty("caption")
    private String caption;

    @JsonProperty("alt_sizes")
    private List<PhotoSize> altSizes = new ArrayList<PhotoSize>();

    @JsonProperty("original_size")
    private PhotoSize originalSize;

    @JsonIgnore private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * @return The caption
     */
    @JsonProperty("caption")
    public String getCaption() {
        return caption;
    }

    /**
     * @param caption The caption
     */
    @JsonProperty("caption")
    public void setCaption(String caption) {
        this.caption = caption;
    }

    /**
     * @return The altSizes
     */
    @JsonProperty("alt_sizes")
    public List<PhotoSize> getAltSizes() {
        return altSizes;
    }

    /**
     * @param altSizes The alt_sizes
     */
    @JsonProperty("alt_sizes")
    public void setAltSizes(List<PhotoSize> altSizes) {
        this.altSizes = altSizes;
    }

    /**
     * @return The originalSize
     */
    @JsonProperty("original_size")
    public PhotoSize getOriginalSize() {
        return originalSize;
    }

    /**
     * @param originalSize The original_size
     */
    @JsonProperty("original_size")
    public void setOriginalSize(PhotoSize originalSize) {
        this.originalSize = originalSize;
    }

    /**
     * Whether this photo has a size to render from. The field has no default, so a photo whose JSON
     * carried no original_size leaves {@link #getOriginalSize()} null and every dereference of it a
     * crash.
     */
    // @JsonIgnore here is belt-and-braces: Jackson only treats getXxx/isXxx as property accessors,
    // so a hasXxx method was never a serialization candidate. On getOriginalUrl() below it is
    // load-bearing.
    @JsonIgnore
    public boolean hasOriginalSize() {
        return originalSize != null && originalSize.getUrl() != null;
    }

    /**
     * The original_size url, or null when this photo has none. Prefer this over
     * {@code getOriginalSize().getUrl()}: the field has no default, so every caller that walks the
     * chain itself needs its own null guard, and the ones that forgot crashed the album, the pager,
     * the grid and the peek view. Use {@link #hasOriginalSize()} only when the {@link PhotoSize}
     * itself is needed, such as for its width and height.
     *
     * <p>@JsonIgnore is required, not decorative: this is a getXxx, which Jackson would otherwise
     * publish as an extra "originalUrl" property when a Photo is serialized.
     */
    @JsonIgnore
    public String getOriginalUrl() {
        return originalSize == null ? null : originalSize.getUrl();
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }
}
