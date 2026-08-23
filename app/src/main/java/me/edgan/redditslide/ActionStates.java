package me.edgan.redditslide;

import java.util.ArrayList;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.PublicContribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.VoteDirection;

/** Created by carlo_000 on 2/26/2016. */
public class ActionStates {
    public static final ArrayList<String> upVotedFullnames = new ArrayList<>();
    public static final ArrayList<String> downVotedFullnames = new ArrayList<>();

    public static final ArrayList<String> unvotedFullnames = new ArrayList<>();
    public static final ArrayList<String> savedFullnames = new ArrayList<>();
    public static final ArrayList<String> unSavedFullnames = new ArrayList<>();

    public static VoteDirection getVoteDirection(PublicContribution s) {
        if (upVotedFullnames.contains(s.getFullName())) {
            return VoteDirection.UPVOTE;
        } else if (downVotedFullnames.contains(s.getFullName())) {
            return VoteDirection.DOWNVOTE;
        } else if (unvotedFullnames.contains(s.getFullName())) {
            return VoteDirection.NO_VOTE;
        } else {
            return s.getVote();
        }
    }

    public static void setVoteDirection(PublicContribution s, VoteDirection direction) {
        String fullname = s.getFullName();
        upVotedFullnames.remove(fullname);
        downVotedFullnames.remove(fullname);
        unvotedFullnames.remove(fullname);
        switch (direction) {
            case UPVOTE:
                upVotedFullnames.add(fullname);
                break;
            case DOWNVOTE:
                downVotedFullnames.add(fullname);
                break;
            case NO_VOTE:
                unvotedFullnames.add(fullname);
                break;
        }
    }

    public static boolean isSaved(PublicContribution s) {
        if (savedFullnames.contains(s.getFullName())) {
            return true;
        } else if (unSavedFullnames.contains(s.getFullName())) {
            return false;
        } else if (s instanceof Submission) {
            // JRAW returns a nullable Boolean when the listing JSON lacks "saved"
            Boolean saved = ((Submission) s).isSaved();
            return saved != null && saved;
        } else if (s instanceof Comment) {
            Boolean saved = ((Comment) s).isSaved();
            return saved != null && saved;
        } else {
            return false;
        }
    }

    public static void setSaved(PublicContribution s, boolean b) {
        String fullname = s.getFullName();
        savedFullnames.remove(fullname);
        if (b) {
            savedFullnames.add(fullname);
        } else {
            unSavedFullnames.add(fullname);
        }
    }
}
