package me.edgan.redditslide.Tumblr;

import androidx.annotation.Nullable;

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
@JsonPropertyOrder({
    "blog_name",
    "id",
    "post_url",
    "slug",
    "type",
    "date",
    "timestamp",
    "state",
    "format",
    "reblog_key",
    "tags",
    "short_url",
    "summary",
    "recommended_source",
    "recommended_color",
    "highlighted",
    "note_count",
    "caption",
    "reblog",
    "trail",
    "photoset_layout",
    "photos",
    "can_send_in_message",
    "can_like",
    "can_reblog",
    "display_avatar"
})
public class Post {

    @JsonProperty("blog_name")
    @Nullable private String blogName;

    @JsonProperty("id")
    @Nullable private Double id;

    @JsonProperty("post_url")
    @Nullable private String postUrl;

    @JsonProperty("slug")
    @Nullable private String slug;

    @JsonProperty("type")
    @Nullable private String type;

    @JsonProperty("date")
    @Nullable private String date;

    @JsonProperty("timestamp")
    @Nullable private Double timestamp;

    @JsonProperty("state")
    @Nullable private String state;

    @JsonProperty("format")
    @Nullable private String format;

    @JsonProperty("reblog_key")
    @Nullable private String reblogKey;

    @JsonProperty("tags")
    @Nullable private List<String> tags = new ArrayList<String>();

    @JsonProperty("short_url")
    @Nullable private String shortUrl;

    @JsonProperty("summary")
    @Nullable private String summary;

    @JsonProperty("recommended_source")
    @Nullable private Object recommendedSource;

    @JsonProperty("recommended_color")
    @Nullable private Object recommendedColor;

    @JsonProperty("highlighted")
    @Nullable private List<Object> highlighted = new ArrayList<Object>();

    @JsonProperty("note_count")
    @Nullable private Integer noteCount;

    @JsonProperty("caption")
    @Nullable private String caption;

    @JsonProperty("reblog")
    @Nullable private Reblog reblog;

    @JsonProperty("trail")
    @Nullable private List<Trail> trail = new ArrayList<Trail>();

    @JsonProperty("photoset_layout")
    @Nullable private String photosetLayout;

    @JsonProperty("photos")
    @Nullable private List<Photo> photos = new ArrayList<Photo>();

    @JsonProperty("can_send_in_message")
    @Nullable private Boolean canSendInMessage;

    @JsonProperty("can_like")
    @Nullable private Boolean canLike;

    @JsonProperty("can_reblog")
    @Nullable private Boolean canReblog;

    @JsonProperty("display_avatar")
    @Nullable private Boolean displayAvatar;

    @JsonIgnore private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * @return The blogName
     */
    @JsonProperty("blog_name")
    @Nullable
    public String getBlogName() {
        return blogName;
    }

    /**
     * @param blogName The blog_name
     */
    @JsonProperty("blog_name")
    public void setBlogName(@Nullable String blogName) {
        this.blogName = blogName;
    }

    /**
     * @return The id
     */
    @JsonProperty("id")
    @Nullable
    public Double getId() {
        return id;
    }

    /**
     * @param id The id
     */
    @JsonProperty("id")
    public void setId(@Nullable Double id) {
        this.id = id;
    }

    /**
     * @return The postUrl
     */
    @JsonProperty("post_url")
    @Nullable
    public String getPostUrl() {
        return postUrl;
    }

    /**
     * @param postUrl The post_url
     */
    @JsonProperty("post_url")
    public void setPostUrl(@Nullable String postUrl) {
        this.postUrl = postUrl;
    }

    /**
     * @return The slug
     */
    @JsonProperty("slug")
    @Nullable
    public String getSlug() {
        return slug;
    }

    /**
     * @param slug The slug
     */
    @JsonProperty("slug")
    public void setSlug(@Nullable String slug) {
        this.slug = slug;
    }

    /**
     * @return The type
     */
    @JsonProperty("type")
    @Nullable
    public String getType() {
        return type;
    }

    /**
     * @param type The type
     */
    @JsonProperty("type")
    public void setType(@Nullable String type) {
        this.type = type;
    }

    /**
     * @return The date
     */
    @JsonProperty("date")
    @Nullable
    public String getDate() {
        return date;
    }

    /**
     * @param date The date
     */
    @JsonProperty("date")
    public void setDate(@Nullable String date) {
        this.date = date;
    }

