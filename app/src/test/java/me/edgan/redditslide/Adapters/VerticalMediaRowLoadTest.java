package me.edgan.redditslide.Adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.GifUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Covers the load a video row owns: that the row lets go of it once the row stops being that item's.
 *
 * <p>The task holds the row's ExoVideoView and writes into it from onPostExecute, so a load left
 * running for the item a recycled row used to show would play that item's video in whatever item the
 * row was rebound to. MediaRowLoadState cannot catch that — it tracks one url per row, not tasks
 * belonging to a previous item — so the holder has to drop the task itself.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class VerticalMediaRowLoadTest {

    private static final String URL = "https://v.redd.it/abc123/DASHPlaylist.mpd";

    /** One host per test method; Robolectric tears its environment down between them. */
    private AppCompatActivity activity;

    /**
     * Every row this test built, so its player can be released. These rows are never attached to a
     * window, so ExoVideoView.onDetachedFromWindow — the only thing that calls stop() — never runs
     * for them, and an unreleased SimpleExoPlayer keeps its playback thread alive.
     */
    private final List<VerticalMediaAdapter.AnimatedViewHolder> rows = new ArrayList<>();

    @After
    public void releasePlayers() {
        for (VerticalMediaAdapter.AnimatedViewHolder row : rows) {
            row.exoVideoView.stop();
            // Asserted, not assumed: without this the teardown could stop releasing anything and no
            // test in the class would notice, which is how the leak got here in the first place.
            assertFalse("row kept its player after stop()", row.exoVideoView.hasPlayer());
        }
        rows.clear();
    }

    @Before
    public void setUp() {
        final ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class);
        activity = controller.get();
        // The row inflates an ExoVideoView, which resolves ?attr/colorAccent.
        activity.setTheme(R.style.Theme_LIGHT);
        controller.setup();
    }

    @Test
    public void recyclingARowCancelsItsLoad() {
        final VerticalMediaAdapter.AnimatedViewHolder holder = animatedRow();
        final GifUtils.AsyncLoadGif load = attachStubLoad(holder);

        newAdapter().onViewRecycled(holder);

        assertTrue(load.isCancelled());
        assertNull(holder.load);
    }

    @Test
    public void detachingARowCancelsItsLoadAndForgetsWhatItHeld() {
        final VerticalMediaAdapter.AnimatedViewHolder holder = animatedRow();
        final GifUtils.AsyncLoadGif load = attachStubLoad(holder);

        // Detaching releases the player this load was going to feed.
        newAdapter().onViewDetachedFromWindow(holder);

        assertTrue(load.isCancelled());
        assertNull(holder.load);
        assertTrue(holder.loadState.isEmpty());
    }

    @Test
    public void cancellingTwiceIsHarmless() {
        final VerticalMediaAdapter.AnimatedViewHolder holder = animatedRow();
        attachStubLoad(holder);

        holder.cancelLoad();
        holder.cancelLoad();

        assertNull(holder.load);
    }

    @Test
    public void aRowWithNoLoadCanStillBeRecycled() {
        final VerticalMediaAdapter.AnimatedViewHolder holder = animatedRow();

        assertNull(holder.load);
        newAdapter().onViewRecycled(holder);

        assertNull(holder.load);
    }

    @Test
    public void anUnboundRowHasNoMediaIndex() {
        // onViewAttachedToWindow reloads from this index, so before a bind assigns one it must not
        // look like a valid entry.
        assertEquals(-1, animatedRow().mediaIndex);
    }

    @Test
    public void bindingAnUnplayableRowClearsAnInheritedProgressBar() {
        final VerticalMediaAdapter adapter = unplayableAdapter();
        final VerticalMediaAdapter.AnimatedViewHolder holder = animatedRow();

        // What the row inherits from the item it used to show: a load that was cancelled or failed
        // leaves the bar up, and only reaching STATE_READY ever takes it down.
        holder.loader.setVisibility(View.VISIBLE);

        adapter.onBindViewHolder(holder, 0);

        assertEquals(View.GONE, holder.loader.getVisibility());
    }

    @Test
    public void bindingARowThatIsLoadingShowsTheProgressBar() {
        final VerticalMediaAdapter adapter = newAdapter(1);
        final VerticalMediaAdapter.AnimatedViewHolder holder = animatedRow();
        holder.loader.setVisibility(View.GONE);

        adapter.onBindViewHolder(holder, 0);

        assertEquals(View.VISIBLE, holder.loader.getVisibility());
        holder.cancelLoad();
    }

    @Test
    public void aRowsLoadGoesToTheThreadPoolNotTheSerialQueue() {
        // AsyncTask.execute() is one process-wide serial queue. Each of these blocks on a network
        // round trip before the next starts — the file size lookup at the top of doInBackground is a
        // synchronous HEAD request — so a list of video rows would load them strictly one after
        // another, behind every other AsyncTask in the app. GifUtils.downloadGif, which the Tumblr
        // rows use, already picks the pool.
        final Executor[] used = {null};
        final VerticalMediaAdapter adapter = newAdapter(1, used);
        final VerticalMediaAdapter.AnimatedViewHolder holder = animatedRow();

        adapter.onBindViewHolder(holder, 0);

        assertSame(AsyncTask.THREAD_POOL_EXECUTOR, used[0]);
    }

    @Test
    public void theLoadIsHandedToTheExecutorItWasGiven() {
        // The seam's own body, called for real rather than overridden: hand it an executor that
        // records instead of running, and the task has to arrive there. If the body ignored its
        // parameter and called load.execute(url), the work would go to the serial queue instead and
        // this executor would never see it.
        final VerticalMediaAdapter.AnimatedViewHolder holder = animatedRow();
        final GifUtils.AsyncLoadGif load = attachStubLoad(holder);
        final List<Runnable> handedOver = new ArrayList<>();

        newAdapter().execute(load, handedOver::add, URL);

        assertEquals(1, handedOver.size());
    }

    @Test
    public void rebindingARowThatAlreadyHasItsMediaLeavesNoProgressBar() {
        final VerticalMediaAdapter adapter = newAdapter(1);
        final VerticalMediaAdapter.AnimatedViewHolder holder = animatedRow();
        // Already showing this url, so the rebind starts no load — a width change does this.
        holder.loadState.loadStarted(URL);
        holder.loader.setVisibility(View.VISIBLE);

        adapter.onBindViewHolder(holder, 0);

        assertEquals(View.GONE, holder.loader.getVisibility());
        assertNull(holder.load);
    }

    @Test
    public void aHostWithNoToolbarGetsNoSpacerRowAtAll() {
        // Shadowbox and the comment thread. The bottom inset there is the album card's overlaying
        // info panel, whose height is only known once BaseAlbumFull has measured it, so that fragment
        // pads the RecyclerView itself and the list is media rows and nothing else.
        final VerticalMediaAdapter adapter = newAdapter(3);
        assertFalse(adapter.hasToolbarSpacer());

        assertEquals(3, adapter.getItemCount());
        for (int position = 0; position < adapter.getItemCount(); position++) {
            assertEquals(
                    VerticalMediaAdapter.VIEW_TYPE_ANIMATED, adapter.getItemViewType(position));
        }
    }

    @Test
    public void withNoLeadingSpacerRowPositionsAreMediaIndicesDirectly() {
        final VerticalMediaAdapter adapter = newAdapter(3);
        assertFalse(adapter.hasToolbarSpacer());

        assertEquals(0, adapter.indexFor(0));
        assertEquals(2, adapter.indexFor(2));
    }

    @Test
    public void aRebindWhileALoadIsInFlightLeavesTheProgressBarAlone() {
        final VerticalMediaAdapter adapter = newAdapter(1);
        final VerticalMediaAdapter.AnimatedViewHolder holder = animatedRow();

        // The state after a bind that started a load: the url is recorded, the task is held, the bar
        // is up. attachStubLoad rather than a real bind, so nothing goes near the network.
        attachStubLoad(holder);
        holder.loader.setVisibility(View.VISIBLE);

        // A width change rebinds mid-download. shouldLoad is already false, so the row starts no
        // second load — but the bar must stay up until the media arrives or fails.
        adapter.onBindViewHolder(holder, 0);

        assertEquals(View.VISIBLE, holder.loader.getVisibility());
        holder.cancelLoad();
    }

    @Test
    public void aHostWithAToolbarClearsItAtTheTopOfTheList() {
        final View toolbar = new View(activity);
        toolbar.setId(R.id.toolbar);
        activity.setContentView(toolbar);

        final VerticalMediaAdapter adapter = newAdapter(1);
        assertTrue(adapter.hasToolbarSpacer());
        assertEquals(2, adapter.getItemCount());
        assertEquals(VerticalMediaAdapter.VIEW_TYPE_SPACER, adapter.getItemViewType(0));
        // Position 0 is the spacer, so media index 0 is at position 1.
        assertEquals(0, adapter.indexFor(1));

        final RecyclerView.ViewHolder spacer = spacerRow(adapter);
        adapter.onBindViewHolder(spacer, 0);

        assertEquals(
                activity.getResources().getDimensionPixelSize(R.dimen.standard_toolbar_height),
                spacer.itemView.getLayoutParams().height);
    }

    private RecyclerView.ViewHolder spacerRow(final VerticalMediaAdapter adapter) {
        final RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        return adapter.onCreateViewHolder(list, VerticalMediaAdapter.VIEW_TYPE_SPACER);
    }

    /**
     * Gives the row a load without starting a real one. What is under test is that the holder lets go
     * of whatever task it holds, not what AsyncLoadGif does with a url.
     */
    private GifUtils.AsyncLoadGif attachStubLoad(
            final VerticalMediaAdapter.AnimatedViewHolder holder) {
        holder.loadState.loadStarted(URL);
        holder.load =
                new GifUtils.AsyncLoadGif(
                        activity,
                        holder.exoVideoView,
                        holder.loader,
                        null, // placeholder
                        false, // closeIfNull
                        false, // autostart
                        null, // size
                        "pics",
                        "A title");
        return holder.load;
    }

    private VerticalMediaAdapter.AnimatedViewHolder animatedRow() {
        final AppCompatActivity context = activity;
        final RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        final View row =
                LayoutInflater.from(context)
                        .inflate(R.layout.submission_gifcard_album, list, false);
        final VerticalMediaAdapter.AnimatedViewHolder holder =
                new VerticalMediaAdapter.AnimatedViewHolder(row);
        // Inflating that layout built an ExoVideoView, and with it a player; see releasePlayers().
        rows.add(holder);
        return holder;
    }

    /** The smallest concrete adapter that reaches the base class's row callbacks. */
    private VerticalMediaAdapter newAdapter() {
        return newAdapter(0);
    }

    /** As above, over {@code count} playable animated entries. */
    private VerticalMediaAdapter newAdapter(final int count) {
        return newAdapter(count, null);
    }

    /**
     * As above; when {@code recorder} is given it captures the executor the row's load is handed to
     * and stops the task running, so the choice is visible without a real load going out.
     */
    private VerticalMediaAdapter newAdapter(
            final int count, final @Nullable Executor[] recorder) {
        return new VerticalMediaAdapter(activity, "pics", "A title") {
            @Override
            void execute(
                    final GifUtils.AsyncLoadGif load,
                    final Executor executor,
                    final String url) {
                if (recorder == null) {
                    super.execute(load, executor, url);
                } else {
                    recorder[0] = executor;
                }
            }

            @Override
            protected int mediaCount() {
                return count;
            }

            @Override
            protected boolean isAnimatedAt(final int index) {
                return true;
            }

            @Override
            protected String mediaUrlAt(final int index) {
                return URL;
            }

            @Override
            protected int mediaWidthAt(final int index) {
                return 1920;
            }

            @Override
            protected int mediaHeightAt(final int index) {
                return 1080;
            }

            @Override
            protected void bindStaticRow(final RecyclerView.ViewHolder holder, final int index) {}
        };
    }

    /** One animated entry whose url is missing, so the row has nothing to load or play. */
    private VerticalMediaAdapter unplayableAdapter() {
        return new VerticalMediaAdapter(activity, "pics", "A title") {
            @Override
            protected int mediaCount() {
                return 1;
            }

            @Override
            protected boolean isAnimatedAt(final int index) {
                return true;
            }

            @Override
            protected @Nullable String mediaUrlAt(final int index) {
                return null;
            }

            @Override
            protected int mediaWidthAt(final int index) {
                return 0;
            }

            @Override
            protected int mediaHeightAt(final int index) {
                return 0;
            }

            @Override
            protected void bindStaticRow(final RecyclerView.ViewHolder holder, final int index) {}
        };
    }

    public static class TestActivity extends AppCompatActivity {}
}
