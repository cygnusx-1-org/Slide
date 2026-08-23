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
    "header_full_width",
    "header_full_height",
    "header_focus_width",
    "header_focus_height",
    "avatar_shape",
    "background_color",
    "body_font",
    "header_bounds",
    "header_image",
    "header_image_focused",
    "header_image_scaled",
    "header_stretch",
    "link_color",
    "show_avatar",
    "show_description",
    "show_header_image",
    "show_title",
    "title_color",
    "title_font",
    "title_font_weight"
})
public class Theme {

    @JsonProperty("header_full_width")
    @Nullable private Integer headerFullWidth;

    @JsonProperty("header_full_height")
    @Nullable private Integer headerFullHeight;

    @JsonProperty("header_focus_width")
    @Nullable private Integer headerFocusWidth;

    @JsonProperty("header_focus_height")
    @Nullable private Integer headerFocusHeight;

    @JsonProperty("avatar_shape")
    @Nullable private String avatarShape;

    @JsonProperty("background_color")
    @Nullable private String backgroundColor;

    @JsonProperty("body_font")
    @Nullable private String bodyFont;

    @JsonProperty("header_bounds")
    @Nullable private String headerBounds;

    @JsonProperty("header_image")
    @Nullable private String headerImage;

    @JsonProperty("header_image_focused")
    @Nullable private String headerImageFocused;

    @JsonProperty("header_image_scaled")
    @Nullable private String headerImageScaled;

    @JsonProperty("header_stretch")
    @Nullable private Boolean headerStretch;

    @JsonProperty("link_color")
    @Nullable private String linkColor;

    @JsonProperty("show_avatar")
    @Nullable private Boolean showAvatar;

    @JsonProperty("show_description")
    @Nullable private Boolean showDescription;

    @JsonProperty("show_header_image")
    @Nullable private Boolean showHeaderImage;

    @JsonProperty("show_title")
    @Nullable private Boolean showTitle;

    @JsonProperty("title_color")
    @Nullable private String titleColor;

    @JsonProperty("title_font")
    @Nullable private String titleFont;

    @JsonProperty("title_font_weight")
    @Nullable private String titleFontWeight;

    @JsonIgnore private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * @return The headerFullWidth
     */
    @JsonProperty("header_full_width")
    @Nullable
    public Integer getHeaderFullWidth() {
        return headerFullWidth;
    }

    /**
     * @param headerFullWidth The header_full_width
     */
    @JsonProperty("header_full_width")
    public void setHeaderFullWidth(@Nullable Integer headerFullWidth) {
        this.headerFullWidth = headerFullWidth;
    }

    /**
     * @return The headerFullHeight
     */
    @JsonProperty("header_full_height")
    @Nullable
    public Integer getHeaderFullHeight() {
        return headerFullHeight;
    }

    /**
     * @param headerFullHeight The header_full_height
     */
    @JsonProperty("header_full_height")
    public void setHeaderFullHeight(@Nullable Integer headerFullHeight) {
        this.headerFullHeight = headerFullHeight;
    }

    /**
     * @return The headerFocusWidth
     */
    @JsonProperty("header_focus_width")
    @Nullable
    public Integer getHeaderFocusWidth() {
        return headerFocusWidth;
    }

    /**
     * @param headerFocusWidth The header_focus_width
     */
    @JsonProperty("header_focus_width")
    public void setHeaderFocusWidth(@Nullable Integer headerFocusWidth) {
        this.headerFocusWidth = headerFocusWidth;
    }

    /**
     * @return The headerFocusHeight
     */
    @JsonProperty("header_focus_height")
    @Nullable
    public Integer getHeaderFocusHeight() {
        return headerFocusHeight;
    }

    /**
     * @param headerFocusHeight The header_focus_height
     */
    @JsonProperty("header_focus_height")
    public void setHeaderFocusHeight(@Nullable Integer headerFocusHeight) {
        this.headerFocusHeight = headerFocusHeight;
    }

    /**
     * @return The avatarShape
     */
    @JsonProperty("avatar_shape")
    @Nullable
    public String getAvatarShape() {
        return avatarShape;
    }

    /**
     * @param avatarShape The avatar_shape
     */
    @JsonProperty("avatar_shape")
    public void setAvatarShape(@Nullable String avatarShape) {
        this.avatarShape = avatarShape;
    }

