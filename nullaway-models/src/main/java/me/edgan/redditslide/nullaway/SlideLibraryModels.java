package me.edgan.redditslide.nullaway;

import static com.uber.nullaway.LibraryModels.MethodRef.methodRef;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.uber.nullaway.LibraryModels;

/**
 * Teaches NullAway the nullability of dependencies that ship none of their own.
 *
 * <p>Under {@code OnlyNullMarked} NullAway treats every method of an unannotated dependency as
 * returning non-null. This is how that gets corrected — but only for a library whose source we
 * cannot change. For one we fork, the annotations belong in the fork, where they are checkable
 * against the implementation and cannot silently stop matching when a method is renamed.
 *
 * <p><b>Scope, measured rather than assumed</b> (NULLAWAY.md phase 8). A model reaches an ordinary
 * dependency jar but <b>not</b> {@code android.*} or {@code androidx.*}: with one model declaring
 * all three {@code @Nullable}, a probe dereferencing a JRAW getter failed the compile while
 * {@code Fragment.getActivity()} and {@code View.findViewById()} passed. Those call sites cannot be
 * reached from here, nor by {@code AcknowledgeRestrictiveAnnotations}.
 *
 * <p>What this carries is Jackson and GSON, which return null for an absent key and which nobody
 * here can change. Apache Commons was measured alongside them in phase 8 and is modelled through
 * {@link #nullImpliesNullParameters()} rather than here, because its nulls are propagated arguments
 * rather than absent data.
 *
 * <p><b>JRAW is no longer modelled here.</b> {@code com.github.edgan:JRAW} is our own fork, and
 * NULLAWAY.md phase 10 moved those contracts into JRAW's own source, where they can be checked
 * against the implementation — which is what caught nine getters the bytecode derivation had wrong.
 * The 143 staged entries this class used to carry are gone with it.
 *
 * <p><b>Universal Image Loader is no longer modelled here either.</b> Its {@code onLoadingComplete}
 * bitmap is annotated in {@code com.github.edgan:Android-Universal-Image-Loader} for the same
 * reason, and the two entries this class used to carry for it are gone. Worth recording from that
 * exercise: a {@code @Nullable} <i>parameter</i> on unannotated code is honoured through {@code
 * AcknowledgeRestrictiveAnnotations} for override checking, so the fork does not need {@code
 * @NullMarked} — which is just as well, since marking those types would have declared every
 * unmarked parameter non-null, and UIL passes a null {@code imageUri} and {@code view} too.
 *
 * <p>Discovered by {@link java.util.ServiceLoader}; see {@code
 * src/main/resources/META-INF/services/com.uber.nullaway.LibraryModels}.
 */
public final class SlideLibraryModels implements LibraryModels {

    private static final String COMMONS_ESCAPE = "org.apache.commons.text.StringEscapeUtils";
    private static final String COMMONS_STRINGS = "org.apache.commons.lang3.StringUtils";

    /**
     * Absent-key reads. Both are documented to return null when the key is not present, and both
     * are third-party code we cannot annotate at the source — the case a library model is for.
     *
     * <p>The overriding declarations are listed alongside the interface ones deliberately: NullAway
     * matches the symbol javac resolved at the call site, so a receiver declared {@code ObjectNode}
     * does not match a model on {@code JsonNode}.
     */
    private static final ImmutableSet<MethodRef> NULLABLE_RETURNS =
            ImmutableSet.<MethodRef>builder()
                    // Jackson: JsonNode.get returns null for an absent field or an out-of-range
                    // index, as distinct from path(), which returns a MissingNode.
                    .add(methodRef("com.fasterxml.jackson.databind.JsonNode", "get(java.lang.String)"))
                    .add(methodRef("com.fasterxml.jackson.databind.JsonNode", "get(int)"))
                    .add(methodRef("com.fasterxml.jackson.databind.node.ObjectNode", "get(java.lang.String)"))
                    .add(methodRef("com.fasterxml.jackson.databind.node.ObjectNode", "get(int)"))
                    .add(methodRef("com.fasterxml.jackson.databind.node.ArrayNode", "get(java.lang.String)"))
                    .add(methodRef("com.fasterxml.jackson.databind.node.ArrayNode", "get(int)"))
                    // GSON: JsonObject.get returns null for an absent member. A member that is
                    // present and JSON null comes back as JsonNull, not as Java null.
                    .add(methodRef("com.google.gson.JsonObject", "get(java.lang.String)"))
                    .add(methodRef("com.google.gson.JsonObject", "getAsJsonObject(java.lang.String)"))
                    .add(methodRef("com.google.gson.JsonObject", "getAsJsonArray(java.lang.String)"))
                    .add(methodRef("com.google.gson.JsonObject", "getAsJsonPrimitive(java.lang.String)"))
                    .build();

