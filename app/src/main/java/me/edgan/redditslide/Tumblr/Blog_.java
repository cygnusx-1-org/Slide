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
@JsonPropertyOrder({"name", "active", "theme", "share_likes", "share_following"})
public class Blog_ {

    @JsonProperty("name")
    @Nullable private String name;

    @JsonProperty("active")
    @Nullable private Boolean active;

    @JsonProperty("theme")
    @Nullable private Theme theme;

    @JsonProperty("share_likes")
    @Nullable private Boolean shareLikes;

    @JsonProperty("share_following")
    @Nullable private Boolean shareFollowing;

    @JsonIgnore private Map<String, Object> additionalProperties = new HashMap<String, Object>();

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
     * @return The active
     */
    @JsonProperty("active")
    @Nullable
    public Boolean getActive() {
        return active;
    }

    /**
     * @param active The active
     */
    @JsonProperty("active")
    public void setActive(@Nullable Boolean active) {
        this.active = active;
    }

    /**
     * @return The theme
     */
    @JsonProperty("theme")
    @Nullable
    public Theme getTheme() {
        return theme;
    }

    /**
     * @param theme The theme
     */
    @JsonProperty("theme")
    public void setTheme(@Nullable Theme theme) {
        this.theme = theme;
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
     * @return The shareFollowing
     */
    @JsonProperty("share_following")
    @Nullable
    public Boolean getShareFollowing() {
        return shareFollowing;
    }

    /**
     * @param shareFollowing The share_following
     */
    @JsonProperty("share_following")
    public void setShareFollowing(@Nullable Boolean shareFollowing) {
        this.shareFollowing = shareFollowing;
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
