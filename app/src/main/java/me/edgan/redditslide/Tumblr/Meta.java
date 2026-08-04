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
@JsonPropertyOrder({"status", "msg"})
public class Meta {

    @JsonProperty("status")
    @Nullable private Integer status;

    @JsonProperty("msg")
    @Nullable private String msg;

    @JsonIgnore private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * @return The status
     */
    @JsonProperty("status")
    @Nullable
    public Integer getStatus() {
        return status;
    }

    /**
     * @param status The status
     */
    @JsonProperty("status")
    public void setStatus(@Nullable Integer status) {
        this.status = status;
    }

    /**
     * @return The msg
     */
    @JsonProperty("msg")
    @Nullable
    public String getMsg() {
        return msg;
    }

    /**
     * @param msg The msg
     */
    @JsonProperty("msg")
    public void setMsg(@Nullable String msg) {
        this.msg = msg;
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
