package me.edgan.redditslide.ImgurAlbum;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "hash",
    "title",
    "description",
    "width",
    "height",
    "size",
    "ext",
    "animated",
    "prefer_video",
    "looping",
    "datetime"
})
public class Image {

    @JsonProperty("hash")
    private String hash;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("width")
    private Integer width;

    @JsonProperty("height")
    private Integer height;

    @JsonProperty("size")
    private Integer size;

    @JsonProperty("ext")
    private String ext;

    @JsonProperty("animated")
    private Boolean animated;

    @JsonProperty("prefer_video")
    private Boolean preferVideo;

    @JsonProperty("looping")
    private Boolean looping;

    @JsonProperty("datetime")
    private String datetime;

    @JsonIgnore private Map<String, Object> additionalProperties = new HashMap<>();

    /**
     * @return The hash
     */
    @JsonProperty("hash")
    public String getHash() {
        return hash;
    }

    /**
     * @param hash The hash
     */
    @JsonProperty("hash")
    public void setHash(String hash) {
        this.hash = hash;
    }

    /**
     * @return The title
     */
    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    /**
     * @param title The title
     */
    @JsonProperty("title")
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * @return The description
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * @param description The description
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return The width
     */
    @JsonProperty("width")
    public Integer getWidth() {
        return width;
    }

    /**
     * @param width The width
     */
    @JsonProperty("width")
    public void setWidth(Integer width) {
        this.width = width;
    }

    /**
     * @return The height
     */
    @JsonProperty("height")
    public Integer getHeight() {
        return height;
    }

    /**
     * @param height The height
     */
    @JsonProperty("height")
    public void setHeight(Integer height) {
        this.height = height;
    }

    /**
     * @return The size
     */
    @JsonProperty("size")
    public Integer getSize() {
        return size;
    }

    /**
     * @param size The size
     */
    @JsonProperty("size")
    public void setSize(Integer size) {
        this.size = size;
    }

    /**
     * @return The ext
     */
    @JsonProperty("ext")
    public String getExt() {
        return ext;
    }

    /**
     * @param ext The ext
     */
    @JsonProperty("ext")
    public void setExt(String ext) {
        this.ext = ext;
    }

    /**
     * @param animated The animated
     */
    @JsonProperty("animated")
    public void setAnimated(Boolean animated) {
        this.animated = animated;
    }

    /**
     * Whether this image is animated, treating an absent flag as false. This is the only reader of
     * the flag: the raw {@code Boolean} getter it replaced was null for an Imgur entry whose JSON
     * omitted the field, and every caller unboxed it. {@link #getPreferVideo()} and
     * {@link #getLooping()} are nullable in the same way and have no caller yet.
     */
    @JsonIgnore
    public boolean animated() {
        return animated != null && animated;
    }

    /**
     * @return The preferVideo
     */
    @JsonProperty("prefer_video")
    public Boolean getPreferVideo() {
        return preferVideo;
    }

    /**
     * @param preferVideo The prefer_video
     */
    @JsonProperty("prefer_video")
    public void setPreferVideo(Boolean preferVideo) {
        this.preferVideo = preferVideo;
    }

    /**
     * @return The looping
     */
    @JsonProperty("looping")
    public Boolean getLooping() {
        return looping;
    }

    /**
     * @param looping The looping
     */
    @JsonProperty("looping")
    public void setLooping(Boolean looping) {
        this.looping = looping;
    }

    /**
     * @return The datetime
     */
    @JsonProperty("datetime")
    public String getDatetime() {
        return datetime;
    }

    /**
     * @param datetime The datetime
     */
    @JsonProperty("datetime")
    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    /**
     * Whether this entry has enough to build a real url with. The builders below concatenate
     * blindly, so a partial entry yields a syntactically valid but meaningless url
     * ("https://i.imgur.com/nullnull") rather than null — and MediaView does not reject that the way
     * it rejects an empty one. Ask this before handing either url to anything.
     *
     * <p>Not a claim that the current loader produces partial entries: every entry, album or single,
     * is built by AlbumUtils.convertToSingle, which drops one whose link has no extension and whose
     * hash step throws rather than returning null. This guards the class's own invariant instead —
     * hash and ext are independently settable Jackson properties with no default and nothing that
     * forces them to be set together, so the completeness the url builders depend on is asserted
     * where they are used rather than assumed from one caller's behaviour.
     */
    // hasXxx is not a Jackson accessor naming pattern, so this was never a serialization
    // candidate; @JsonIgnore states the intent, as it does on animated() above.
    @JsonIgnore
    public boolean hasImageUrl() {
        return hash != null && ext != null;
    }

    /**
     * Both url builders concatenate unconditionally and never return null, because their consumers
     * are not null-tolerant — AlbumPager truncates at lastIndexOf(".") and the save paths pass the
     * value straight down. The cost is that a partial entry yields a meaningless url rather than
     * nothing, so entries are screened before they get this far: AlbumUtils.convertToSingle drops
     * one with no extension, and {@link #hasImageUrl()} skips one the album path left incomplete.
     * Making these nullable means guarding every consumer first.
     */
    public String getImageUrl() {
        return "https://i.imgur.com/" + hash + ext;
    }

    public String getThumbnailUrl() {
        return "https://i.imgur.com/" + hash + "s" + ext;
    }
}
