package me.edgan.redditslide.Toolbox;

import android.util.Base64;
import androidx.annotation.ColorInt;
import androidx.annotation.OptIn;
import androidx.media3.common.util.ColorParser;
import androidx.media3.common.util.UnstableApi;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/** A group of usernotes for a subreddit */
@OptIn(markerClass = UnstableApi.class)
public class Usernotes {
    @SerializedName("ver")
    private int schema;

    // The three below are set either by the four-argument constructor or, for a wiki page, by
    // GSON followed by setSubreddit() in Toolbox — which keeps a result only when isUsable()
    // says both of the deserialized ones arrived.
    @SuppressWarnings("NullAway.Init")
    private UsernotesConstants constants;

    @SerializedName("blob")
    @SuppressWarnings("NullAway.Init")
    private Map<String, List<Usernote>> notes;

    @SuppressWarnings("NullAway.Init")
    private transient String subreddit;

    public Usernotes() {
        // for GSON
    }

    public Usernotes(
            int schema,
            UsernotesConstants constants,
            Map<String, List<Usernote>> notes,
            String subreddit) {
        this.schema = schema;
        this.constants = constants;
        this.notes = notes;
        this.subreddit = subreddit;
    }

    /**
     * Add a usernote to this usernotes object
     *
     * <p>Make sure to persist back to the wiki after doing this!
     *
     * @param user User to add note for
     * @param noteText Note text
     * @param link Toolbox link formatted link
     * @param time Time in ms
     * @param mod Mod making the note
     * @param type optional warning type
     */
    public void createNote(
            String user,
            String noteText,
            String link,
            long time,
            String mod,
            @Nullable String type) {
        boolean modExists = false;
        int modIndex = -1;
        boolean typeExists = false;
        int typeIndex = -1;

        for (int i = 0; i < constants.getMods().length; i++) {
            if (constants.getMods()[i].equals(mod)) {
                modExists = true;
                modIndex = i;
                break;
            }
        }
        // Read the array once: an untyped note is a null entry, and repeating getTypes() would
        // leave the guard and the dereference reading two different expressions.
        final @Nullable String[] existingTypes = constants.getTypes();
        for (int i = 0; i < existingTypes.length; i++) {
            final String existing = existingTypes[i];
            if ((existing == null && type == null)
                    || (existing != null && existing.equals(type))) {
                typeExists = true;
                typeIndex = i;
                break;
            }
        }

        if (!modExists) {
            modIndex = constants.addMod(mod);
        }
        if (!typeExists) {
            typeIndex = constants.addType(type);
        }

        Usernote note = new Usernote(noteText, link, time / 1000, modIndex, typeIndex);

        final List<Usernote> existing = notes.get(user);
        if (existing != null) {
            existing.add(0, note);
        } else {
            List<Usernote> newList = new ArrayList<>();
            newList.add(note);
            notes.put(user, newList);
        }
    }

    /**
     * Remove a usernote for a user
     *
     * <p>Make sure to persist back to the wiki after doing this!
     *
     * @param user User to remove note from
     * @param note Note to remove
     */
    public void removeNote(String user, Usernote note) {
        final List<Usernote> existing = notes.get(user);
        if (existing != null) {
            existing.remove(note);
            if (existing.isEmpty()) { // if we just removed the last note, remove the user too
                notes.remove(user);
            }
        }
    }

    public int getSchema() {
        return schema;
    }

    /**
     * Whether GSON produced something the rest of this class can be used on: schema 6 with both
     * deserialized parts present.
     *
     * <p>Schema alone is not enough. A schema-6 page whose blob does not inflate — truncated, or
     * not zlib at all — deserializes to a null {@code notes}, because {@link
     * BlobDeserializer#blobToJson} reports that by returning null; a page that omits "constants"
     * or "blob" outright leaves the field untouched. Either way every accessor here dereferences
     * it, so {@link Toolbox} keeps only a usable one.
     */
    public boolean isUsable() {
        return schema == 6 && constants != null && constants.isComplete() && notes != null;
    }

    public UsernotesConstants getConstants() {
        return constants;
    }

    public Map<String, List<Usernote>> getNotes() {
        return notes;
    }

    /**
     * Get the list of usernotes for a user
     *
     * @param user User to get notes for
     * @return List of usernotes, or null when the user has none
     */
    @Nullable
    public List<Usernote> getNotesForUser(String user) {
        return notes.get(user);
    }

    /**
     * Gets the display text for a user using same logic as toolbox
     *
     * @param user User
     * @return (Shortened) usernote text (plus count if additional notes)
     */
    public String getDisplayNoteForUser(String user) {
        final List<Usernote> userNotes = getNotesForUser(user);
        if (userNotes == null || userNotes.isEmpty()) {
            return "";
        }
        String noteText = StringUtils.abbreviate(userNotes.get(0).getNoteText(), "…", 20);
        if (userNotes.size() > 1) {
            noteText += " (+" + (userNotes.size() - 1) + ")";
        }
        return noteText;
    }

