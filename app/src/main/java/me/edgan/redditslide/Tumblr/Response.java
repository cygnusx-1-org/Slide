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
@JsonPropertyOrder({"blog", "posts", "total_posts"})
public class Response {

    @JsonProperty("blog")
    @Nullable private Blog blog;

    @JsonProperty("posts")
    @Nullable private List<Post> posts = new ArrayList<Post>();

    @JsonProperty("total_posts")
    @Nullable private Integer totalPosts;

    @JsonIgnore private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * @return The blog
     */
    @JsonProperty("blog")
    @Nullable
    public Blog getBlog() {
        return blog;
    }

    /**
     * @param blog The blog
     */
    @JsonProperty("blog")
    public void setBlog(@Nullable Blog blog) {
        this.blog = blog;
    }

    /**
     * @return The posts
     */
    @JsonProperty("posts")
    @Nullable
    public List<Post> getPosts() {
        return posts;
    }

    /**
     * @param posts The posts
     */
    @JsonProperty("posts")
    public void setPosts(@Nullable List<Post> posts) {
        this.posts = posts;
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

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }
}
