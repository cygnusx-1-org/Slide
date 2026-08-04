package me.edgan.redditslide.Toolbox;

import androidx.annotation.Nullable;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class defining a toolbox config. Contains removal reasons, mod macros, usernote colors, domain
 * tags, etc.
 */
public class ToolboxConfig {
    @SerializedName("ver")
    private int schema;

    @JsonAdapter(EmptyStringAsNullTypeAdapter.class)
    @Nullable private List<Map<String, String>> domainTags;

    @JsonAdapter(EmptyStringAsNullTypeAdapter.class)
    @Nullable private RemovalReasons removalReasons;

    @JsonAdapter(EmptyStringAsNullTypeAdapter.class)
    @Nullable private List<Map<String, String>> macros;

    @SerializedName("usernoteColors")
    @JsonAdapter(UsernoteTypeDeserializer.class)
    @Nullable private Map<String, Map<String, String>> usernoteTypes;

    @JsonAdapter(EmptyStringAsNullTypeAdapter.class)
    @Nullable private Map<String, String> banMacros;

    public ToolboxConfig() {}

    public int getSchema() {
        return schema;
    }

    @Nullable
    public List<Map<String, String>> getDomainTags() {
        return domainTags;
    }

    @Nullable
    public RemovalReasons getRemovalReasons() {
        return removalReasons;
    }

    @Nullable
    public Map<String, Map<String, String>> getUsernoteTypes() {
        return usernoteTypes;
    }

    public String getUsernoteColor(@Nullable String type) {
        final String color = usernoteDetail(type, "color");
        // gray for non-typed or unknown type notes, same as Toolbox
        return color == null ? "#808080" : color;
    }

    public String getUsernoteText(@Nullable String type) {
        final String text = usernoteDetail(type, "text");
        return text == null ? "" : text;
    }

    /**
     * One detail ("color" or "text") of a usernote type: the sub's own config where it defines the
     * type, Toolbox's built-in defaults otherwise, or null when neither has it.
     */
    @Nullable
    private String usernoteDetail(@Nullable String type, String detail) {
        final Map<String, String> configured =
                usernoteTypes == null ? null : usernoteTypes.get(type);
        if (configured != null) {
            final String value = configured.get(detail);
            if (value != null) {
                return value;
            }
        }
        final Map<String, String> fallback = Toolbox.DEFAULT_USERNOTE_TYPES.get(type);
        return fallback == null ? null : fallback.get(detail);
    }

    public static class UsernoteTypeDeserializer
            implements JsonDeserializer<Map<String, Map<String, String>>> {
        @Override
        @Nullable
        public Map<String, Map<String, String>> deserialize(
                JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            // Toolbox writes an empty string for "no colors", and a hand-edited page can leave a
            // JSON null or an object here; getAsJsonArray() throws IllegalStateException on all
            // three, which is not a JsonParseException and so escapes gson.fromJson uncaught.
            if (!json.isJsonArray()) {
                return null;
            }
            Map<String, Map<String, String>> result = new HashMap<>();
            for (JsonElement noteType : json.getAsJsonArray()) {
                if (!noteType.isJsonObject()) {
                    continue;
                }
                final JsonObject typeObject = noteType.getAsJsonObject();
                final String key = stringMember(typeObject, "key");
                final String color = stringMember(typeObject, "color");
                final String text = stringMember(typeObject, "text");
                // A type missing any of the three is unusable — the add-note dialog reads the
                // color straight into Color.parseColor — and the chain this replaced dereferenced
                // the null JsonObject.get hands back for an absent key, throwing out of
                // gson.fromJson as an NPE that no caller catches.
                if (key == null || color == null || text == null) {
                    continue;
                }
                Map<String, String> details = new HashMap<>();
                details.put("color", color);
                details.put("text", text);
                result.put(key, details);
            }
            return result;
        }

        /**
         * One string member of a usernote type, or null when it is absent, JSON null, or not a
         * string. {@code JsonObject.get} returns {@link com.google.gson.JsonNull} rather than null
         * for an explicit {@code null}, and {@code getAsString()} throws on that and on a
         * non-primitive.
         */
        @Nullable
        private static String stringMember(JsonObject object, String name) {
            final JsonElement member = object.get(name);
            if (member == null || !member.isJsonPrimitive()) {
                return null;
            }
            final JsonPrimitive primitive = member.getAsJsonPrimitive();
            return primitive.isString() ? primitive.getAsString() : null;
        }
    }

    // from https://stackoverflow.com/a/48806970, because toolbox uses empty strings to mean null in
    // some instances
    public static final class EmptyStringAsNullTypeAdapter<T> implements JsonDeserializer<T> {
        @Override
        @Nullable
        public T deserialize(
                final JsonElement jsonElement,
                final Type type,
                final JsonDeserializationContext context)
                throws JsonParseException {
            if (jsonElement.isJsonPrimitive()) {
                final JsonPrimitive jsonPrimitive = jsonElement.getAsJsonPrimitive();
                if (jsonPrimitive.isString() && jsonPrimitive.getAsString().isEmpty()) {
                    return null;
                }
            }
            return context.deserialize(jsonElement, type);
        }
    }
}
