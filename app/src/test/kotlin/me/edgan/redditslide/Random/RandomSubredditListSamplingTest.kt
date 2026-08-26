package me.edgan.redditslide.Random

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Reading names out of the cached list file.
 *
 * The list is 328k lines and roughly 4.4 MB, so it is scanned as bytes and only the chosen lines are
 * turned into Strings. That is the one piece of this feature with real off-by-one risk, and getting
 * it wrong is quiet — you land on the neighbouring subreddit rather than crashing — so the indexing
 * is pinned here against files small enough to check by hand.
 */
class RandomSubredditListSamplingTest {

    @get:Rule val folder = TemporaryFolder()

    /** A stream factory over a temp file, matching what the production source selector returns. */
    private fun file(contents: String): () -> java.io.InputStream {
        val f: File = folder.newFile().apply { writeBytes(contents.toByteArray()) }
        return { f.inputStream() }
    }

    @Test
    fun countsNewlineTerminatedLines() {
        assertEquals(3, RandomSubredditList.countLines(file("pics\ngifs\naww\n")))
    }

    @Test
    fun countsAFinalLineWithNoTrailingNewline() {
        assertEquals(3, RandomSubredditList.countLines(file("pics\ngifs\naww")))
    }

    @Test
    fun anEmptyFileHasNoLines() {
        assertEquals(0, RandomSubredditList.countLines(file("")))
    }

    @Test
    fun countsASingleUnterminatedLine() {
        assertEquals(1, RandomSubredditList.countLines(file("pics")))
    }

    @Test
    fun readsTheRequestedLinesAndNoOthers() {
        val f = file("a\nb\nc\nd\ne\n")
        assertEquals(listOf("a", "c", "e"), RandomSubredditList.readLines(f, intArrayOf(0, 2, 4)))
    }

    @Test
    fun readsTheFirstLine() {
        val f = file("first\nsecond\nthird\n")
        assertEquals(listOf("first"), RandomSubredditList.readLines(f, intArrayOf(0)))
    }

    @Test
    fun readsTheLastLineWhenItIsNewlineTerminated() {
        val f = file("first\nsecond\nthird\n")
        assertEquals(listOf("third"), RandomSubredditList.readLines(f, intArrayOf(2)))
    }

    @Test
    fun readsTheLastLineWhenItHasNoTrailingNewline() {
        // The hosted lists are LF-terminated, but a truncated or hand-edited cache must not lose
        // its final name or, worse, return it merged with nothing.
        val f = file("first\nsecond\nthird")
        assertEquals(listOf("third"), RandomSubredditList.readLines(f, intArrayOf(2)))
    }

    @Test
    fun readsEveryLineWhenEveryIndexIsAsked() {
        val f = file("a\nb\nc\n")
        assertEquals(listOf("a", "b", "c"), RandomSubredditList.readLines(f, intArrayOf(0, 1, 2)))
    }

    @Test
    fun readsAcrossManyLinesToPickDistantIndices() {
        // Far more lines than the tiny hand-written cases, so a buffer boundary or a stale
        // line-index cannot pass unnoticed.
        val f = file((0 until 5000).joinToString("\n") { "sub$it" } + "\n")
        assertEquals(
            listOf("sub0", "sub1", "sub2499", "sub4998", "sub4999"),
            RandomSubredditList.readLines(f, intArrayOf(0, 1, 2499, 4998, 4999)),
        )
    }

    @Test
    fun stripsCarriageReturnsAndSurroundingSpace() {
        val f = file("pics\r\ngifs\r\n")
        assertEquals(listOf("pics", "gifs"), RandomSubredditList.readLines(f, intArrayOf(0, 1)))
    }

    @Test
    fun anEmptyIndexListReadsNothing() {
        assertTrue(RandomSubredditList.readLines(file("a\nb\n"), intArrayOf()).isEmpty())
    }

    @Test
    fun pickIndicesAreDistinctSortedAndInRange() {
        val indices = RandomSubredditList.pickIndices(1000, 100)
        assertEquals(100, indices.size)
        assertEquals(100, indices.toSet().size)
        assertEquals(indices.sortedArray().toList(), indices.toList())
        assertTrue(indices.all { it in 0 until 1000 })
    }

    @Test
    fun pickIndicesCannotAskForMoreLinesThanExist() {
        // A list shorter than one batch must not spin forever looking for distinct indices.
        val indices = RandomSubredditList.pickIndices(7, 100)
        assertEquals(7, indices.size)
        assertEquals((0..6).toList(), indices.toList())
    }

    @Test
    fun everyPickedIndexResolvesToARealName() {
        // The two halves together: sample indices, then read them back.
        val names = (0 until 500).map { "sub$it" }
        val f = file(names.joinToString("\n") + "\n")
        val read = RandomSubredditList.readLines(f, RandomSubredditList.pickIndices(500, 100))
        assertEquals(100, read.size)
        assertTrue(read.all { it in names })
        assertEquals(100, read.toSet().size)
    }
}
