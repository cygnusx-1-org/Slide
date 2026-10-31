package me.edgan.redditslide.Adapters;

import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.MoreChildren;

/** Created by carlo_000 on 1/23/2016. */
public class MoreChildItem extends CommentObject {
    public MoreChildren children;

    /** Outlives the row: a rebind mid-load must neither offer the tap again nor lose the spinner. */
    public boolean loading;

    @Override
    public boolean isComment() {
        return false;
    }

    public MoreChildItem(CommentNode node, MoreChildren children) {
        comment = node;
        this.children = children;
        this.name = comment.getComment().getFullName() + "more";
    }
}
