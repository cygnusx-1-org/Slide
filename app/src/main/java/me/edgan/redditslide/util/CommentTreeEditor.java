package me.edgan.redditslide.util;

import android.util.Log;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.CommentSort;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.MoreChildren;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Grows a JRAW comment tree from comments fetched outside {@link CommentNode#loadMoreComments}.
 *
 * <p>CommentNode can only add children through its own morechildren and /api/info calls: its child
 * constructor and its {@code moreChildren} field are private, hence the reflection.
 * CommentTreeEditorTest fails if a JRAW bump renames either.
 *
 * <p>A node's depth is its parent's plus one, not the {@code depth} in its JSON, and nothing reads
 * that JSON value. So the relative depth a focused listing reports needs no rebasing here.
 */
@NullMarked
public final class CommentTreeEditor {
    private CommentTreeEditor() {}

    /**
     * Builds a node for each comment and its subtree, dropping any that can't be parsed so one odd
     * comment can't block the rest. Only points the new nodes at {@code parent}: adding them to its
     * children is left to the caller, on the main thread.
     */
    public static List<CommentNode> buildChildren(
            CommentNode parent, List<JsonNode> comments, CommentSort sort) {
        List<CommentNode> built = new ArrayList<>(comments.size());
        for (JsonNode data : comments) {
            try {
                built.add(newChild(parent, data, sort));
            } catch (Exception e) {
                Log.w(LogUtil.getTag(), "Dropping unparseable comment " + data.path("id"), e);
            }
        }
        return built;
    }

    private static CommentNode newChild(CommentNode parent, JsonNode data, CommentSort sort)
            throws ReflectiveOperationException {
        Constructor<CommentNode> constructor =
                CommentNode.class.getDeclaredConstructor(
                        String.class,
                        CommentNode.class,
                        Comment.class,
                        MoreChildren.class,
                        CommentSort.class,
                        int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                parent.getSubmissionName(),
                parent,
                new Comment(data),
                moreOfReplies(data),
                sort,
                parent.getDepth() + 1);
    }

    private static @Nullable MoreChildren moreOfReplies(JsonNode data) {
        JsonNode replies = data.path("replies");
        if (!replies.isObject()) {
            return null;
        }
        return new Listing<>(replies.get("data"), Comment.class).getMoreChildren();
    }

    /** Replaces what {@code node} still has to load, or clears it when {@code more} is null. */
    public static void setMore(CommentNode node, @Nullable MoreChildren more) {
        try {
            Field field = CommentNode.class.getDeclaredField("moreChildren");
            field.setAccessible(true);
            field.set(node, more);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("JRAW's CommentNode no longer matches", e);
        }
    }

    /** A {@code more} shaped like Reddit's, for what's left after a partial load. */
    public static MoreChildren newMore(List<String> ids, int count, String parentFullName) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("count", count);
        data.put("id", ids.get(0));
        data.put("name", "t1_" + ids.get(0));
        data.put("parent_id", parentFullName);
        ArrayNode children = data.putArray("children");
        for (String id : ids) {
            children.add(id);
        }
        return new MoreChildren(data);
    }

    /**
     * The replies behind {@code node}'s {@code more}. Reddit's count is only an estimate, and can
     * read lower than the ids it came with. "Continue this thread" has no ids and counts as none.
     */
    public static int pendingReplies(CommentNode node) {
        MoreChildren more = node.getMoreChildren();
        if (more == null) {
            return 0;
        }
        int ids = more.getChildrenIds().size();
        if (ids == 0) {
            return 0;
        }
        Integer count = more.getCount();
        return count == null ? ids : Math.max(count, ids);
    }

    /**
     * Every reply under {@code node}, loaded or not. The tree has no node for a "load more" row, so
     * that placeholder can't count as a reply.
     *
     * @param online whether a {@code more} can be loaded at all, and so whether it shows a row
     */
    public static int totalReplies(CommentNode node, boolean online) {
        int total = -1; // walkTree includes node itself
        for (CommentNode n : node.walkTree()) {
            total++;
            if (online) {
                total += pendingReplies(n);
            }
        }
        return total;
    }
}
