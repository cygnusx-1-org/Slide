package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.util.PrefUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * {@link Authentication#migrateAccountToTokenForm} rewrites a bare account name in the stored
 * "accounts" set to the newer "name:token" form.
 *
 * <p>The block was previously copy-pasted into five call sites, where each one mutated the set
 * returned by {@code getStringSet} in place before writing it back. These tests pin the extracted
 * method's behaviour — which names get rewritten, and which are left alone — not the copy semantics
 * that {@code PrefUtilTest} covers. They pass with or without the copy, because the in-place edit
 * was visible to a same-process read either way; that invisibility is the whole shape of the bug,
 * and catching it needs a disk round-trip these tests do not attempt.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class AccountMigrationTest {

    private static final String KEY = "accounts";

    private SharedPreferences prefs;
    @SuppressWarnings("NullAway.Init") // assigned in setUp, which JUnit runs before every test
    private SharedPreferences originalAuthentication;

    @Nullable private String originalRefresh;

    @Before
    public void setUp() {
        originalAuthentication = Authentication.authentication;
        originalRefresh = Authentication.refresh;

        prefs =
                ((Context) ApplicationProvider.getApplicationContext())
                        .getSharedPreferences("AccountMigrationTest", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();

        Authentication.authentication = prefs;
        Authentication.refresh = "tok123";
    }

    @After
    public void tearDown() {
        // Authentication's state is static and the Robolectric sandbox is shared with same-config
        // test classes, so restore what was there rather than leaving the test's prefs installed.
        Authentication.authentication = originalAuthentication;
        Authentication.refresh = originalRefresh;
    }

    /** Re-read from preferences rather than holding on to a set the method under test touched. */
    private Set<String> stored() {
        return new HashSet<>(prefs.getStringSet(KEY, Collections.emptySet()));
    }

    @Test
    public void bareNameIsRewrittenToTokenForm() {
        prefs.edit().putStringSet(KEY, new HashSet<>(Collections.singletonList("alice"))).commit();

        Authentication.migrateAccountToTokenForm("alice");

        assertEquals(new HashSet<>(Collections.singletonList("alice:tok123")), stored());
    }

    @Test
    public void migrationIsVisibleThroughAnotherPreferencesHandle() {
        prefs.edit().putStringSet(KEY, new HashSet<>(Collections.singletonList("alice"))).commit();

        Authentication.migrateAccountToTokenForm("alice");

        // Read through a second handle for the same file, so the result cannot come from the local
        // `prefs` reference. Same in-memory store, so this is not a disk round-trip: what it pins
        // is that the migrated contents are what a subsequent read returns.
        Set<String> reread =
                PrefUtil.getStringSet(
                        ((Context) ApplicationProvider.getApplicationContext())
                                .getSharedPreferences(
                                        "AccountMigrationTest", Context.MODE_PRIVATE),
                        KEY,
                        Collections.emptySet());
        assertTrue("migrated entry must be readable from preferences", reread.contains("alice:tok123"));
        assertFalse("bare name must be gone from preferences", reread.contains("alice"));
    }

    @Test
    public void otherAccountsAreLeftAlone() {
        prefs.edit()
                .putStringSet(KEY, new HashSet<>(Arrays.asList("alice", "bob:othertoken")))
                .commit();

        Authentication.migrateAccountToTokenForm("alice");

        assertEquals(new HashSet<>(Arrays.asList("alice:tok123", "bob:othertoken")), stored());
    }

    @Test
    public void alreadyMigratedAccountIsUntouched() {
        // The common case: every authentication calls this, and almost every time there is nothing
        // to do. It must not append a second entry or re-token an existing one.
        Set<String> before = new HashSet<>(Collections.singletonList("alice:tok123"));
        prefs.edit().putStringSet(KEY, new HashSet<>(before)).commit();

        Authentication.migrateAccountToTokenForm("alice");

        assertEquals(before, stored());
    }

    @Test
    public void unknownAccountIsANoOp() {
        Set<String> before = new HashSet<>(Collections.singletonList("bob:othertoken"));
        prefs.edit().putStringSet(KEY, new HashSet<>(before)).commit();

        Authentication.migrateAccountToTokenForm("alice");

        assertEquals(before, stored());
    }

    @Test
    public void emptyAccountsSetIsANoOp() {
        Authentication.migrateAccountToTokenForm("alice");

        assertEquals(Collections.emptySet(), stored());
    }
}