    /**
     * Get the color for the primary displayed usernote of a user
     *
     * @param user User
     * @return A color int
     */
    @ColorInt
    public int getDisplayColorForUser(String user) {
        final List<Usernote> userNotes = getNotesForUser(user);
        if (userNotes != null && !userNotes.isEmpty()) {
            return getColorFromWarningIndex(userNotes.get(0).getWarning());
        } else {
            return 0xFF808080;
        }
    }

    /**
     * Get a color from a warning index
     *
     * @param index Index
     * @return A color int
     */
    @ColorInt
    public int getColorFromWarningIndex(int index) {
        String color = "#808080";

        ToolboxConfig config = Toolbox.getConfig(subreddit);
        final String typeName = constants.getTypeName(index);
        if (config != null) { // Subs can have usernotes without a toolbox config
            color = config.getUsernoteColor(typeName);
        } else {
            Map<String, String> defaults = Toolbox.DEFAULT_USERNOTE_TYPES.get(typeName);

            if (defaults != null) {
                String defaultColor = defaults.get("color");

                if (defaultColor != null) {
                    color = defaultColor;
                }
            }
        }

        try {
            return ColorParser.parseCssColor(color);
        } catch (IllegalArgumentException e) {
            return 0xFF808080;
        }
    }

    /**
     * Get the warning text for a usernote from the index in the warnings array
     *
     * @param index Index in warnings array
     * @param bracket Whether to wrap the returned result in brackets
     * @return Warning text
     */
    public String getWarningTextFromWarningIndex(int index, boolean bracket) {
        StringBuilder result = new StringBuilder(bracket ? "[" : "");
        final ToolboxConfig config = Toolbox.getConfig(subreddit);
        final String typeName = constants.getTypeName(index);
        if (config != null) {
            if (typeName != null) {
                String text = config.getUsernoteText(typeName);
                if (!text.isEmpty()) {
                    result.append(text);
                } else {
                    return "";
                }
            } else {
                return "";
            }
        } else {
            final Map<String, String> defaults = Toolbox.DEFAULT_USERNOTE_TYPES.get(typeName);
            if (defaults != null) {
                String def = defaults.get("text");
                if (def != null) {
                    result.append(def);
                } else {
                    return "";
                }
            } else {
                return "";
            }
        }
        result.append(bracket ? "]" : "");
        return result.toString();
    }

    public String getModNameFromModIndex(int index) {
        return constants.getModName(index);
    }

    /**
     * Sets the Usernotes object's subreddit
     *
     * @param subreddit
     */
    public void setSubreddit(String subreddit) {
        this.subreddit = subreddit;
    }

    /** Allows GSON to deserialize the "blob" into an object */
    public static class BlobDeserializer implements JsonDeserializer<Map<String, List<Usernote>>> {
        @Override
        @Nullable
        public Map<String, List<Usernote>> deserialize(
                JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {

            // Every step below tests the node's type rather than assuming it. getAsString() on a
            // non-string and getAsJsonObject() on anything but an object throw
            // UnsupportedOperationException and IllegalStateException, neither of which is a
            // JsonParseException, so a malformed page escaped gson.fromJson uncaught rather than
            // reaching the callers' error paths. Returning null reports it as an unusable page,
            // which is what Usernotes.isUsable() then reads.
            if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
                return null;
            }
            String decodedBlob = blobToJson(json.getAsString());
            if (decodedBlob == null) {
                return null;
            }
            JsonElement jsonBlob = JsonParser.parseString(decodedBlob);
            if (!jsonBlob.isJsonObject()) {
                return null;
            }
            Map<String, List<Usernote>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

            for (Map.Entry<String, JsonElement> userAndNotes :
                    jsonBlob.getAsJsonObject().entrySet()) {
                if (!userAndNotes.getValue().isJsonObject()) {
                    continue;
                }
                // A user entry with no usable "ns" carries no notes; the chain this replaced
                // dereferenced the null JsonObject.get hands back for an absent key, and
                // getAsJsonArray() throws on the JsonNull it hands back for an explicit null.
                final JsonElement ns = userAndNotes.getValue().getAsJsonObject().get("ns");
                List<Usernote> notesList = new ArrayList<>();
                if (ns != null && ns.isJsonArray()) {
                    for (JsonElement notesArray : ns.getAsJsonArray()) {
                        notesList.add(context.deserialize(notesArray, Usernote.class));
                    }
                }
                result.put(userAndNotes.getKey().toLowerCase(Locale.ENGLISH), notesList);
            }

            return result;
        }

