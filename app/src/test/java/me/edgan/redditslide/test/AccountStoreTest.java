package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The stored "accounts" set and the parallel "tokens" set are written by {@link
 * Authentication#storeAccountToken} on sign-in and pruned by {@link Authentication#forgetAccount}
 * on removal. Both used to leave live refresh tokens in "tokens": removal wrote "accounts" alone,
 * and sign-in only ever added. These tests pin which entries and tokens go, and which stay.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class AccountStoreTest {

    private static final String ACCOUNTS = "accounts";
    private static final String TOKENS = "tokens";

    private SharedPreferences prefs;

    @SuppressWarnings("NullAway.Init") // assigned in setUp, which JUnit runs before every test
    private SharedPreferences originalAuthentication;

    @Before
    public void setUp() {
        originalAuthentication = Authentication.authentication;

        prefs =
                ((Context) ApplicationProvider.getApplicationContext())
                        .getSharedPreferences("AccountStoreTest", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();

        Authentication.authentication = prefs;
    }

    @After
    public void tearDown() {
        // Authentication's state is static and the Robolectric sandbox is shared with same-config
        // test classes, so restore what was there rather than leaving the test's prefs installed.
        Authentication.authentication = originalAuthentication;
    }

    private void store(Set<String> accounts, Set<String> tokens) {
        prefs.edit().putStringSet(ACCOUNTS, accounts).putStringSet(TOKENS, tokens).commit();
    }

    /** Drives the sign-in write the way Login and Reauthenticate do: through their own editor. */
    private void storeAccountToken(@Nullable String accountName, @Nullable String refreshToken) {
        SharedPreferences.Editor editor = prefs.edit();
        Authentication.storeAccountToken(editor, accountName, refreshToken);
        editor.commit();
    }

    /** Re-read from preferences rather than holding on to a set the method under test touched. */
    private Set<String> stored(String key) {
        return new HashSet<>(prefs.getStringSet(key, Collections.emptySet()));
    }

    @Test
    public void removedAccountsTokenIsPruned() {
        store(
                new HashSet<>(Arrays.asList("alice:tokA", "bob:tokB")),
                new HashSet<>(Arrays.asList("tokA", "tokB")));

        Authentication.forgetAccount("alice");

        assertEquals(new HashSet<>(Collections.singletonList("bob:tokB")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Collections.singletonList("tokB")), stored(TOKENS));
    }

    @Test
    public void removingTheLastAccountEmptiesBothSets() {
        store(
                new HashSet<>(Collections.singletonList("alice:tokA")),
                new HashSet<>(Collections.singletonList("tokA")));

        Authentication.forgetAccount("alice");

        assertEquals(Collections.emptySet(), stored(ACCOUNTS));
        assertEquals(Collections.emptySet(), stored(TOKENS));
    }

    @Test
    public void removingABareNameEntryStrandsItsTokenWhichIsThenPruned() {
        // An account stored in the older form names no token, so there is nothing to attribute
        // directly. Removing it leaves no bare-name entry behind, though, and the index lookup
        // that was the only reader of "tokens" dies with it — so the stranded token goes too.
        store(
                new HashSet<>(Arrays.asList("alice", "bob:tokB")),
                new HashSet<>(Arrays.asList("tokA", "tokB")));

        Authentication.forgetAccount("alice");

        assertEquals(new HashSet<>(Collections.singletonList("bob:tokB")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Collections.singletonList("tokB")), stored(TOKENS));
    }

    @Test
    public void unknownAccountIsANoOp() {
        Set<String> accounts = new HashSet<>(Collections.singletonList("bob:tokB"));
        Set<String> tokens = new HashSet<>(Collections.singletonList("tokB"));
        store(new HashSet<>(accounts), new HashSet<>(tokens));

        Authentication.forgetAccount("alice");

        assertEquals(accounts, stored(ACCOUNTS));
        assertEquals(tokens, stored(TOKENS));
    }

    @Test
    public void emptyStoreIsANoOp() {
        Authentication.forgetAccount("alice");

        assertEquals(Collections.emptySet(), stored(ACCOUNTS));
        assertEquals(Collections.emptySet(), stored(TOKENS));
    }

    @Test
    public void forgettingAnAccountLeavesAPrefixNamedOneAlone() {
        // "bob" is a substring of "bobby:tokBobby", which is how the entry used to be matched.
        store(
                new HashSet<>(Arrays.asList("bob:tokBob", "bobby:tokBobby")),
                new HashSet<>(Arrays.asList("tokBob", "tokBobby")));

        Authentication.forgetAccount("bob");

        assertEquals(new HashSet<>(Collections.singletonList("bobby:tokBobby")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Collections.singletonList("tokBobby")), stored(TOKENS));
    }

    @Test
    public void signingInStoresTheAccountAndItsToken() {
        storeAccountToken("alice", "tokA");

        assertEquals(new HashSet<>(Collections.singletonList("alice:tokA")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Collections.singletonList("tokA")), stored(TOKENS));
    }

    @Test
    public void signingInAgainSupersedesTheStoredToken() {
        store(
                new HashSet<>(Collections.singletonList("alice:tokA")),
                new HashSet<>(Collections.singletonList("tokA")));

        storeAccountToken("alice", "tokA2");

        assertEquals(new HashSet<>(Collections.singletonList("alice:tokA2")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Collections.singletonList("tokA2")), stored(TOKENS));
    }

    @Test
    public void signingInClearsADuplicateLeftByAnEarlierLogin() {
        // What the add-only login produced: two entries for one account, both tokens kept.
        store(
                new HashSet<>(Arrays.asList("alice:tokA", "alice:tokA2")),
                new HashSet<>(Arrays.asList("tokA", "tokA2")));

        storeAccountToken("alice", "tokA3");

        assertEquals(new HashSet<>(Collections.singletonList("alice:tokA3")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Collections.singletonList("tokA3")), stored(TOKENS));
    }

    @Test
    public void signingInReplacesABareNameEntry() {
        store(
                new HashSet<>(Collections.singletonList("alice")),
                new HashSet<>(Collections.singletonList("tokA")));

        storeAccountToken("alice", "tokA2");

        assertEquals(new HashSet<>(Collections.singletonList("alice:tokA2")), stored(ACCOUNTS));
        // The bare entry named no token, so the one already in "tokens" could not be attributed to
        // it. Signing in gives that account a token of its own and leaves no bare-name entry, so
        // the old one is now unreachable and goes.
        assertEquals(new HashSet<>(Collections.singletonList("tokA2")), stored(TOKENS));
    }

    @Test
    public void signingInLeavesOtherAccountsAlone() {
        store(
                new HashSet<>(Arrays.asList("alice:tokA", "bob:tokB")),
                new HashSet<>(Arrays.asList("tokA", "tokB")));

        storeAccountToken("alice", "tokA2");

        assertEquals(new HashSet<>(Arrays.asList("alice:tokA2", "bob:tokB")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Arrays.asList("tokA2", "tokB")), stored(TOKENS));
    }

    @Test
    public void signingInDoesNotReplaceAnAccountWhoseNameStartsTheSame() {
        store(
                new HashSet<>(Collections.singletonList("bobby:tokBobby")),
                new HashSet<>(Collections.singletonList("tokBobby")));

        storeAccountToken("bob", "tokBob");

        assertEquals(new HashSet<>(Arrays.asList("bob:tokBob", "bobby:tokBobby")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Arrays.asList("tokBob", "tokBobby")), stored(TOKENS));
    }

    @Test
    public void nothingIsStoredWithoutARefreshToken() {
        Set<String> accounts = new HashSet<>(Collections.singletonList("bob:tokB"));
        Set<String> tokens = new HashSet<>(Collections.singletonList("tokB"));
        store(new HashSet<>(accounts), new HashSet<>(tokens));

        storeAccountToken("alice", null);

        assertEquals(accounts, stored(ACCOUNTS));
        assertEquals(tokens, stored(TOKENS));
    }

    @Test
    public void nothingIsStoredWithoutAnAccountName() {
        Set<String> accounts = new HashSet<>(Collections.singletonList("bob:tokB"));
        Set<String> tokens = new HashSet<>(Collections.singletonList("tokB"));
        store(new HashSet<>(accounts), new HashSet<>(tokens));

        storeAccountToken(null, "tokA");

        assertEquals(accounts, stored(ACCOUNTS));
        assertEquals(tokens, stored(TOKENS));
    }

    @Test
    public void removalDropsATokenNoAccountNames() {
        // The orphan an older removal or reauth left behind: nothing in "accounts" names it, so no
        // lookup can reach it.
        store(
                new HashSet<>(Arrays.asList("alice:tokA", "bob:tokB")),
                new HashSet<>(Arrays.asList("tokA", "tokB", "orphan")));

        Authentication.forgetAccount("bob");

        assertEquals(new HashSet<>(Collections.singletonList("alice:tokA")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Collections.singletonList("tokA")), stored(TOKENS));
    }

    @Test
    public void signingInDropsATokenNoAccountNames() {
        store(
                new HashSet<>(Collections.singletonList("alice:tokA")),
                new HashSet<>(Arrays.asList("tokA", "orphan")));

        storeAccountToken("bob", "tokB");

        assertEquals(new HashSet<>(Arrays.asList("alice:tokA", "bob:tokB")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Arrays.asList("tokA", "tokB")), stored(TOKENS));
    }

    @Test
    public void removingTheLastAccountDropsEveryRemainingToken() {
        store(
                new HashSet<>(Collections.singletonList("alice:tokA")),
                new HashSet<>(Arrays.asList("tokA", "orphan")));

        Authentication.forgetAccount("alice");

        assertEquals(Collections.emptySet(), stored(ACCOUNTS));
        assertEquals(Collections.emptySet(), stored(TOKENS));
    }

    @Test
    public void aBareNameEntryProtectsUnnamedTokens() {
        // "bob" carries no token of its own, so the drawer looks its token up by position in
        // "tokens". Pruning what no entry names would take the only copy of it.
        store(
                new HashSet<>(Arrays.asList("alice:tokA", "bob")),
                new HashSet<>(Arrays.asList("tokA", "tokB")));

        storeAccountToken("alice", "tokA2");

        assertEquals(new HashSet<>(Arrays.asList("alice:tokA2", "bob")), stored(ACCOUNTS));
        // tokB is unnamed but survives, because "bob" still needs the index lookup. tokA goes only
        // because alice's own entry superseded it, not because it was pruned.
        assertEquals(new HashSet<>(Arrays.asList("tokB", "tokA2")), stored(TOKENS));
    }

    @Test
    public void theTokenInUseIsNeverPruned() {
        prefs.edit().putString("lasttoken", "tokLive").commit();
        store(
                new HashSet<>(Collections.singletonList("alice:tokA")),
                new HashSet<>(Arrays.asList("tokA", "tokLive")));

        Authentication.forgetAccount("nobody");

        assertEquals(new HashSet<>(Arrays.asList("tokA", "tokLive")), stored(TOKENS));
    }

    @Test
    public void reauthPruneDropsOrphansWithNoAccountChange() {
        // The restored-backup case: nothing was added or removed, so only the reauth pass can
        // reach this profile.
        prefs.edit().putString("lasttoken", "tokA").commit();
        store(
                new HashSet<>(Collections.singletonList("alice:tokA")),
                new HashSet<>(Arrays.asList("tokA", "orphan")));

        Authentication.pruneOrphanTokens();

        assertEquals(new HashSet<>(Collections.singletonList("alice:tokA")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Collections.singletonList("tokA")), stored(TOKENS));
    }

    @Test
    public void reauthPruneLeavesABareNameProfileAlone() {
        prefs.edit().putString("lasttoken", "tokA").commit();
        Set<String> tokens = new HashSet<>(Arrays.asList("tokA", "tokB"));
        store(new HashSet<>(Arrays.asList("alice:tokA", "bob")), new HashSet<>(tokens));

        Authentication.pruneOrphanTokens();

        assertEquals(tokens, stored(TOKENS));
    }

    @Test
    public void reauthPruneIsANoOpWhenNothingIsOrphaned() {
        prefs.edit().putString("lasttoken", "tokA").commit();
        Set<String> tokens = new HashSet<>(Arrays.asList("tokA", "tokB"));
        store(new HashSet<>(Arrays.asList("alice:tokA", "bob:tokB")), new HashSet<>(tokens));

        Authentication.pruneOrphanTokens();

        assertEquals(tokens, stored(TOKENS));
    }

    @Test
    public void isEntryForMatchesBothStoredForms() {
        assertTrue(Authentication.isEntryFor("alice:tokA", "alice"));
        assertTrue(Authentication.isEntryFor("alice", "alice"));
    }

    @Test
    public void isEntryForRejectsANameThatIsMerelyASubstring() {
        assertFalse(Authentication.isEntryFor("bobby:tokBobby", "bob"));
        assertFalse(Authentication.isEntryFor("bobby", "bob"));
        assertFalse(Authentication.isEntryFor("alice:tokA", "lice"));
    }

    @Test
    public void isEntryForIgnoresCase() {
        assertTrue(Authentication.isEntryFor("Alice:tokA", "alice"));
        assertTrue(Authentication.isEntryFor("alice:tokA", "ALICE"));
        assertTrue(Authentication.isEntryFor("Alice", "alice"));
    }

    @Test
    public void isEntryForComparesTheNameHalfOnly() {
        // The token half is not a name and must not be searched for one, however it is cased.
        assertFalse(Authentication.isEntryFor("bob:alice", "alice"));
    }

    @Test
    public void signingInWithADifferentlyCasedNameReplacesTheSameEntry() {
        store(
                new HashSet<>(Collections.singletonList("alice:tokA")),
                new HashSet<>(Collections.singletonList("tokA")));

        storeAccountToken("Alice", "tokA2");

        assertEquals(new HashSet<>(Collections.singletonList("Alice:tokA2")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Collections.singletonList("tokA2")), stored(TOKENS));
    }

    @Test
    public void forgettingAnAccountIgnoresCase() {
        store(
                new HashSet<>(Arrays.asList("Alice:tokA", "bob:tokB")),
                new HashSet<>(Arrays.asList("tokA", "tokB")));

        Authentication.forgetAccount("alice");

        assertEquals(new HashSet<>(Collections.singletonList("bob:tokB")), stored(ACCOUNTS));
        assertEquals(new HashSet<>(Collections.singletonList("tokB")), stored(TOKENS));
    }

    @Test
    public void tokenOfReadsTheTokenHalfOfAnEntry() {
        assertEquals("tokA", Authentication.tokenOf("alice:tokA"));
    }

    @Test
    public void tokenOfSplitsOnTheFirstColonOnly() {
        // Reddit refresh tokens contain "-" and "_" rather than ":", but splitting on the first
        // separator is what keeps a token that did contain one intact — and what stops
        // String.split(":")[1] from silently truncating it.
        assertEquals("tok:with:colons", Authentication.tokenOf("alice:tok:with:colons"));
    }

    @Test
    public void tokenOfIsNullForABareName() {
        assertNull(Authentication.tokenOf("alice"));
    }
}
