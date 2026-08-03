package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import me.edgan.redditslide.util.PrefUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * {@link PrefUtil} wraps the two {@link SharedPreferences} reads that are declared {@code @Nullable}
 * on the way out because they take a {@code @Nullable} default — an annotation cannot say "non-null
 * when the default is". The wrappers supply that conditional.
 *
 * <p>Nothing below reaches the wrappers' own null branch, and nothing can: AOSP returns the default
 * for an absent key, and {@code putString(key, null)} removes the key rather than storing a null.
 * Making the branch throw leaves every test here green. What these tests pin is the contract callers
 * rely on — a non-null default always comes back — across the ways a key can be missing or empty.
 *
 * <p>{@code getMutableStringSet} additionally exists because {@code getStringSet} hands back the
 * platform's own set instance, which Android documents as leaving the stored data undefined if you
 * modify it. Mutating it in place appears to work — later reads in the same process get the same
 * cached instance and see the change — so the copy semantics are what these tests pin down.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class PrefUtilTest {

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs =
                ((Context) ApplicationProvider.getApplicationContext())
                        .getSharedPreferences("PrefUtilTest", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    // ---------- getString ----------

    @Test
    public void getString_returnsStoredValue() {
        prefs.edit().putString("k", "stored").commit();
        assertEquals("stored", PrefUtil.getString(prefs, "k", "fallback"));
    }

    @Test
    public void getString_returnsDefaultWhenKeyAbsent() {
        assertEquals("fallback", PrefUtil.getString(prefs, "missing", "fallback"));
    }

    @Test
    public void getString_putNullRemovesTheKeyRatherThanStoringNull() {
        // putString(key, null) is a removal, not a stored null, so the read falls to the default.
        prefs.edit().putString("k", "stored").commit();
        prefs.edit().putString("k", null).commit();
        assertEquals("fallback", PrefUtil.getString(prefs, "k", "fallback"));
    }

    @Test
    public void getString_emptyStoredValueIsNotTreatedAsAbsent() {
        prefs.edit().putString("k", "").commit();
        assertEquals("", PrefUtil.getString(prefs, "k", "fallback"));
    }

    // ---------- getStringSet ----------

    @Test
    public void getStringSet_returnsStoredValue() {
        prefs.edit().putStringSet("k", new HashSet<>(Arrays.asList("a", "b"))).commit();
        assertEquals(
                new HashSet<>(Arrays.asList("a", "b")),
                PrefUtil.getStringSet(prefs, "k", Collections.emptySet()));
    }

    @Test
    public void getStringSet_putNullRemovesTheKeyRatherThanStoringNull() {
        prefs.edit().putStringSet("k", new HashSet<>(Arrays.asList("a", "b"))).commit();
        prefs.edit().putStringSet("k", null).commit();
        Set<String> fallback = new HashSet<>(Collections.singletonList("d"));
        assertEquals(fallback, PrefUtil.getStringSet(prefs, "k", fallback));
    }

    // ---------- getMutableStringSet ----------

    @Test
    public void getMutableStringSet_hasSameContentsAsStored() {
        prefs.edit().putStringSet("k", new HashSet<>(Arrays.asList("a", "b"))).commit();
        assertEquals(
                new HashSet<>(Arrays.asList("a", "b")),
                PrefUtil.getMutableStringSet(prefs, "k", new HashSet<>()));
    }

    @Test
    public void getMutableStringSet_isModifiable() {
        prefs.edit().putStringSet("k", new HashSet<>(Collections.singletonList("a"))).commit();
        Set<String> copy = PrefUtil.getMutableStringSet(prefs, "k", new HashSet<>());
        copy.remove("a");
        copy.add("b");
        assertEquals(new HashSet<>(Collections.singletonList("b")), copy);
    }

    @Test
    public void getMutableStringSet_isNotTheStoredInstance() {
        prefs.edit().putStringSet("k", new HashSet<>(Collections.singletonList("a"))).commit();
        assertNotSame(
                "must hand back a copy, not the platform's cached instance",
                PrefUtil.getStringSet(prefs, "k", new HashSet<>()),
                PrefUtil.getMutableStringSet(prefs, "k", new HashSet<>()));
    }

    @Test
    public void getMutableStringSet_mutationDoesNotLeakIntoStoredValue() {
        // The actual defect getMutableStringSet exists to prevent: mutating the returned set must
        // not reach the stored data. Only an explicit putStringSet may change it.
        prefs.edit().putStringSet("k", new HashSet<>(Collections.singletonList("a"))).commit();

        Set<String> copy = PrefUtil.getMutableStringSet(prefs, "k", new HashSet<>());
        copy.remove("a");
        copy.add("a:token");

        assertEquals(
                "stored set must be untouched until it is written back",
                new HashSet<>(Collections.singletonList("a")),
                PrefUtil.getStringSet(prefs, "k", new HashSet<>()));

        prefs.edit().putStringSet("k", copy).commit();
        assertEquals(
                new HashSet<>(Collections.singletonList("a:token")),
                PrefUtil.getStringSet(prefs, "k", new HashSet<>()));
    }

    @Test
    public void getMutableStringSet_copiesTheDefaultWhenKeyAbsent() {
        Set<String> fallback = new HashSet<>(Collections.singletonList("d"));
        Set<String> copy = PrefUtil.getMutableStringSet(prefs, "missing", fallback);
        copy.add("extra");
        assertTrue("mutating the result must not modify the caller's default", fallback.size() == 1);
    }
}
