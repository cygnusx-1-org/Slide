package me.edgan.redditslide.Flair;

import androidx.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * A richtext flair element. Which fields are present depends on the element type, so all of them
 * are nullable: "t" carries text, "a" and "u" carry an emoji alias and URL.
 */
public class Richtext {

    @SerializedName("e")
    @Expose
    private @Nullable String e;

    @SerializedName("t")
    @Expose
    private @Nullable String t;

    @SerializedName("a")
    @Expose
    private @Nullable String a;

    @SerializedName("u")
    @Expose
    private @Nullable String u;

    public @Nullable String getE() {
        return e;
    }

    public void setE(@Nullable String e) {
        this.e = e;
    }

    public @Nullable String getT() {
        return t;
    }

    public void setT(@Nullable String t) {
        this.t = t;
    }

    public @Nullable String getA() {
        return a;
    }

    public void setA(@Nullable String a) {
        this.a = a;
    }

    public @Nullable String getU() {
        return u;
    }

    public void setU(@Nullable String u) {
        this.u = u;
    }
}
