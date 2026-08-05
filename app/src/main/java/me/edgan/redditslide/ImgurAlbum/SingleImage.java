package me.edgan.redditslide.ImgurAlbum;

/** Created by carlo_000 on 5/3/2016. */
import androidx.annotation.Nullable;

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
    "id",
    "title",
    "description",
    "datetime",
    "type",
    "animated",
    "width",
    "height",
    "size",
    "views",
    "bandwidth",
    "vote",
    "favorite",
    "nsfw",
    "section",
    "account_url",
    "account_id",
    "in_gallery",
    "link"
})
public class SingleImage {

    @JsonProperty("id")
    @Nullable private String id;

    @JsonProperty("title")
    @Nullable private String title;

    // Unread, but part of the generated DTO shape and written by setDescription.
    @SuppressWarnings("UnusedVariable")
    @JsonProperty("description")
    @Nullable private String description;

    @JsonProperty("datetime")
    @Nullable private Double datetime;

    @JsonProperty("type")
    @Nullable private String type;

    @JsonProperty("animated")
    @Nullable private Boolean animated;

    @JsonProperty("width")
    @Nullable private Integer width;

    @JsonProperty("height")
    @Nullable private Integer height;

    @JsonProperty("size")
    @Nullable private Double size;

    @JsonProperty("views")
    @Nullable private Double views;

    @JsonProperty("bandwidth")
    @Nullable private Double bandwidth;

    @JsonProperty("vote")
    @Nullable private Object vote;

    @JsonProperty("favorite")
    @Nullable private Boolean favorite;

    @JsonProperty("nsfw")
    @Nullable private Boolean nsfw;

    @JsonProperty("section")
    @Nullable private String section;

    @JsonProperty("account_url")
    @Nullable private Object accountUrl;

    @JsonProperty("account_id")
    @Nullable private Object accountId;

    @JsonProperty("in_gallery")
    @Nullable private Boolean inGallery;

    @JsonProperty("link")
    @Nullable private String link;

    @JsonIgnore private Map<String, Object> additionalProperties = new HashMap<>();

    /**
     * @return The id
     */
    @JsonProperty("id")
    @Nullable
    public String getId() {
        return id;
    }

    /**
     * @param id The id
     */
    @JsonProperty("id")
    public void setId(@Nullable String id) {
        this.id = id;
    }

    /**
     * @return The title
     */
    @JsonProperty("title")
    @Nullable
    public String getTitle() {
        return title;
    }

    /**
     * @param title The title
     */
    @JsonProperty("title")
    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    /**
     * @param description The description
     */
    @JsonProperty("description")
    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    /**
     * @return The datetime
     */
    @JsonProperty("datetime")
    @Nullable
    public Double getDatetime() {
        return datetime;
    }

    /**
     * @param datetime The datetime
     */
    @JsonProperty("datetime")
    public void setDatetime(@Nullable Double datetime) {
        this.datetime = datetime;
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
     * @return The animated
     */
    @JsonProperty("animated")
    @Nullable
    public Boolean getAnimated() {
        return animated;
    }

    /**
     * @param animated The animated
     */
    @JsonProperty("animated")
    public void setAnimated(@Nullable Boolean animated) {
        this.animated = animated;
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

    /**
     * @return The size
     */
    @JsonProperty("size")
    @Nullable
    public Double getSize() {
        return size;
    }

    /**
     * @param size The size
     */
    @JsonProperty("size")
    public void setSize(@Nullable Double size) {
        this.size = size;
    }

    /**
     * @return The views
     */
    @JsonProperty("views")
    @Nullable
    public Double getViews() {
        return views;
    }

    /**
     * @param views The views
     */
    @JsonProperty("views")
    public void setViews(@Nullable Double views) {
        this.views = views;
    }

    /**
     * @return The bandwidth
     */
    @JsonProperty("bandwidth")
    @Nullable
    public Double getBandwidth() {
        return bandwidth;
    }

    /**
     * @param bandwidth The bandwidth
     */
    @JsonProperty("bandwidth")
    public void setBandwidth(@Nullable Double bandwidth) {
        this.bandwidth = bandwidth;
    }

    /**
     * @return The vote
     */
    @JsonProperty("vote")
    @Nullable
    public Object getVote() {
        return vote;
    }

    /**
     * @param vote The vote
     */
    @JsonProperty("vote")
    public void setVote(@Nullable Object vote) {
        this.vote = vote;
    }

    /**
     * @return The favorite
     */
    @JsonProperty("favorite")
    @Nullable
    public Boolean getFavorite() {
        return favorite;
    }

    /**
     * @param favorite The favorite
     */
    @JsonProperty("favorite")
    public void setFavorite(@Nullable Boolean favorite) {
        this.favorite = favorite;
    }

    /**
     * @return The nsfw
     */
    @JsonProperty("nsfw")
    @Nullable
    public Boolean getNsfw() {
        return nsfw;
    }

    /**
     * @param nsfw The nsfw
     */
    @JsonProperty("nsfw")
    public void setNsfw(@Nullable Boolean nsfw) {
        this.nsfw = nsfw;
    }

    /**
     * @return The section
     */
    @JsonProperty("section")
    @Nullable
    public String getSection() {
        return section;
    }

    /**
     * @param section The section
     */
    @JsonProperty("section")
    public void setSection(@Nullable String section) {
        this.section = section;
    }

    /**
     * @return The accountUrl
     */
    @JsonProperty("account_url")
    @Nullable
    public Object getAccountUrl() {
        return accountUrl;
    }

    /**
     * @param accountUrl The account_url
     */
    @JsonProperty("account_url")
    public void setAccountUrl(@Nullable Object accountUrl) {
        this.accountUrl = accountUrl;
    }

    /**
     * @return The accountId
     */
    @JsonProperty("account_id")
    @Nullable
    public Object getAccountId() {
        return accountId;
    }

    /**
     * @param accountId The account_id
     */
    @JsonProperty("account_id")
    public void setAccountId(@Nullable Object accountId) {
        this.accountId = accountId;
    }

    /**
     * @return The inGallery
     */
    @JsonProperty("in_gallery")
    @Nullable
    public Boolean getInGallery() {
        return inGallery;
    }

    /**
     * @param inGallery The in_gallery
     */
    @JsonProperty("in_gallery")
    public void setInGallery(@Nullable Boolean inGallery) {
        this.inGallery = inGallery;
    }

    /**
     * @return The link
     */
    @JsonProperty("link")
    @Nullable
    public String getLink() {
        return link;
    }

    /**
     * @param link The link
     */
    @JsonProperty("link")
    public void setLink(@Nullable String link) {
        this.link = link;
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