    /**
     * @return The backgroundColor
     */
    @JsonProperty("background_color")
    @Nullable
    public String getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * @param backgroundColor The background_color
     */
    @JsonProperty("background_color")
    public void setBackgroundColor(@Nullable String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    /**
     * @return The bodyFont
     */
    @JsonProperty("body_font")
    @Nullable
    public String getBodyFont() {
        return bodyFont;
    }

    /**
     * @param bodyFont The body_font
     */
    @JsonProperty("body_font")
    public void setBodyFont(@Nullable String bodyFont) {
        this.bodyFont = bodyFont;
    }

    /**
     * @return The headerBounds
     */
    @JsonProperty("header_bounds")
    @Nullable
    public String getHeaderBounds() {
        return headerBounds;
    }

    /**
     * @param headerBounds The header_bounds
     */
    @JsonProperty("header_bounds")
    public void setHeaderBounds(@Nullable String headerBounds) {
        this.headerBounds = headerBounds;
    }

    /**
     * @return The headerImage
     */
    @JsonProperty("header_image")
    @Nullable
    public String getHeaderImage() {
        return headerImage;
    }

    /**
     * @param headerImage The header_image
     */
    @JsonProperty("header_image")
    public void setHeaderImage(@Nullable String headerImage) {
        this.headerImage = headerImage;
    }

    /**
     * @return The headerImageFocused
     */
    @JsonProperty("header_image_focused")
    @Nullable
    public String getHeaderImageFocused() {
        return headerImageFocused;
    }

    /**
     * @param headerImageFocused The header_image_focused
     */
    @JsonProperty("header_image_focused")
    public void setHeaderImageFocused(@Nullable String headerImageFocused) {
        this.headerImageFocused = headerImageFocused;
    }

    /**
     * @return The headerImageScaled
     */
    @JsonProperty("header_image_scaled")
    @Nullable
    public String getHeaderImageScaled() {
        return headerImageScaled;
    }

    /**
     * @param headerImageScaled The header_image_scaled
     */
    @JsonProperty("header_image_scaled")
    public void setHeaderImageScaled(@Nullable String headerImageScaled) {
        this.headerImageScaled = headerImageScaled;
    }

    /**
     * @return The headerStretch
     */
    @JsonProperty("header_stretch")
    @Nullable
    public Boolean getHeaderStretch() {
        return headerStretch;
    }

    /**
     * @param headerStretch The header_stretch
     */
    @JsonProperty("header_stretch")
    public void setHeaderStretch(@Nullable Boolean headerStretch) {
        this.headerStretch = headerStretch;
    }

    /**
     * @return The linkColor
     */
    @JsonProperty("link_color")
    @Nullable
    public String getLinkColor() {
        return linkColor;
    }

    /**
     * @param linkColor The link_color
     */
    @JsonProperty("link_color")
    public void setLinkColor(@Nullable String linkColor) {
        this.linkColor = linkColor;
    }

    /**
     * @return The showAvatar
     */
    @JsonProperty("show_avatar")
    @Nullable
    public Boolean getShowAvatar() {
        return showAvatar;
    }

    /**
     * @param showAvatar The show_avatar
     */
    @JsonProperty("show_avatar")
    public void setShowAvatar(@Nullable Boolean showAvatar) {
        this.showAvatar = showAvatar;
    }

    /**
     * @return The showDescription
     */
    @JsonProperty("show_description")
    @Nullable
    public Boolean getShowDescription() {
        return showDescription;
    }

    /**
     * @param showDescription The show_description
     */
    @JsonProperty("show_description")
    public void setShowDescription(@Nullable Boolean showDescription) {
        this.showDescription = showDescription;
    }

    /**
     * @return The showHeaderImage
     */
    @JsonProperty("show_header_image")
    @Nullable
    public Boolean getShowHeaderImage() {
        return showHeaderImage;
    }

    /**
     * @param showHeaderImage The show_header_image
     */
    @JsonProperty("show_header_image")
    public void setShowHeaderImage(@Nullable Boolean showHeaderImage) {
        this.showHeaderImage = showHeaderImage;
    }

    /**
     * @return The showTitle
     */
    @JsonProperty("show_title")
    @Nullable
    public Boolean getShowTitle() {
        return showTitle;
    }

    /**
     * @param showTitle The show_title
     */
    @JsonProperty("show_title")
    public void setShowTitle(@Nullable Boolean showTitle) {
        this.showTitle = showTitle;
    }

    /**
     * @return The titleColor
     */
    @JsonProperty("title_color")
    @Nullable
    public String getTitleColor() {
        return titleColor;
    }

    /**
     * @param titleColor The title_color
     */
    @JsonProperty("title_color")
    public void setTitleColor(@Nullable String titleColor) {
        this.titleColor = titleColor;
    }

    /**
     * @return The titleFont
     */
    @JsonProperty("title_font")
    @Nullable
    public String getTitleFont() {
        return titleFont;
    }

    /**
     * @param titleFont The title_font
     */
    @JsonProperty("title_font")
    public void setTitleFont(@Nullable String titleFont) {
        this.titleFont = titleFont;
    }

    /**
     * @return The titleFontWeight
     */
    @JsonProperty("title_font_weight")
    @Nullable
    public String getTitleFontWeight() {
        return titleFontWeight;
    }

    /**
     * @param titleFontWeight The title_font_weight
     */
    @JsonProperty("title_font_weight")
    public void setTitleFontWeight(@Nullable String titleFontWeight) {
        this.titleFontWeight = titleFontWeight;
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
