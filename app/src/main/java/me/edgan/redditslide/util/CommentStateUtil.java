package me.edgan.redditslide.util;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.edgan.redditslide.ActionStates;
import me.edgan.redditslide.Adapters.CommentAdapter;
import me.edgan.redditslide.Adapters.CommentViewHolder;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Views.DoEditorActions;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.Vote;
import me.edgan.redditslide.util.stubs.SimpleTextWatcher;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.VoteDirection;

// Added imports based on usage in the method
import me.edgan.redditslide.Adapters.CommentAdapterHelper;

public class CommentStateUtil {

    /**
     * Handles the logic for setting the highlighted state of a comment view.
     * Extracted from CommentAdapter.setCommentStateHighlighted.
     */
    public static void handleSetCommentStateHighlighted(
            final CommentAdapter adapter,
            final CommentViewHolder holder,
            final Comment n,
            final CommentNode baseNode,
            boolean isReplying,
            boolean animate) {

        if (adapter.currentlySelected != null && adapter.currentlySelected != holder) {
            adapter.setCommentStateUnhighlighted(adapter.currentlySelected, adapter.currentBaseNode, true);
        }

        // If a comment is hidden and (Swap long press == true), then a single click will un-hide
        // the comment and expand to show all children comments
        if (SettingValues.swap && holder.firstTextView.getVisibility() == View.GONE && !isReplying) {
            adapter.hiddenPersons.remove(n.getFullName());
            adapter.unhideAll(baseNode, holder.getBindingAdapterPosition() + 1);
            if (adapter.toCollapse.contains(n.getFullName()) && SettingValues.collapseComments) {
                adapter.setViews(n.getDataNode().get("body_html").asText(), adapter.submission.getSubredditName(), holder);
            }
            CommentAdapterHelper.hideChildrenObject(holder.childrenNumber);
            holder.commentOverflow.setVisibility(View.VISIBLE);
            adapter.toCollapse.remove(n.getFullName());
        } else {
            adapter.currentlySelected = holder;
            adapter.currentBaseNode = baseNode;
            int color = Palette.getColor(n.getSubredditName());
            adapter.currentSelectedItem = n.getFullName();
            adapter.currentNode = baseNode;
            LayoutInflater inflater = ((Activity) adapter.mContext).getLayoutInflater();
            adapter.resetMenu(holder.menuArea, false);
            final View baseView = inflater.inflate(SettingValues.rightHandedCommentMenu ? R.layout.comment_menu_right_handed : R.layout.comment_menu, holder.menuArea);

            if (!isReplying) {
                baseView.setVisibility(View.GONE);
                if (animate) {
                    adapter.expand(baseView);
                } else {
                    baseView.setVisibility(View.VISIBLE);
                    final int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    final int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    baseView.measure(widthSpec, heightSpec);
                    View l2 = baseView.findViewById(R.id.replyArea) == null ? baseView.findViewById(R.id.innerSend) : baseView.findViewById(R.id.replyArea);
                    final int widthSpec2 = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    final int heightSpec2 = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    l2.measure(widthSpec2, heightSpec2);
                    ViewGroup.LayoutParams layoutParams = baseView.getLayoutParams();
                    layoutParams.height = baseView.getMeasuredHeight() - l2.getMeasuredHeight();
                    baseView.setLayoutParams(layoutParams);
                }
            }

            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
            params.setMargins(0, 0, 0, 0);
            holder.itemView.setLayoutParams(params);

            View reply = baseView.findViewById(R.id.reply);
            View send = baseView.findViewById(R.id.send);

            final View menu = baseView.findViewById(R.id.menu);
            final View replyArea = baseView.findViewById(R.id.replyArea);

            final View more = baseView.findViewById(R.id.more);
            final ImageView upvote = baseView.findViewById(R.id.upvote);
            final ImageView downvote = baseView.findViewById(R.id.downvote);
            View discard = baseView.findViewById(R.id.discard);
            final EditText replyLine = baseView.findViewById(R.id.replyLine);
            final ImageView mod = baseView.findViewById(R.id.mod);

            final Comment comment = baseNode.getComment();
            if (ActionStates.getVoteDirection(comment) == VoteDirection.UPVOTE) {
                BlendModeUtil.tintImageViewAsModulate(upvote, holder.textColorUp);
                upvote.setContentDescription(adapter.mContext.getResources().getString(R.string.btn_upvoted));
            } else if (ActionStates.getVoteDirection(comment) == VoteDirection.DOWNVOTE) {
                BlendModeUtil.tintImageViewAsModulate(downvote, holder.textColorDown);
                downvote.setContentDescription(adapter.mContext.getResources().getString(R.string.btn_downvoted));
            } else {
                downvote.clearColorFilter();
                downvote.setContentDescription(adapter.mContext.getResources().getString(R.string.btn_downvote));
                upvote.clearColorFilter();
                upvote.setContentDescription(adapter.mContext.getResources().getString(R.string.btn_upvote));
            }

            try {
                mod.setVisibility(View.GONE);
            } catch (Exception e) {
                Log.d(LogUtil.getTag(), "Error loading mod " + e.toString());
            }

            if (UserSubscriptions.modOf != null && UserSubscriptions.modOf.contains(adapter.submission.getSubredditName().toLowerCase(Locale.ENGLISH))) {
                mod.setVisibility(View.VISIBLE);
                final Map<String, Integer> reports = comment.getUserReports();
                final Map<String, String> reports2 = comment.getModeratorReports();
                if (reports.size() + reports2.size() > 0) {
                    BlendModeUtil.tintImageViewAsSrcAtop(mod, ContextCompat.getColor(adapter.mContext, R.color.md_red_300));
                } else {
                    BlendModeUtil.tintImageViewAsSrcAtop(mod, Color.WHITE);
                }
                mod.setOnClickListener(
                        new OnSingleClickListener() {
                            @Override
                            public void onSingleClick(View v) {
                                CommentAdapterHelper.showModBottomSheet(
                                        adapter,
                                        adapter.mContext,
                                        baseNode,
                                        comment,
                                        holder,
                                        reports,
                                        reports2);
                            }
                        });
            } else {
                mod.setVisibility(View.GONE);
            }

            final ImageView edit = baseView.findViewById(R.id.edit);
            if (Authentication.name != null && Authentication.name.toLowerCase(Locale.ENGLISH).equals(comment.getAuthor().toLowerCase(Locale.ENGLISH)) && Authentication.didOnline) {
                edit.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {
                            CommentAdapterHelper.doCommentEdit(
                                adapter,
                                adapter.mContext,
                                adapter.fm,
                                baseNode,
                                baseNode.isTopLevel()
                                    ? adapter.submission.getSelftext()
                                    : baseNode.getParent().getComment().getBody(),
                                holder);
                        }
                    });
            } else {
                edit.setVisibility(View.GONE);
            }