    /**
     * @return The timestamp
     */
    @JsonProperty("timestamp")
    @Nullable
    public Double getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp The timestamp
     */
    @JsonProperty("timestamp")
    public void setTimestamp(@Nullable Double timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * @return The state
     */
    @JsonProperty("state")
    @Nullable
    public String getState() {
        return state;
    }

    /**
     * @param state The state
     */
    @JsonProperty("state")
    public void setState(@Nullable String state) {
        this.state = state;
    }

    /**
     * @return The format
     */
    @JsonProperty("format")
    @Nullable
    public String getFormat() {
        return format;
    }

    /**
     * @param format The format
     */
    @JsonProperty("format")
    public void setFormat(@Nullable String format) {
        this.format = format;
    }

    /**
     * @return The reblogKey
     */
    @JsonProperty("reblog_key")
    @Nullable
    public String getReblogKey() {
        return reblogKey;
    }

    /**
     * @param reblogKey The reblog_key
     */
    @JsonProperty("reblog_key")
    public void setReblogKey(@Nullable String reblogKey) {
        this.reblogKey = reblogKey;
    }

    /**
     * @return The tags
     */
    @JsonProperty("tags")
    @Nullable
    public List<String> getTags() {
        return tags;
    }

    /**
     * @param tags The tags
     */
    @JsonProperty("tags")
    public void setTags(@Nullable List<String> tags) {
        this.tags = tags;
    }

    /**
     * @return The shortUrl
     */
    @JsonProperty("short_url")
    @Nullable
    public String getShortUrl() {
        return shortUrl;
    }

    /**
     * @param shortUrl The short_url
     */
    @JsonProperty("short_url")
    public void setShortUrl(@Nullable String shortUrl) {
        this.shortUrl = shortUrl;
    }

    /**
     * @return The summary
     */
    @JsonProperty("summary")
    @Nullable
    public String getSummary() {
        return summary;
    }

    /**
     * @param summary The summary
     */
    @JsonProperty("summary")
    public void setSummary(@Nullable String summary) {
        this.summary = summary;
    }

    /**
     * @return The recommendedSource
     */
    @JsonProperty("recommended_source")
    @Nullable
    public Object getRecommendedSource() {
        return recommendedSource;
    }

    /**
     * @param recommendedSource The recommended_source
     */
    @JsonProperty("recommended_source")
    public void setRecommendedSource(@Nullable Object recommendedSource) {
        this.recommendedSource = recommendedSource;
    }

    /**
     * @return The recommendedColor
     */
    @JsonProperty("recommended_color")
    @Nullable
    public Object getRecommendedColor() {
        return recommendedColor;
    }

    /**
     * @param recommendedColor The recommended_color
     */
    @JsonProperty("recommended_color")
    public void setRecommendedColor(@Nullable Object recommendedColor) {
        this.recommendedColor = recommendedColor;
    }

    /**
     * @return The highlighted
     */
    @JsonProperty("highlighted")
    @Nullable
    public List<Object> getHighlighted() {
        return highlighted;
    }

    /**
     * @param highlighted The highlighted
     */
    @JsonProperty("highlighted")
    public void setHighlighted(@Nullable List<Object> highlighted) {
        this.highlighted = highlighted;
    }

    /**
     * @return The noteCount
     */
    @JsonProperty("note_count")
    @Nullable
    public Integer getNoteCount() {
        return noteCount;
    }

    /**
     * @param noteCount The note_count
     */
    @JsonProperty("note_count")
    public void setNoteCount(@Nullable Integer noteCount) {
        this.noteCount = noteCount;
    }

    /**
     * @return The caption
     */
    @JsonProperty("caption")
    @Nullable
    public String getCaption() {
        return caption;
    }

    /**
     * @param caption The caption
     */
    @JsonProperty("caption")
    public void setCaption(@Nullable String caption) {
        this.caption = caption;
    }

    /**
     * @return The reblog
     */
    @JsonProperty("reblog")
    @Nullable
    public Reblog getReblog() {
        return reblog;
    }

    /**
     * @param reblog The reblog
     */
    @JsonProperty("reblog")
    public void setReblog(@Nullable Reblog reblog) {
        this.reblog = reblog;
    }

    /**
     * @return The trail
     */
    @JsonProperty("trail")
    @Nullable
    public List<Trail> getTrail() {
        return trail;
    }

    /**
     * @param trail The trail
     */
    @JsonProperty("trail")
    public void setTrail(@Nullable List<Trail> trail) {
        this.trail = trail;
    }

    /**
     * @return The photosetLayout
     */
    @JsonProperty("photoset_layout")
    @Nullable
    public String getPhotosetLayout() {
        return photosetLayout;
    }

    /**
     * @param photosetLayout The photoset_layout
     */
    @JsonProperty("photoset_layout")
    public void setPhotosetLayout(@Nullable String photosetLayout) {
        this.photosetLayout = photosetLayout;
    }

    /**
     * @return The photos
     */
    @JsonProperty("photos")
    @Nullable
    public List<Photo> getPhotos() {
        return photos;
    }

    /**
     * @param photos The photos
     */
    @JsonProperty("photos")
    public void setPhotos(@Nullable List<Photo> photos) {
        this.photos = photos;
    }

    /**
     * @return The canSendInMessage
     */
    @JsonProperty("can_send_in_message")
    @Nullable
    public Boolean getCanSendInMessage() {
        return canSendInMessage;
    }

    /**
     * @param canSendInMessage The can_send_in_message
     */
    @JsonProperty("can_send_in_message")
    public void setCanSendInMessage(@Nullable Boolean canSendInMessage) {
        this.canSendInMessage = canSendInMessage;
    }

    /**
     * @return The canLike
     */
    @JsonProperty("can_like")
    @Nullable
    public Boolean getCanLike() {
        return canLike;
    }

    /**
     * @param canLike The can_like
     */
    @JsonProperty("can_like")
    public void setCanLike(@Nullable Boolean canLike) {
        this.canLike = canLike;
    }

    /**
     * @return The canReblog
     */
    @JsonProperty("can_reblog")
    @Nullable
    public Boolean getCanReblog() {
        return canReblog;
    }

    /**
     * @param canReblog The can_reblog
     */
    @JsonProperty("can_reblog")
    public void setCanReblog(@Nullable Boolean canReblog) {
        this.canReblog = canReblog;
    }

    /**
     * @return The displayAvatar
     */
    @JsonProperty("display_avatar")
    @Nullable
    public Boolean getDisplayAvatar() {
        return displayAvatar;
    }

    /**
     * @param displayAvatar The display_avatar
     */
    @JsonProperty("display_avatar")
    public void setDisplayAvatar(@Nullable Boolean displayAvatar) {
        this.displayAvatar = displayAvatar;
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
