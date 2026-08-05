package me.edgan.redditslide.util;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jspecify.annotations.NullMarked;

/**
 * Missing-member reads for GSON, the equivalent of Jackson's {@link
 * com.fasterxml.jackson.databind.JsonNode#path(String)}.
 *
 * <p>{@code JsonObject.get} returns Java null for an absent member, so navigating a response shape
 * that turns out not to match — a truncated imgur reply, an API that renamed a field — throws an
 * NPE partway down the chain. GSON ships no {@code path()} of its own, so these stand in for it:
 * each returns an empty/default value that reads as "absent" rather than null, which lets a caller
 * navigate the whole chain and test the result once at the end.
 *
 * <p>This is deliberately not the same as asserting the members exist. Where absence should change
 * what the app does, test for it — an empty string from {@link #string} is a usable signal, an
 * empty {@link JsonObject} from {@link #obj} answers {@code has()} with false. See NULLAWAY.md
 * phase 8.
 */
@NullMarked
public final class GsonUtil {
    private GsonUtil() {}

    /** The member as an object, or an empty object when it is absent, null, or not an object. */
    public static JsonObject obj(@Nullable JsonElement element, String member) {
        final JsonElement child = member(element, member);
        return child != null && child.isJsonObject() ? child.getAsJsonObject() : new JsonObject();
    }

    /** The member as an array, or an empty array when it is absent, null, or not an array. */
    public static JsonArray array(@Nullable JsonElement element, String member) {
        final JsonElement child = member(element, member);
        return child != null && child.isJsonArray() ? child.getAsJsonArray() : new JsonArray();
    }

    /**
     * The member as a string, or {@code defValue} when it is absent, JSON null, or not a primitive.
     */
    public static String string(@Nullable JsonElement element, String member, String defValue) {
        final JsonElement child = member(element, member);
        return child != null && child.isJsonPrimitive() ? child.getAsString() : defValue;
    }

    /**
     * The member as a boolean, or {@code defValue} when it is absent, JSON null, or not a
     * primitive.
     */
    public static boolean bool(@Nullable JsonElement element, String member, boolean defValue) {
        final JsonElement child = member(element, member);
        return (child != null && child.isJsonPrimitive()) ? child.getAsBoolean() : defValue;
    }

    /** The raw member, or null when the parent is not an object or has no such member. */
    private static @Nullable JsonElement member(@Nullable JsonElement element, String member) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        final JsonElement child = element.getAsJsonObject().get(member);
        return child == null || child.isJsonNull() ? null : child;
    }
}
