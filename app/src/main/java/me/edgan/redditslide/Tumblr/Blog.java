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

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "title",
    "name",
    "total_posts",
    "posts",
    "url",
    "updated",
    "description",
    "is_nsfw",
    "ask",
    "ask_page_title",
    "ask_anon",
    "share_likes",
    "likes"
})
public class Blog {

    @JsonProperty("title")
    @Nullable private String title;

    @JsonProperty("name")
    @Nullable private String name;

    @JsonProperty("total_posts")
    @Nullable private Integer totalPosts;

    @JsonProperty("posts")
    @Nullable private Integer posts;

    @JsonProperty("url")
    @Nullable private String url;

    @JsonProperty("updated")
    @Nullable private Double updated;

    @JsonProperty("description")
    @Nullable private String description;

    @JsonProperty("is_nsfw")
    @Nullable private Boolean isNsfw;

    @JsonProperty("ask")
    @Nullable private Boolean ask;

    @JsonProperty("ask_page_title")
    @Nullable private String askPageTitle;

    @JsonProperty("ask_anon")
    @Nullable private Boolean askAnon;

    @JsonProperty("share_likes")
    @Nullable private Boolean shareLikes;

    @JsonProperty("likes")
    @Nullable private Integer likes;

    @JsonIgnore private Map<String, Object> additionalProperties = new HashMap<String, Object>();

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
     * @return The name
     */
    @JsonProperty("name")
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * @param name The name
     */
    @JsonProperty("name")
    public void setName(@Nullable String name) {
        this.name = name;
    }

    /**
     * @return The totalPosts
     */
    @JsonProperty("total_posts")
    @Nullable
    public Integer getTotalPosts() {
        return totalPosts;
    }

    /**
     * @param totalPosts The total_posts
     */
    @JsonProperty("total_posts")
    public void setTotalPosts(@Nullable Integer totalPosts) {
        this.totalPosts = totalPosts;
    }

    /**
     * @return The posts
     */
    @JsonProperty("posts")
    @Nullable
    public Integer getPosts() {
        return posts;
    }

    /**
     * @param posts The posts
     */
    @JsonProperty("posts")
    public void setPosts(@Nullable Integer posts) {
        this.posts = posts;
    }

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
     * @return The updated
     */
    @JsonProperty("updated")
    @Nullable
    public Double getUpdated() {
        return updated;
    }

    /**
     * @param updated The updated
     */
    @JsonProperty("updated")
    public void setUpdated(@Nullable Double updated) {
        this.updated = updated;
    }

    /**
     * @return The description
     */
    @JsonProperty("description")
    @Nullable
    public String getDescription() {
        return description;
    }

    /**
     * @param description The description
     */
    @JsonProperty("description")
    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    /**
     * @return The isNsfw
     */
    @JsonProperty("is_nsfw")
    @Nullable
    public Boolean getIsNsfw() {
        return isNsfw;
    }

    /**
     * @param isNsfw The is_nsfw
     */
    @JsonProperty("is_nsfw")
    public void setIsNsfw(@Nullable Boolean isNsfw) {
        this.isNsfw = isNsfw;
    }

    /**
     * @return The ask
     */
    @JsonProperty("ask")
    @Nullable
    public Boolean getAsk() {
        return ask;
    }

    /**
     * @param ask The ask
     */
    @JsonProperty("ask")
    public void setAsk(@Nullable Boolean ask) {
        this.ask = ask;
    }

    /**
     * @return The askPageTitle
     */
    @JsonProperty("ask_page_title")
    @Nullable
    public String getAskPageTitle() {
        return askPageTitle;
    }

    /**
     * @param askPageTitle The ask_page_title
     */
    @JsonProperty("ask_page_title")
    public void setAskPageTitle(@Nullable String askPageTitle) {
        this.askPageTitle = askPageTitle;
    }

    /**
     * @return The askAnon
     */
    @JsonProperty("ask_anon")
    @Nullable
    public Boolean getAskAnon() {
        return askAnon;
    }

    /**
     * @param askAnon The ask_anon
     */
    @JsonProperty("ask_anon")
    public void setAskAnon(@Nullable Boolean askAnon) {
        this.askAnon = askAnon;
    }

    /**
     * @return The shareLikes
     */
    @JsonProperty("share_likes")
    @Nullable
    public Boolean getShareLikes() {
        return shareLikes;
    }

    /**
     * @param shareLikes The share_likes
     */
    @JsonProperty("share_likes")
    public void setShareLikes(@Nullable Boolean shareLikes) {
        this.shareLikes = shareLikes;
    }

    /**
     * @return The likes
     */
    @JsonProperty("likes")
    @Nullable
    public Integer getLikes() {
        return likes;
    }

    /**
     * @param likes The likes
     */
    @JsonProperty("likes")
    public void setLikes(@Nullable Integer likes) {
        this.likes = likes;
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
