package me.edgan.redditslide;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import me.edgan.redditslide.Adapters.CommentObject;
import net.dean.jraw.models.PrivateMessage;

/** Created by ccrama on 9/19/2015. */
public class DataShare {
    // Each field is written by one screen and read by another, so it is null until the writer has
    // run — CommentSearch already null-checks sharedComments for exactly that reason.
    @Nullable public static PrivateMessage sharedMessage;
    @Nullable public static ArrayList<CommentObject> sharedComments;
}
