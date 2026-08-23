package me.edgan.redditslide.Tumblr;

import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.HashMap;
import java.util.Map;

/** One photo rendition (url + dimensions) — used for both original and alt sizes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"url", "width", "height"})
public class PhotoSize {

    @JsonProperty("url")
    @Nullable private String url;

    @JsonProperty("width")
    @Nullable private Integer width;

    @JsonProperty("height")
    @Nullable private Integer height;

    @JsonIgnore private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * @return The url
     */
    @JsonProperty("url")
    @Nullable
    public String getUrl() {
        return url;
    }

    /**
     * @param url The url
     */
    @JsonProperty("url")
    public void setUrl(@Nullable String url) {
        this.url = url;
    }

    /**
     * @return The width
     */
    @JsonProperty("width")
    @Nullable
    public Integer getWidth() {
        return width;
    }

    /**
     * @param width The width
     */
    @JsonProperty("width")
    public void setWidth(@Nullable Integer width) {
        this.width = width;
    }

    /**
     * @return The height
     */
    @JsonProperty("height")
    @Nullable
    public Integer getHeight() {
        return height;
    }

    /**
     * @param height The height
     */
    @JsonProperty("height")
    public void setHeight(@Nullable Integer height) {
        this.height = height;
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
