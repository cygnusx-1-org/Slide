package me.edgan.redditslide.Tumblr;

import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"meta", "response"})
public class TumblrPost {

    @JsonProperty("meta")
    @Nullable private Meta meta;

    @JsonProperty("response")
    @Nullable private Response response;

    /**
     * @return The meta
     */
    @JsonProperty("meta")
    @Nullable
    public Meta getMeta() {
        return meta;
    }

    /**
     * @param meta The meta
     */
    @JsonProperty("meta")
    public void setMeta(@Nullable Meta meta) {
        this.meta = meta;
    }

    /**
     * @return The response
     */
    @JsonProperty("response")
    @Nullable
    public Response getResponse() {
        return response;
    }

    /**
     * @param response The response
     */
    @JsonProperty("response")
    public void setResponse(@Nullable Response response) {
        this.response = response;
    }
}
