package me.edgan.redditslide.ImgurAlbum;

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
@JsonPropertyOrder({"data", "success", "status"})
public class AlbumImage {

    @JsonProperty("data")
    @Nullable private Data data;

    @JsonProperty("success")
    @Nullable private Boolean success;

    @JsonProperty("status")
    @Nullable private Integer status;

    @JsonIgnore private Map<String, Object> additionalProperties = new HashMap<>();

    /**
     * @return The data
     */
    @JsonProperty("data")
    @Nullable
    public Data getData() {
        return data;
    }

    /**
     * @param data The data
     */
    @JsonProperty("data")
    public void setData(@Nullable Data data) {
        this.data = data;
    }

    /**
     * @return The success
     */
    @JsonProperty("success")
    @Nullable
    public Boolean getSuccess() {
        return success;
    }

    /**
     * @param success The success
     */
    @JsonProperty("success")
    public void setSuccess(@Nullable Boolean success) {
        this.success = success;
    }

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

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }
}
