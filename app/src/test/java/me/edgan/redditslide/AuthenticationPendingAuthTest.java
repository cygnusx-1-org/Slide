package me.edgan.redditslide;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import androidx.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dean.jraw.RedditClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * A cold start from a link to a thread asks for it while a guest's token request is still out, and
 * Reddit refuses a request with no token. {@link Authentication#awaitPendingAuth} is what holds the
 * thread fetch back until that request has run.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class AuthenticationPendingAuthTest {
    @Nullable private RedditClient clientWas;

    @Before
    public void setUp() {
        clientWas = Authentication.reddit;
    }

    @After
    public void tearDown() {
        Authentication.reddit = clientWas;
    }

    /** The executor every token request runs on, in order. */
    private static void queueAuthTask(Runnable task) throws Exception {
        Field executor = Authentication.class.getDeclaredField("REAUTH_EXECUTOR");
        executor.setAccessible(true);
        ((Executor) executor.get(null)).execute(task);
    }

    @Test
    public void anUnauthenticatedClientWaitsForTheQueuedTokenRequest() throws Exception {
        Authentication.reddit = mock(RedditClient.class);
        AtomicBoolean tokenRequestDone = new AtomicBoolean();
        queueAuthTask(
                () -> {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    tokenRequestDone.set(true);
                });

        Authentication.awaitPendingAuth();

        assertTrue(tokenRequestDone.get());
    }

    @Test
    public void anAuthenticatedClientDoesNotWait() throws Exception {
        RedditClient client = mock(RedditClient.class);
        when(client.isAuthenticated()).thenReturn(true);
        Authentication.reddit = client;
        CountDownLatch release = new CountDownLatch(1);
        queueAuthTask(
                () -> {
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

        long start = System.nanoTime();
        Authentication.awaitPendingAuth();
        long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        release.countDown();

        assertTrue("waited " + waitedMs + "ms", waitedMs < 1000);
    }

    @Test
    public void noClientMeansNothingToWaitFor() {
        Authentication.reddit = null;
        Authentication.awaitPendingAuth();
    }
}
