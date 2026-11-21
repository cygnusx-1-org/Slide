package me.edgan.redditslide.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/**
 * The user journeys the baseline profile is generated from, shared with [StartupBenchmark].
 *
 * Everything runs in guest mode against the `benchmark` build, which is a fresh package with no
 * account. That is deliberate: a personal feed differs between runs and between machines, and the
 * guest frontpage exercises the same feed, comments and media code.
 */
object SlideJourneys {

    /** applicationId "me.edgan.redditslide" + :app's `benchmark` applicationIdSuffix. */
    const val PACKAGE = "me.edgan.redditslide.benchmark"

    /** The RecyclerView id of both the feed (SubmissionsView) and the comment list (CommentPage). */
    private const val LIST_ID = "vertical_content"

    /**
     * A subreddit of image posts, so an image is always near the top. The frontpage mixes link
     * posts in, and tapping a link's image would leave the app. See [openImage] for the pinned self
     * posts that keep this from being image-only.
     */
    private const val IMAGE_SUBREDDIT = "https://www.reddit.com/r/pics"

    private const val UI_TIMEOUT_MS = 30_000L
    private const val SHORT_TIMEOUT_MS = 5_000L
    private const val PERMISSION_CONTROLLER = "com.android.permissioncontroller"

    /** Cold start through to a loaded frontpage, clicking through the first-run tutorial if shown. */
    fun MacrobenchmarkScope.startToFeed() {
        pressHome()
        startActivityAndWait()
        device.dismissFirstRunIfPresent()
        device.awaitList()
    }

    /**
     * Flings the frontpage, so card inflation and bind for several post types are recorded.
     *
     * Down only. Flinging back up overshoots the top, where the gesture becomes a pull-to-refresh
     * that reloads the feed and UiAutomator waits five seconds per fling for a scroll that never
     * comes.
     */
    fun MacrobenchmarkScope.scrollFeed() {
        device.awaitList().flingPasses(device, passes = 5, Direction.DOWN)
    }

    /**
     * Opens a post from the feed and scrolls its comments, then returns to the feed.
     *
     * Taps a title rather than a card: a tap on the card's centre can land on the lead image,
     * which opens media instead of comments.
     */
    fun MacrobenchmarkScope.openPostAndScrollComments() {
        device.findClearOfBars(device.awaitList(), By.res(PACKAGE, "title"), "post title").click()
        // commentnav and the reply FAB exist only on the comments screen; the feed's list has the
        // same id, so waiting on the list alone would return before the screen changed.
        val commentsScreen =
            By.res(Pattern.compile("$PACKAGE:id/(commentnav|comment_floating_action_button)"))
        check(device.wait(Until.hasObject(commentsScreen), UI_TIMEOUT_MS) == true) {
            "The comments screen did not open; the app is on ${device.currentPackageName}."
        }
        device.waitForIdle()
        device.awaitList().flingPasses(device, passes = 3, Direction.DOWN)
        device.pressBack()
        device.awaitList()
    }

    /** Opens an image full-screen from r/pics, then returns. */
    fun MacrobenchmarkScope.openImage() {
        startActivityAndWait(
            Intent(Intent.ACTION_VIEW, Uri.parse(IMAGE_SUBREDDIT)).setPackage(PACKAGE)
        )
        // r/pics is not image-only at the top: its pinned announcements are self posts with a
        // header image, and tapping one opens comments. The domain overlay reads "i.redd.it" only
        // on image posts, and a domain is not translated, so this does not depend on the locale.
        val imageHost = By.res(PACKAGE, "subtextimage").text("i.redd.it")
        device.findClearOfBars(device.awaitList(), imageHost, "i.redd.it post").click()
        // MediaView covers the subreddit's list; wait for that rather than for its first frame,
        // which is drawn before the image has decoded.
        check(device.wait(Until.gone(By.res(PACKAGE, LIST_ID)), UI_TIMEOUT_MS) == true) {
            "Tapping the lead image did not open media; the app is on ${device.currentPackageName}."
        }
        device.wait(Until.hasObject(By.res(PACKAGE, "submission_image")), SHORT_TIMEOUT_MS)
        device.waitForIdle()
        device.pressBack()
        device.awaitList()
        device.pressBack()
    }

