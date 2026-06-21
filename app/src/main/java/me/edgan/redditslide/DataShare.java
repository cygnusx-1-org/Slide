package me.edgan.redditslide;

import java.util.ArrayList;
import me.edgan.redditslide.Adapters.CommentObject;
import net.dean.jraw.models.PrivateMessage;
import net.dean.jraw.models.Submission;

/** Created by ccrama on 9/19/2015. */
public class DataShare {
    public static Submission sharedSubmission;
    //   public static Submission notifs;
    public static PrivateMessage sharedMessage;
    public static ArrayList<CommentObject> sharedComments;
    public static String subAuthor;
}