            final ImageView delete = baseView.findViewById(R.id.delete);
            if (Authentication.name != null && Authentication.name.toLowerCase(Locale.ENGLISH).equals(comment.getAuthor().toLowerCase(Locale.ENGLISH)) && Authentication.didOnline) {
                delete.setOnClickListener(
                        new OnSingleClickListener() {
                            @Override
                            public void onSingleClick(View v) {
                                CommentAdapterHelper.deleteComment(adapter, adapter.mContext, baseNode, holder);
                            }
                        });
            } else {
                delete.setVisibility(View.GONE);
            }

            if (Authentication.isLoggedIn
                    && !adapter.submission.isArchived()
                    && !adapter.submission.isLocked()
                    && !(comment.getDataNode().has("locked")
                            && comment.getDataNode().get("locked").asBoolean())
                    && !adapter.deleted.contains(n.getFullName())
                    && !comment.getAuthor().equals("[deleted]")
                    && Authentication.didOnline) {
                if (isReplying) {
                    baseView.setVisibility(View.VISIBLE);

                    final int widthSpec =
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    final int heightSpec =
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    baseView.measure(widthSpec, heightSpec);

                    View l2 =
                            baseView.findViewById(R.id.replyArea) == null
                                    ? baseView.findViewById(R.id.innerSend)
                                    : baseView.findViewById(R.id.replyArea);
                    final int widthSpec2 =
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    final int heightSpec2 =
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    l2.measure(widthSpec2, heightSpec2);
                    RelativeLayout.LayoutParams params2 = (RelativeLayout.LayoutParams) baseView.getLayoutParams();
                    params2.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
                    params2.addRule(RelativeLayout.BELOW, R.id.commentOverflow);
                    baseView.setLayoutParams(params2);
                    replyArea.setVisibility(View.VISIBLE);
                    menu.setVisibility(View.GONE);
                    adapter.currentlyEditing = replyLine;
                    adapter.currentlyEditing.setOnFocusChangeListener(
                            new View.OnFocusChangeListener() {
                                @Override
                                public void onFocusChange(View v, boolean hasFocus) {
                                    if (hasFocus) {
                                        adapter.mPage.fastScroll.setVisibility(View.GONE);
                                        if (adapter.mPage.fab != null) {
                                            adapter.mPage.fab.setVisibility(View.GONE);
                                        }
                                        adapter.mPage.overrideFab = true;
                                    } else if (SettingValues.fastscroll) {
                                        adapter.mPage.fastScroll.setVisibility(View.VISIBLE);
                                        if (adapter.mPage.fab != null) {
                                            adapter.mPage.fab.setVisibility(View.VISIBLE);
                                        }
                                        adapter.mPage.overrideFab = false;
                                    }
                                }
                            });
                    final TextView profile = baseView.findViewById(R.id.profile);
                    adapter.changedProfile = Authentication.name;
                    profile.setText("/u/" + adapter.changedProfile);
                    profile.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                final HashMap<String, String> accounts = new HashMap<>();

                                for (String s :
                                        Authentication.authentication.getStringSet("accounts", new HashSet<String>())) {
                                    if (s.contains(":")) {
                                        accounts.put(s.split(":")[0], s.split(":")[1]);
                                    } else {
                                        accounts.put(s, "");
                                    }
                                }
                                final ArrayList<String> keys = new ArrayList<>(accounts.keySet());
                                final int i = keys.indexOf(adapter.changedProfile);

                                new AlertDialog.Builder(adapter.mContext)
                                    .setTitle(R.string.sorting_choose)
                                    .setSingleChoiceItems(
                                        keys.toArray(new String[0]),
                                        i,
                                        (dialog, which) -> {
                                            adapter.changedProfile = keys.get(which);
                                            profile.setText("/u/" + adapter.changedProfile);
                                        })
                                    .setNegativeButton(R.string.btn_cancel, null)
                                    .show();
                            }
                        });
                    replyLine.requestFocus();
                    KeyboardUtil.toggleKeyboard(adapter.mContext, InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);

                    adapter.currentlyEditingId = n.getFullName();
                    replyLine.setText(adapter.backedText);
                    replyLine.addTextChangedListener(
                            new SimpleTextWatcher() {
                                @Override
                                public void onTextChanged(CharSequence s, int start, int before, int count) {
                                    adapter.backedText = s.toString();
                                }
                            });
                    adapter.editingPosition = holder.getBindingAdapterPosition();
                }
                reply.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {
                            adapter.expandAndSetParams(baseView);

                            // If the base theme is Light or Sepia, tint the Editor actions to be white
                            if (SettingValues.currentTheme == 1
                                    || SettingValues.currentTheme == 5) {
                                final ImageView saveDraft = (ImageView) replyArea.findViewById(R.id.savedraft);
                                final ImageView draft = (ImageView) replyArea.findViewById(R.id.draft);
                                final ImageView imagerep = (ImageView) replyArea.findViewById(R.id.imagerep);
                                final ImageView link = (ImageView) replyArea.findViewById(R.id.link);
                                final ImageView bold = (ImageView) replyArea.findViewById(R.id.bold);
                                final ImageView italics = (ImageView) replyArea.findViewById(R.id.italics);
                                final ImageView bulletlist = (ImageView) replyArea.findViewById(R.id.bulletlist);
                                final ImageView numlist = (ImageView) replyArea.findViewById(R.id.numlist);
                                final ImageView draw = (ImageView) replyArea.findViewById(R.id.draw);
                                final ImageView quote = (ImageView) replyArea.findViewById(R.id.quote);
                                final ImageView size = (ImageView) replyArea.findViewById(R.id.size);
                                final ImageView strike = (ImageView) replyArea.findViewById(R.id.strike);
                                final ImageView author = (ImageView) replyArea.findViewById(R.id.author);
                                final ImageView spoiler = (ImageView) replyArea.findViewById(R.id.spoiler);
                                final List<ImageView> imageViewSet =
                                    Arrays.asList(
                                        saveDraft,
                                        draft,
                                        imagerep,
                                        link,
                                        bold,
                                        italics,
                                        bulletlist,
                                        numlist,
                                        draw,
                                        quote,
                                        size,
                                        strike,
                                        author,
                                        spoiler
                                    );
                                BlendModeUtil.tintImageViewsAsSrcAtop(imageViewSet, Color.WHITE);
                                BlendModeUtil.tintDrawableAsSrcIn(replyLine.getBackground(), Color.WHITE);
                            }

                            replyArea.setVisibility(View.VISIBLE);
                            menu.setVisibility(View.GONE);
                            adapter.currentlyEditing = replyLine;
                            DoEditorActions.doActions(
                                adapter.currentlyEditing,
                                replyArea,
                                adapter.fm,
                                (Activity) adapter.mContext,
                                comment.getBody(),
                                adapter.getParents(baseNode)
                            );
                            adapter.currentlyEditing.setOnFocusChangeListener(
                                new View.OnFocusChangeListener() {
                                    @Override
                                    public void onFocusChange(View v, boolean hasFocus) {
                                        if (hasFocus) {
                                            adapter.mPage.fastScroll.setVisibility(View.GONE);
                                            if (adapter.mPage.fab != null) {
                                                adapter.mPage.fab.setVisibility(View.GONE);
                                            }
                                            adapter.mPage.overrideFab = true;
                                        } else if (SettingValues.fastscroll) {
                                            adapter.mPage.fastScroll.setVisibility(View.VISIBLE);
                                            if (adapter.mPage.fab != null) {
                                                adapter.mPage.fab.setVisibility(View.VISIBLE);
                                            }
                                            adapter.mPage.overrideFab = false;
                                        }
                                    }
                                });
                            final TextView profile = baseView.findViewById(R.id.profile);
                            adapter.changedProfile = Authentication.name; // Static field access
                            profile.setText("/u/" + adapter.changedProfile);
                            profile.setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        final HashMap<String, String> accounts =
                                                new HashMap<>();

                                        for (String s : Authentication.authentication.getStringSet("accounts", new HashSet<String>())) {
                                            if (s.contains(":")) {
                                                accounts.put(s.split(":")[0], s.split(":")[1]);
                                            } else {
                                                accounts.put(s, "");
                                            }
                                        }
                                        final ArrayList<String> keys = new ArrayList<>(accounts.keySet());
                                        final int i = keys.indexOf(adapter.changedProfile);

                                        new AlertDialog.Builder(adapter.mContext)
                                            .setTitle(R.string.sorting_choose)
                                            .setSingleChoiceItems(
                                                keys.toArray(new String[0]),
                                                i,
                                                (dialog, which) -> {
                                                    adapter.changedProfile = keys.get(which);
                                                    profile.setText("/u/" + adapter.changedProfile);
                                                })
                                            .setNegativeButton(
                                                    R.string.btn_cancel, null)
                                            .show();
                                    }
                                });
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                replyLine.setOnFocusChangeListener(
                                    (view, b) -> {
                                        if (b) {
                                            view.postDelayed(
                                                () -> {
                                                    if (!view.hasFocus()) {
                                                        view.requestFocus();
                                                    }
                                                },
                                                100
                                            );
                                        }
                                    });
                            }
                            replyLine.requestFocus(); // TODO: Not working when called a second time
                            // time
                            KeyboardUtil.toggleKeyboard(adapter.mContext, InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);

                            adapter.currentlyEditingId = n.getFullName();
                            replyLine.addTextChangedListener(
                                new SimpleTextWatcher() {
                                    @Override
                                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                                        adapter.backedText = s.toString();
                                    }
                                });
                            adapter.editingPosition = holder.getBindingAdapterPosition();
                        }
                    });
                send.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {
                            adapter.currentlyEditingId = "";
                            adapter.backedText = "";

                            adapter.doShowMenu(baseView);

                            if (SettingValues.fastscroll) {
                                adapter.mPage.fastScroll.setVisibility(View.VISIBLE);
                                if (adapter.mPage.fab != null) adapter.mPage.fab.setVisibility(View.VISIBLE);
                                adapter.mPage.overrideFab = false;
                            }

                            adapter.dataSet.refreshLayout.setRefreshing(true);

                            if (adapter.currentlyEditing != null) {
                                String text = adapter.currentlyEditing.getText().toString();
                                // Instantiate inner class via adapter instance
                                adapter.new ReplyTaskComment(n, baseNode, holder, adapter.changedProfile).execute(text);
                                adapter.currentlyEditing = null;
                                adapter.editingPosition = -1;
                            }

                            // Hide soft keyboard
                            View view = ((Activity) adapter.mContext).findViewById(android.R.id.content);

                            if (view != null) {
                                KeyboardUtil.hideKeyboard(adapter.mContext, view.getWindowToken(), 0);
                            }
                        }
                    });
                discard.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {
                            adapter.currentlyEditing = null;
                            adapter.editingPosition = -1;
                            adapter.currentlyEditingId = "";
                            adapter.backedText = "";
                            adapter.mPage.overrideFab = false;
                            View view = ((Activity) adapter.mContext).findViewById(android.R.id.content);

                            if (view != null) {
                                KeyboardUtil.hideKeyboard(adapter.mContext, view.getWindowToken(), 0);
                            }

                            adapter.doShowMenu(baseView);
                        }
                    });
            } else {
                if (reply.getVisibility() == View.VISIBLE) {
                    reply.setVisibility(View.GONE);
                }
                if ((adapter.submission.isArchived()
                                || adapter.deleted.contains(n.getFullName())
                                || comment.getAuthor().equals("[deleted]"))
                        && Authentication.isLoggedIn
                        && Authentication.didOnline
                        && upvote.getVisibility() == View.VISIBLE) {
                    upvote.setVisibility(View.GONE);
                }
                if ((adapter.submission.isArchived()
                                || adapter.deleted.contains(n.getFullName())
                                || comment.getAuthor().equals("[deleted]"))
                        && Authentication.isLoggedIn
                        && Authentication.didOnline
                        && downvote.getVisibility() == View.VISIBLE) {
                    downvote.setVisibility(View.GONE);
                }
            }

            more.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {
                            CommentAdapterHelper.showOverflowBottomSheet(adapter, adapter.mContext, holder, baseNode);
                        }
                    });
            upvote.setOnClickListener(
                new OnSingleClickListener() {
                    @Override
                    public void onSingleClick(View v) {
                        adapter.setCommentStateUnhighlighted(holder, comment, baseNode, true);
                        if (ActionStates.getVoteDirection(comment) == VoteDirection.UPVOTE) {
                            new Vote(v, adapter.mContext).execute(n);
                            ActionStates.setVoteDirection(comment, VoteDirection.NO_VOTE);
                            adapter.doScoreText(holder, n, adapter);
                            upvote.clearColorFilter();
                        } else {
                            new Vote(true, v, adapter.mContext).execute(n);
                            ActionStates.setVoteDirection(comment, VoteDirection.UPVOTE);
                            downvote.clearColorFilter(); // reset colour
                            adapter.doScoreText(holder, n, adapter);
                            BlendModeUtil.tintImageViewAsModulate(upvote, holder.textColorUp);
                        }
                    }
                });
            downvote.setOnClickListener(
                new OnSingleClickListener() {

                    @Override
                    public void onSingleClick(View v) {
                        adapter.setCommentStateUnhighlighted(holder, comment, baseNode, true);
                        if (ActionStates.getVoteDirection(comment) == VoteDirection.DOWNVOTE) {
                            new Vote(v, adapter.mContext).execute(n);
                            ActionStates.setVoteDirection(comment, VoteDirection.NO_VOTE);
                            adapter.doScoreText(holder, n, adapter);
                            downvote.clearColorFilter();
                        } else {
                            new Vote(false, v, adapter.mContext).execute(n);
                            ActionStates.setVoteDirection(comment, VoteDirection.DOWNVOTE);
                            upvote.clearColorFilter(); // reset colour
                            adapter.doScoreText(holder, n, adapter);
                            BlendModeUtil.tintImageViewAsModulate(downvote, holder.textColorDown);
                        }
                    }
                });
            menu.setBackgroundColor(color);
            replyArea.setBackgroundColor(color);

            if (!isReplying) {
                menu.setVisibility(View.VISIBLE);
                replyArea.setVisibility(View.GONE);
            }

            holder.itemView
                .findViewById(R.id.background)
                .setBackgroundColor(Color.argb(50, Color.red(color), Color.green(color), Color.blue(color)));
        }
    }
}