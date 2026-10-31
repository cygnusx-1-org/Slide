package me.edgan.redditslide.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.CommentSort;
import net.dean.jraw.models.MoreChildren;

/** JSON shaped like Reddit's comment listings. */
final class MoreCommentsFixtures {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MoreCommentsFixtures() {}

    /** A {@code t1} thing, with its replies (other things) under it. */
    static ObjectNode comment(String id, JsonNode... replies) {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("id", id);
        data.put("name", "t1_" + id);
        data.put("parent_id", "t3_post");
        data.put("author", "author_" + id);
        data.put("body", "body " + id);
        // Focused listings report a relative depth; nothing may trust it.
        data.put("depth", 0);
        if (replies.length == 0) {
            data.put("replies", "");
        } else {
            ObjectNode listing = data.putObject("replies");
            listing.put("kind", "Listing");
            ArrayNode children = listing.putObject("data").putArray("children");
            for (JsonNode reply : replies) {
                children.add(reply);
            }
        }
        ObjectNode thing = MAPPER.createObjectNode();
        thing.put("kind", "t1");
        thing.set("data", data);
        return thing;
    }

    /** A {@code more} thing. */
    static ObjectNode more(int count, String... ids) {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("count", count);
        data.put("id", ids.length == 0 ? "_" : ids[0]);
        data.put("name", "t1_" + (ids.length == 0 ? "_" : ids[0]));
        data.put("parent_id", "t3_post");
        ArrayNode children = data.putArray("children");
        for (String id : ids) {
            children.add(id);
        }
        ObjectNode thing = MAPPER.createObjectNode();
        thing.put("kind", "more");
        thing.set("data", data);
        return thing;
    }

    /** The two-element array of a focused listing, with these things at the top level. */
    static JsonNode listing(JsonNode... things) {
        ArrayNode root = MAPPER.createArrayNode();
        root.addObject().putObject("data").putArray("children");
        ArrayNode children = root.addObject().putObject("data").putArray("children");
        for (JsonNode thing : things) {
            children.add(thing);
        }
        return root;
    }

    static FocusedListing ok(JsonNode json) {
        return new FocusedListing(200, json, "9993.0", "300");
    }

    static List<String> ids(String prefix, int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(prefix + i);
        }
        return ids;
    }

    /** A thread with these top-level things, the way a fetch of it would be parsed. */
    static CommentNode thread(MoreChildren rootMore, JsonNode... topLevel) {
        List<Comment> comments = new ArrayList<>();
        for (JsonNode thing : topLevel) {
            comments.add(new Comment(thing.get("data")));
        }
        return new CommentNode("t3_post", comments, rootMore, CommentSort.CONFIDENCE);
    }

    static MoreChildren moreChildren(int count, String... ids) {
        return new MoreChildren(more(count, ids).get("data"));
    }
}