        /**
         * Converts a base64 encoded and zlib compressed blob into a String.
         *
         * @param blob Blob to convert to string
         * @return Decoded blob
         */
        @Nullable
        public static String blobToJson(String blob) {
            // Adapted from https://stackoverflow.com/a/33022277
            try {
                // Inside the try: Base64.decode throws IllegalArgumentException on a blob that is
                // not base64 at all, which escaped gson.fromJson the same way an inflate failure
                // would have without the catch below.
                final byte[] decoded = Base64.decode(blob, Base64.DEFAULT);
                ByteArrayInputStream input = new ByteArrayInputStream(decoded);
                InflaterInputStream inflater = new InflaterInputStream(input);

                StringBuilder result = new StringBuilder();
                byte[] buf = new byte[5];
                int rlen;
                while ((rlen = inflater.read(buf)) != -1) {
                    result.append(new String(Arrays.copyOf(buf, rlen)));
                }
                return result.toString();
            } catch (IOException | IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** Allows GSON to serialize the usernotes map into a blob */
    public static class BlobSerializer implements JsonSerializer<Map<String, List<Usernote>>> {
        @Override
        public JsonElement serialize(
                Map<String, List<Usernote>> src, Type srcType, JsonSerializationContext context) {
            Map<String, Map<String, List<Usernote>>> notes = new HashMap<>();
            for (Map.Entry<String, List<Usernote>> entry : src.entrySet()) {
                Map<String, List<Usernote>> newNotes = new HashMap<>();
                newNotes.put("ns", entry.getValue());
                notes.put(entry.getKey(), newNotes);
            }
            String encodedBlob = jsonToBlob(context.serialize(notes).toString());
            return context.serialize(encodedBlob);
        }

        /**
         * Converts a JSON string into a zlib compressed and base64 encoded blog
         *
         * @param json JSON to turn into blob
         * @return Blob
         */
        @Nullable
        public static String jsonToBlob(String json) {
            // Adapted from https://stackoverflow.com/a/33022277
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                DeflaterOutputStream deflater = new DeflaterOutputStream(output);
                deflater.write(json.getBytes());
                deflater.flush();
                deflater.close();

                return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            } catch (IOException e) {
                return null;
            }
        }
    }

    /** Class describing the "constants" field of a usernotes config */
    public static class UsernotesConstants {
        // Both are set either by the two-argument constructor or by GSON, which
        // Usernotes.isUsable() then checks arrived before the page is kept.
        @SerializedName("users")
        @SuppressWarnings("NullAway.Init")
        private String[] mods; // String array of mods. Usernote mod is index in this

        // An untyped note is stored as a null entry here, which createNote matches on.
        @SerializedName("warnings")
        @SuppressWarnings("NullAway.Init")
        private @Nullable String[] types; // String array of used type names corresponding to types in the

        // config/defaults. Usernote warning is index in this

        public UsernotesConstants() {
            // for GSON
        }

        public UsernotesConstants(String[] mods, @Nullable String[] types) {
            this.mods = mods;
            this.types = types;
        }

        /**
         * Whether GSON filled in both arrays. A page that omits "users" or "warnings", or sets
         * either to JSON null, leaves the field null for {@link #getModName} and {@link
         * #getTypeName} to index into. See {@link Usernotes#isUsable()}.
         */
        boolean isComplete() {
            return mods != null && types != null;
        }

        public String[] getMods() {
            return mods;
        }

        /**
         * Add a new user to the mods array
         *
         * <p>Does not check for duplicates!
         *
         * @param user User to add
         * @return Index of added mod
         */
        public int addMod(String user) {
            String[] newMods = new String[mods.length + 1];
            System.arraycopy(mods, 0, newMods, 0, mods.length);
            newMods[newMods.length - 1] = user;
            mods = newMods;
            return newMods.length - 1;
        }

        public @Nullable String[] getTypes() {
            return types;
        }

        /**
         * Adds a type to the warnings array
         *
         * <p>Does not check for duplicates!
         *
         * @param type Type to add
         * @return Index of added type
         */
        public int addType(@Nullable String type) {
            @Nullable String[] newTypes = new @Nullable String[types.length + 1];
            System.arraycopy(types, 0, newTypes, 0, types.length);
            newTypes[newTypes.length - 1] = type;
            types = newTypes;
            return newTypes.length - 1;
        }

        /**
         * @return the type name at {@code index}, or null — an untyped note is stored as a null
         *     entry in the warnings array, which {@link #createNote} matches on.
         */
        @Nullable
        public String getTypeName(int index) {
            return types[index];
        }

        public String getModName(int index) {
            return mods[index];
        }
    }
}
