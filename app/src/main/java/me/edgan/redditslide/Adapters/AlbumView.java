package me.edgan.redditslide.Adapters;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.devspark.robototextview.RobotoTypefaces;
import java.util.List;
import me.edgan.redditslide.Activities.Album;
import me.edgan.redditslide.ImgurAlbum.Image;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.SubmissionParser;

/** Vertical list of an Imgur album. Row behaviour lives in {@link VerticalMediaAdapter}. */
public class AlbumView extends VerticalMediaAdapter {

    private final List<Image> users;

    public AlbumView(
            final Activity context,
            final List<Image> users,
            @Nullable String subreddit,
            @Nullable String SubmissionTitle) {
        super(context, subreddit, SubmissionTitle);
        this.users = users;

        if (context.findViewById(R.id.grid) != null)
            context.findViewById(R.id.grid)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    LayoutInflater l = context.getLayoutInflater();
                                    View body = l.inflate(R.layout.album_grid_dialog, null, false);
                                    GridView gridview = body.findViewById(R.id.images);
                                    gridview.setAdapter(new ImageGridAdapter(context, users));

                                    final AlertDialog.Builder builder =
                                            new AlertDialog.Builder(context).setView(body);
                                    final Dialog d = builder.create();
                                    gridview.setOnItemClickListener(
                                            new AdapterView.OnItemClickListener() {
                                                @Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                                                    final int offset = LayoutUtils.getToolbarOffset(context);
                                                    final RecyclerView.LayoutManager lm =
                                                            context instanceof Album
                                                                    ? ((Album) context).album.album.recyclerView.getLayoutManager()
                                                                    : ((RecyclerView) context.findViewById(R.id.images)).getLayoutManager();
                                                    if (lm != null) {
                                                        ((LinearLayoutManager) lm)
                                                                .scrollToPositionWithOffset(position + 1, offset);
                                                    }
                                                    d.dismiss();
                                                }
                                            });
                                    DialogUtil.matchDialogToCardBackground(d);
                                    d.show();
                                }
                            });
    }

    @Override
    protected int mediaCount() {
        return users == null ? 0 : users.size();
    }

    @Override
    protected boolean isAnimatedAt(final int index) {
        return users.get(index).animated();
    }

    @Override
    protected @Nullable String mediaUrlAt(final int index) {
        final Image image = users.get(index);
        return image.hasImageUrl() ? image.getImageUrl() : null;
    }

    @Override
    protected int mediaWidthAt(final int index) {
        final Integer width = users.get(index).getWidth();
        return width == null ? 0 : width;
    }

    @Override
    protected int mediaHeightAt(final int index) {
        final Integer height = users.get(index).getHeight();
        return height == null ? 0 : height;
    }

    @Override
    protected void bindStaticRow(final RecyclerView.ViewHolder holder2, final int index) {
        final StaticViewHolder holder = (StaticViewHolder) holder2;
        final Image user = users.get(index);
        final String url = mediaUrlAt(index);
        final boolean playable = url != null;

        if (playable) {
            ((Reddit) main.getApplicationContext())
                    .getImageLoader()
                    .displayImage(url, holder.image, ImageGridAdapter.options);
        } else {
            // Cancel first: displayImage() is what clears a pending load for this view, so
            // skipping it would let the previous row's task finish and draw into this one.
            ((Reddit) main.getApplicationContext()).getImageLoader().cancelDisplayTask(holder.image);
            holder.image.setImageDrawable(null);
        }
        holder.body.setVisibility(View.VISIBLE);
        holder.text.setVisibility(View.VISIBLE);

        // Reserve the row's height from the dimensions Imgur reported so that loading the bitmap
        // never resizes the row. The 0 reset matters because the aspect ratio survives recycling.
        final int imageWidth = mediaWidthAt(index);
        final int imageHeight = mediaHeightAt(index);
        holder.image.setAspectRatio(
                (imageWidth > 0 && imageHeight > 0)
                        ? (double) imageHeight / (double) imageWidth
                        : 0);
        holder.image.setLayoutParams(
                new LinearLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT));
        {
            int type = new FontPreferences(holder.body.getContext()).getFontTypeComment().getTypeface();
            Typeface typeface;
            if (type >= 0) {
                typeface = RobotoTypefaces.obtainTypeface(holder.body.getContext(), type);
            } else {
                typeface = Typeface.DEFAULT;
            }
            holder.body.setTypeface(typeface);
        }
        {
            int type = new FontPreferences(holder.body.getContext()).getFontTypeTitle().getTypeface();
            Typeface typeface;
            if (type >= 0) {
                typeface = RobotoTypefaces.obtainTypeface(holder.body.getContext(), type);
            } else {
                typeface = Typeface.DEFAULT;
            }
            holder.text.setTypeface(typeface);
        }
        {
            if (user.getTitle() != null) {
                List<String> text = SubmissionParser.getBlocks(user.getTitle());
                if (!text.isEmpty()) {
                    LinkUtil.setTextWithLinks(text.get(0), holder.text);
                } else {
                    // No blocks: clear any stale text left over from a recycled row so the
                    // emptiness check below hides the view instead of showing the wrong caption.
                    holder.text.setText("");
                }
                if (holder.text.getText().toString().isEmpty()) {
                    holder.text.setVisibility(View.GONE);
                }

            } else {
                holder.text.setVisibility(View.GONE);
            }
        }
        {
            if (user.getDescription() != null) {
                List<String> text = SubmissionParser.getBlocks(user.getDescription());
                if (!text.isEmpty()) {
                    LinkUtil.setTextWithLinks(text.get(0), holder.body);
                } else {
                    // No blocks: clear any stale text left over from a recycled row so the
                    // emptiness check below hides the view instead of showing the wrong caption.
                    holder.body.setText("");
                }
                if (holder.body.getText().toString().isEmpty()) {
                    holder.body.setVisibility(View.GONE);
                }
            } else {
                holder.body.setVisibility(View.GONE);
            }
        }

        // -1: the Imgur still path has never sent an index, and MediaView only uses one to page a
        // gallery and to name a saved file.
        holder.itemView.setOnClickListener(url == null ? null : v -> openMedia(url, -1));
        // After setOnClickListener, not before: that method turns clickable back on when it is
        // handed a null listener, so setting this first would leave an inert row still clickable.
        holder.itemView.setClickable(playable);
    }
}
