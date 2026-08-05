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
 * <p><b>The JRAW entries below are staged, not live.</b> {@code com.github.edgan:JRAW} is our own
 * fork, so NULLAWAY.md phase 10 moves those 143 contracts into JRAW proper rather than modelling
 * them from outside; delete the set when that lands. Switching them on is that phase's 410-finding
 * burn-down and not a side effect of this one, so {@link #nullableReturns()} does not include them
 * — adding {@code .addAll(JRAW_NULLABLE_RETURNS)} there reproduces the measurement. They are not
 * hand-picked and must not be edited by hand — {@code scripts/derive_jraw_nullable.py} generates
 * them, reporting every zero-argument getter in {@code net.dean.jraw} whose bytecode either returns
 * {@code JsonModel.data(String)} straight through — {@code data(String)} being {@code node.has(key)
 * ? ... : null} — or contains an explicit {@code return null}. That same list is the worklist for
 * phase 10; re-run the script when JRAW is bumped.
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

    /**
     * Staged for NULLAWAY.md phase 10, and deliberately not returned by {@link #nullableReturns()}.
     * See the class javadoc.
     */
    private static final ImmutableSet<MethodRef> JRAW_NULLABLE_RETURNS =
            ImmutableSet.<MethodRef>builder()
                    // AuthenticationManager
                    .add(methodRef("net.dean.jraw.auth.AuthenticationManager", "getUsername()"))
                    // OAuthData
                    .add(methodRef("net.dean.jraw.http.oauth.OAuthData", "getAccessToken()"))
                    .add(methodRef("net.dean.jraw.http.oauth.OAuthData", "getExpirationDate()"))
                    .add(methodRef("net.dean.jraw.http.oauth.OAuthData", "getRefreshToken()"))
                    .add(methodRef("net.dean.jraw.http.oauth.OAuthData", "getScopes()"))
                    .add(methodRef("net.dean.jraw.http.oauth.OAuthData", "getTokenType()"))
                    // Account
                    .add(methodRef("net.dean.jraw.models.Account", "getCommentKarma()"))
                    .add(methodRef("net.dean.jraw.models.Account", "getLinkKarma()"))
                    // Comment
                    .add(methodRef("net.dean.jraw.models.Comment", "getApprovedBy()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getAuthor()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getAuthorFlair()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getBannedBy()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getBody()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getEditDate()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getLocalizedReportCount()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getParentId()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getReportCount()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getSubmissionAuthor()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getSubmissionId()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getSubmissionTitle()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getSubredditId()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getSubredditName()"))
                    .add(methodRef("net.dean.jraw.models.Comment", "getUrl()"))
                    // CommentMessage
                    .add(methodRef("net.dean.jraw.models.CommentMessage", "getLinkTitle()"))
                    // LiveThread
                    .add(methodRef("net.dean.jraw.models.LiveThread", "getDescription()"))
                    .add(methodRef("net.dean.jraw.models.LiveThread", "getId()"))
                    .add(methodRef("net.dean.jraw.models.LiveThread", "getLocalizedViewerCount()"))
                    .add(methodRef("net.dean.jraw.models.LiveThread", "getResources()"))
                    .add(methodRef("net.dean.jraw.models.LiveThread", "getState()"))
                    .add(methodRef("net.dean.jraw.models.LiveThread", "getTitle()"))
                    .add(methodRef("net.dean.jraw.models.LiveThread", "getViewerCount()"))
                    .add(methodRef("net.dean.jraw.models.LiveThread", "getWebsocketUrl()"))
                    // LiveUpdate
                    .add(methodRef("net.dean.jraw.models.LiveUpdate", "getAuthor()"))
                    .add(methodRef("net.dean.jraw.models.LiveUpdate", "getBody()"))
                    // LoggedInAccount
                    .add(methodRef("net.dean.jraw.models.LoggedInAccount", "getCreddits()"))
                    .add(methodRef("net.dean.jraw.models.LoggedInAccount", "getInboxCount()"))
                    .add(methodRef("net.dean.jraw.models.LoggedInAccount", "getLocalizedInboxCount()"))
                    // Message
                    .add(methodRef("net.dean.jraw.models.Message", "getAuthor()"))
                    .add(methodRef("net.dean.jraw.models.Message", "getBody()"))
                    .add(methodRef("net.dean.jraw.models.Message", "getFirstMessage()"))
                    .add(methodRef("net.dean.jraw.models.Message", "getParentId()"))
                    .add(methodRef("net.dean.jraw.models.Message", "getSubject()"))
                    .add(methodRef("net.dean.jraw.models.Message", "getSubreddit()"))
                    // ModAction
                    .add(methodRef("net.dean.jraw.models.ModAction", "getAction()"))
                    .add(methodRef("net.dean.jraw.models.ModAction", "getDescription()"))
                    .add(methodRef("net.dean.jraw.models.ModAction", "getDetails()"))
                    .add(methodRef("net.dean.jraw.models.ModAction", "getModerator()"))
                    .add(methodRef("net.dean.jraw.models.ModAction", "getModeratorId()"))
                    .add(methodRef("net.dean.jraw.models.ModAction", "getSubreddit()"))
                    .add(methodRef("net.dean.jraw.models.ModAction", "getSubredditId()"))
                    .add(methodRef("net.dean.jraw.models.ModAction", "getTargetAuthor()"))
                    .add(methodRef("net.dean.jraw.models.ModAction", "getTargetFullName()"))
                    .add(methodRef("net.dean.jraw.models.ModAction", "getTargetPermalink()"))
                    // MoreChildren
                    .add(methodRef("net.dean.jraw.models.MoreChildren", "getCount()"))
                    .add(methodRef("net.dean.jraw.models.MoreChildren", "getLocalizedCount()"))
                    .add(methodRef("net.dean.jraw.models.MoreChildren", "getParentId()"))
                    // MultiReddit
                    .add(methodRef("net.dean.jraw.models.MultiReddit", "getDescription()"))
                    .add(methodRef("net.dean.jraw.models.MultiReddit", "getDisplayName()"))
                    .add(methodRef("net.dean.jraw.models.MultiReddit", "getFullName()"))
                    .add(methodRef("net.dean.jraw.models.MultiReddit", "getIconUrl()"))
                    .add(methodRef("net.dean.jraw.models.MultiReddit", "getKeyColor()"))
                    .add(methodRef("net.dean.jraw.models.MultiReddit", "getOrigin()"))
                    .add(methodRef("net.dean.jraw.models.MultiReddit", "getVisibility()"))
                    .add(methodRef("net.dean.jraw.models.MultiReddit", "getWeightingScheme()"))
                    // MultiSubreddit
                    .add(methodRef("net.dean.jraw.models.MultiSubreddit", "getDisplayName()"))
                    .add(methodRef("net.dean.jraw.models.MultiSubreddit", "getFullName()"))
                    .add(methodRef("net.dean.jraw.models.MultiSubreddit", "getHeaderImage()"))
                    .add(methodRef("net.dean.jraw.models.MultiSubreddit", "getIconImage()"))
                    .add(methodRef("net.dean.jraw.models.MultiSubreddit", "getKeyColor()"))
                    // OAuthScope
                    .add(methodRef("net.dean.jraw.models.OAuthScope", "getDescription()"))
                    // OEmbed
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getAuthorName()"))
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getAuthorUrl()"))
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getCacheAge()"))
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getHeight()"))
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getHtml()"))
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getMediaType()"))
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getProviderName()"))
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getProviderUrl()"))
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getThumbnail()"))
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getTitle()"))
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getUrl()"))
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getVersion()"))
                    .add(methodRef("net.dean.jraw.models.OEmbed", "getWidth()"))
                    // PublicContribution
                    .add(methodRef("net.dean.jraw.models.PublicContribution", "getLocalizedScore()"))
                    .add(methodRef("net.dean.jraw.models.PublicContribution", "getModeratorReports()"))
                    .add(methodRef("net.dean.jraw.models.PublicContribution", "getRemovalReason()"))
                    .add(methodRef("net.dean.jraw.models.PublicContribution", "getUserReports()"))
                    // Submission
                    .add(methodRef("net.dean.jraw.models.Submission", "getApprovedBy()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getAuthor()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getAuthorFlair()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getBannedBy()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getCommentCount()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getDomain()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getEdited()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getLocalizedCommentCount()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getLocalizedScore()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getOEmbedMedia()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getPermalink()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getPostHint()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getSelftext()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getSubmissionFlair()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getSubredditId()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getSubredditName()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getSuggestedSort()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getThumbnail()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getThumbnails()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getTitle()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getUpvoteRatio()"))
                    .add(methodRef("net.dean.jraw.models.Submission", "getUrl()"))
                    // Subreddit
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getAccountsActive()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getBannerImage()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getCommentScoreHideDuration()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getDisplayName()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getHeaderImage()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getHeaderTitle()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getLocalizedAccountsActive()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getLocalizedSubscriberCount()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getPublicDescription()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getRelativeLocation()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getSidebar()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getSubmitLinkLabel()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getSubmitTextLabel()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getSubredditType()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getSubscriberCount()"))
                    .add(methodRef("net.dean.jraw.models.Subreddit", "getTitle()"))
                    // SubredditRule
                    .add(methodRef("net.dean.jraw.models.SubredditRule", "getKind()"))
                    // Thing
                    .add(methodRef("net.dean.jraw.models.Thing", "getFullName()"))
                    .add(methodRef("net.dean.jraw.models.Thing", "getId()"))
                    // Thumbnails
                    .add(methodRef("net.dean.jraw.models.Thumbnails", "getId()"))
                    // Trophy
                    .add(methodRef("net.dean.jraw.models.Trophy", "getAboutUrl()"))
                    .add(methodRef("net.dean.jraw.models.Trophy", "getDescription()"))
                    .add(methodRef("net.dean.jraw.models.Trophy", "getIcon()"))
                    .add(methodRef("net.dean.jraw.models.Trophy", "getIconSmall()"))
                    .add(methodRef("net.dean.jraw.models.Trophy", "getTrophyId()"))
                    // UserRecord
                    .add(methodRef("net.dean.jraw.models.UserRecord", "getDate()"))
                    .add(methodRef("net.dean.jraw.models.UserRecord", "getModPermissions()"))
                    .add(methodRef("net.dean.jraw.models.UserRecord", "getNote()"))
                    // WikiPage
                    .add(methodRef("net.dean.jraw.models.WikiPage", "getContent()"))
                    .add(methodRef("net.dean.jraw.models.WikiPage", "getCurrentRevisionAuthor()"))
                    .add(methodRef("net.dean.jraw.models.WikiPage", "getRevisionDate()"))
                    // WikiPageSettings
                    .add(methodRef("net.dean.jraw.models.WikiPageSettings", "getPermissionLevel()"))
                    // Paginator
                    .add(methodRef("net.dean.jraw.paginators.Paginator", "getSortingString()"))
                    // UserProfilePaginator
                    .add(methodRef("net.dean.jraw.paginators.UserProfilePaginator", "getSortingString()"))
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
