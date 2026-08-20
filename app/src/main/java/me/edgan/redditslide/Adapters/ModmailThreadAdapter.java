package me.edgan.redditslide.Adapters;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import com.devspark.robototextview.RobotoTypefaces;
import java.util.List;
import me.edgan.redditslide.Modmail.ModmailMessage;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.util.SubmissionParser;
import me.edgan.redditslide.util.TimeUtils;

/**
 * Renders the ordered messages of a single New Modmail conversation in {@link
 * me.edgan.redditslide.Activities.ModmailThread}. Mod replies use the indented {@code message_reply}
 * card; participant messages use the full-width {@code top_level_message} card, mirroring how the
 * inbox distinguishes replies.
 */
public class ModmailThreadAdapter extends RecyclerView.Adapter<MessageViewHolder> {

    private static final int PARTICIPANT = 1;
    private static final int MOD = 2;

    private final Context context;
    private final List<ModmailMessage> messages;

    public ModmailThreadAdapter(Context context, List<ModmailMessage> messages) {
        this.context = context;
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isAuthorMod() ? MOD : PARTICIPANT;
    }

    @Override
    public MessageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layout = viewType == MOD ? R.layout.message_reply : R.layout.top_level_message;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MessageViewHolder(v);
    }

    @Override
    public void onBindViewHolder(MessageViewHolder holder, int position) {
        final ModmailMessage message = messages.get(position);

        holder.time.setText(TimeUtils.getTimeAgo(message.getCreatedMillis(), context));

        SpannableStringBuilder author = new SpannableStringBuilder();
        String name = message.getAuthor();
        author.append(name.isEmpty() ? "[deleted]" : "/u/" + name);
        if (message.isInternal()) {
            SpannableStringBuilder note =
                    new SpannableStringBuilder(
                            " " + context.getString(R.string.modmail_internal_note));
            note.setSpan(
                    new StyleSpan(Typeface.ITALIC),
                    0,
                    note.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            author.append(note);
        }
        holder.user.setText(author);
        holder.title.setVisibility(View.GONE);

        int type = new FontPreferences(context).getFontTypeComment().getTypeface();
        Typeface typeface =
                type >= 0 ? RobotoTypefaces.obtainTypeface(context, type) : Typeface.DEFAULT;
        holder.content.setTypeface(typeface);

        setViews(
                SubmissionParser.replaceProcessingImgPlaceholders(
                        message.getBodyHtml(), message.getDataNode()),
                "FORCE_LINK_CLICK",
                holder);
    }

    private void setViews(String rawHTML, String subredditName, MessageViewHolder holder) {
        if (rawHTML.isEmpty()) {
            holder.content.setText("");
            holder.content.setVisibility(View.GONE);
            holder.commentOverflow.removeAllViews();
            return;
        }

        List<String> blocks = SubmissionParser.getBlocks(rawHTML);

        int startIndex = 0;
        if (!blocks.get(0).equals("<div class=\"md\">")) {
            holder.content.setVisibility(View.VISIBLE);
            holder.content.setTextHtml(blocks.get(0), subredditName);
            startIndex = 1;
        } else {
            holder.content.setText("");
            holder.content.setVisibility(View.GONE);
        }

        if (blocks.size() > 1) {
            if (startIndex == 0) {
                holder.commentOverflow.setViews(blocks, subredditName);
            } else {
                holder.commentOverflow.setViews(
                        blocks.subList(startIndex, blocks.size()), subredditName);
            }
        } else {
            holder.commentOverflow.removeAllViews();
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }
}