    /**
     * Returns a [target] in [list] whose centre is clear of the bars, scrolling down until one is.
     *
     * The feed scrolls under the toolbar and tab strip at the top and the navigation bar at the
     * bottom, and a tap on a view under either lands on the bar instead. UiAutomator still reports
     * such a view as visible, because it clips only to the list's own bounds.
     */
    private fun UiDevice.findClearOfBars(
        list: UiObject2,
        target: BySelector,
        what: String,
    ): UiObject2 {
        list.wait(Until.hasObject(target), UI_TIMEOUT_MS)
        list.setGestureMargin(list.visibleBounds.width() / 5)
        repeat(6) {
            val bounds = list.visibleBounds
            val clear = (bounds.top + bounds.height() / 5)..(bounds.bottom - bounds.height() / 10)
            list.findObjects(target)
                .firstOrNull { it.visibleBounds.centerY() in clear }
                ?.let { return it }
            list.scroll(Direction.DOWN, 0.5f)
            waitForIdle()
        }
        error("No $what clear of the toolbar and navigation bar to tap.")
    }

    /**
     * Clicks through the first-run tutorial and the notification permission prompt if they show.
     *
     * AGP uninstalls the target after every connected run, which wipes the "Tutorial" key that
     * gates the tutorial (MainActivity), so a fresh install is the normal case here.
     */
    fun UiDevice.dismissFirstRunIfPresent() {
        // The notification permission dialog (requested only by the tutorial) belongs to the
        // permission controller, not the app. Matched by resource id, not button text, so this
        // does not depend on the device locale.
        val allow = By.res(PERMISSION_CONTROLLER, "permission_allow_button")
        // Welcome page "GET STARTED" (its sibling restore button needs the SAF picker), then
        // Personalize page "DONE", accepting the default theme.
        val getStarted = By.res(PACKAGE, "welcome_getStarted")
        val done = By.res(PACKAGE, "done")
        val anyScreen = By.res(
            Pattern.compile(
                "$PERMISSION_CONTROLLER:id/permission_allow_button|" +
                    "$PACKAGE:id/(welcome_getStarted|done|$LIST_ID)"
            )
        )
        // Waits on whichever screen comes up rather than on each in turn, so an installed app
        // that goes straight to the feed costs no timeouts.
        repeat(4) {
            wait(Until.hasObject(anyScreen), UI_TIMEOUT_MS)
            val step = findObject(allow) ?: findObject(getStarted) ?: findObject(done) ?: return
            step.click()
            waitForIdle()
        }
    }

    /** Waits for a feed or comment list to be on screen and returns it. */
    fun UiDevice.awaitList(): UiObject2 {
        wait(Until.hasObject(By.res(PACKAGE, LIST_ID)), UI_TIMEOUT_MS)
        return findObject(By.res(PACKAGE, LIST_ID))
            ?: error(
                "No $LIST_ID on screen after ${UI_TIMEOUT_MS}ms. The app is on " +
                    "${currentPackageName ?: "an unknown package"}; if that is not $PACKAGE the " +
                    "first-run tutorial or a permission dialog is still up."
            )
    }

    /**
     * Flings the list [passes] times. The gesture margin keeps the swipe off the screen edges,
     * where the drawer and swipe-to-exit handlers would take it.
     */
    private fun UiObject2.flingPasses(device: UiDevice, passes: Int, direction: Direction) {
        setGestureMargin(visibleBounds.width() / 5)
        repeat(passes) {
            fling(direction)
            device.waitForIdle()
        }
    }
}
