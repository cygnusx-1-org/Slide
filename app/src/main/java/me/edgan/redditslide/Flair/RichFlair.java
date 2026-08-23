package me.edgan.redditslide.Flair;

import androidx.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Every field is populated by the JSON deserializer and stays null when Reddit omits the key, so
 * all of them are nullable regardless of how consistently they show up in practice.
 */
public class RichFlair {

    @SerializedName("type")
    @Expose
    private @Nullable String type;

    @SerializedName("text_editable")
    @Expose(serialize = true, deserialize = false)
    private @Nullable Boolean textEditable;

    @SerializedName("allowable_content")
    @Expose
    private @Nullable String allowableContent;

    @SerializedName("text")
    @Expose
    private @Nullable String text;

    @SerializedName("id")
    @Expose
    private @Nullable String id;

    public @Nullable String getType() {
        return type;
    }

    public void setType(@Nullable String type) {
        this.type = type;
    }

    public @Nullable Boolean getTextEditable() {
        return textEditable;
    }

    public void setTextEditable(@Nullable Boolean textEditable) {
        this.textEditable = textEditable;
    }

    public @Nullable String getAllowableContent() {
        return allowableContent;
    }

    public void setAllowableContent(@Nullable String allowableContent) {
        this.allowableContent = allowableContent;
    }

    public @Nullable String getText() {
        return text;
    }

    public void setText(@Nullable String text) {
        this.text = text;
    }

    public @Nullable String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }
}