    @Override
    public ImmutableSet<MethodRef> nullableReturns() {
        return NULLABLE_RETURNS;
    }

    @Override
    public ImmutableSet<MethodRef> nonNullReturns() {
        return ImmutableSet.of();
    }

    @Override
    public ImmutableSetMultimap<MethodRef, Integer> failIfNullParameters() {
        return ImmutableSetMultimap.of();
    }

    @Override
    public ImmutableSetMultimap<MethodRef, Integer> explicitlyNullableParameters() {
        return ImmutableSetMultimap.of();
    }

    @Override
    public ImmutableSetMultimap<MethodRef, Integer> nonNullParameters() {
        return ImmutableSetMultimap.of();
    }

    @Override
    public ImmutableSetMultimap<MethodRef, Integer> nullImpliesTrueParameters() {
        return ImmutableSetMultimap.of();
    }

    @Override
    public ImmutableSetMultimap<MethodRef, Integer> nullImpliesFalseParameters() {
        return ImmutableSetMultimap.of();
    }

    /**
     * Apache Commons, which is unannotated and whose string helpers pass a null argument straight
     * through to the return. NULLAWAY.md phase 4 was bitten by exactly this: {@code
     * StringUtils.abbreviate} took a GSON-nulled note text and handed it back, so {@code
     * Usernotes.getDisplayNoteForUser} returned null out of a non-null declaration and the usernote
     * chip rendered the literal text "null".
     *
     * <p>These are null-in/null-out rather than absent-key reads, so they belong here and not in
     * {@code nullableReturns()} — modelling them as unconditionally nullable would push a dead
     * guard onto every call site that passes a non-null argument, which is most of them.
     */
    private static final ImmutableSetMultimap<MethodRef, Integer> NULL_IMPLIES_NULL_PARAMETERS =
            ImmutableSetMultimap.<MethodRef, Integer>builder()
                    // commons-text: every one of these is CharSequenceTranslator.translate, which
                    // opens with `if (input == null) return null;`.
                    .put(methodRef(COMMONS_ESCAPE, "escapeHtml4(java.lang.String)"), 0)
                    .put(methodRef(COMMONS_ESCAPE, "unescapeHtml4(java.lang.String)"), 0)
                    .put(methodRef(COMMONS_ESCAPE, "unescapeJava(java.lang.String)"), 0)
                    .put(methodRef(COMMONS_ESCAPE, "unescapeJson(java.lang.String)"), 0)
                    // commons-lang3
                    .put(methodRef(COMMONS_STRINGS, "capitalize(java.lang.String)"), 0)
                    .put(methodRef(COMMONS_STRINGS, "uncapitalize(java.lang.String)"), 0)
                    .put(methodRef(COMMONS_STRINGS, "abbreviate(java.lang.String,int)"), 0)
                    .put(methodRef(COMMONS_STRINGS, "abbreviate(java.lang.String,int,int)"), 0)
                    .put(
                            methodRef(
                                    COMMONS_STRINGS,
                                    "abbreviate(java.lang.String,java.lang.String,int)"),
                            0)
                    .put(methodRef(COMMONS_STRINGS, "trim(java.lang.String)"), 0)
                    .put(methodRef(COMMONS_STRINGS, "strip(java.lang.String)"), 0)
                    .put(methodRef(COMMONS_STRINGS, "lowerCase(java.lang.String)"), 0)
                    .put(methodRef(COMMONS_STRINGS, "upperCase(java.lang.String)"), 0)
                    .build();

    @Override
    public ImmutableSetMultimap<MethodRef, Integer> nullImpliesNullParameters() {
        return NULL_IMPLIES_NULL_PARAMETERS;
    }

    @Override
    public ImmutableSetMultimap<MethodRef, Integer> castToNonNullMethods() {
        return ImmutableSetMultimap.of();
    }
}
