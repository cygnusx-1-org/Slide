package me.edgan.redditslide.Random

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A list is read in two passes — count the lines, then pull out the chosen ones — and a refresh can
 * rename a freshly downloaded list over the same path in between. These pin that the two passes see
 * one file, because the failure is silent: counting a longer list and reading a shorter one just
 * returns fewer names than asked for, with nothing logged and nothing thrown.
 */
class RandomSubredditListPinnedSourceTest {

    @get:Rule val folder = TemporaryFolder()

    private fun lines(vararg names: String) = names.joinToString("\n") + "\n"

    /** Replaces [path] the way a completed download does: write a sibling, rename over the top. */
    private fun replaceByRename(path: File, contents: String) {
        val temp = File(path.path + ".tmp")
        temp.writeBytes(contents.toByteArray())
        check(temp.renameTo(path)) { "rename failed" }
    }

    @Test
    fun bothPassesSeeOneFileWhenTheListIsReplacedBetweenThem() {
        val path = folder.newFile()
        path.writeBytes(lines("alpha", "bravo", "charlie", "delta").toByteArray())

        val read =
            RandomSubredditList.withPinnedFile(path) { open ->
                val counted = RandomSubredditList.countLines(open)

                // The refresh lands here, between the count and the read.
                replaceByRename(path, lines("zulu"))

                counted to RandomSubredditList.readLines(open, intArrayOf(0, 1, 2, 3))
            }

        assertEquals(4, read.first)
        assertEquals(listOf("alpha", "bravo", "charlie", "delta"), read.second)
    }

    @Test
    fun everyRequestedIndexStillResolvesAfterAReplacement() {
        // The symptom the pinning prevents: indices counted against the old list falling past the
        // end of a shorter new one and coming back as a short batch.
        val path = folder.newFile()
        path.writeBytes((0 until 200).joinToString("\n") { "sub$it" }.plus("\n").toByteArray())

        val names =
            RandomSubredditList.withPinnedFile(path) { open ->
                val counted = RandomSubredditList.countLines(open)
                replaceByRename(path, lines("only-one-left"))
                RandomSubredditList.readLines(open, intArrayOf(0, 99, counted - 1))
            }

        assertEquals(listOf("sub0", "sub99", "sub199"), names)
    }

    @Test
    fun reopeningByPathIsWhatWouldHaveBrokenIt() {
        // The same sequence against a by-path factory, to show the pinning is load-bearing and not
        // an accident of how the readers happen to be ordered.
        val path = folder.newFile()
        path.writeBytes(lines("alpha", "bravo", "charlie", "delta").toByteArray())

        val byPath = { path.inputStream() }
        val counted = RandomSubredditList.countLines(byPath)
        replaceByRename(path, lines("zulu"))
        val names = RandomSubredditList.readLines(byPath, intArrayOf(0, 1, 2, 3))

        assertEquals(4, counted)
        assertNotEquals(listOf("alpha", "bravo", "charlie", "delta"), names)
        assertEquals(listOf("zulu"), names)
    }

    @Test
    fun theSourceStillReadsNormallyWithNoReplacement() {
        val path = folder.newFile()
        path.writeBytes(lines("one", "two", "three").toByteArray())

        val result =
            RandomSubredditList.withPinnedFile(path) { open ->
                RandomSubredditList.countLines(open) to
                    RandomSubredditList.readLines(open, intArrayOf(0, 2))
            }

        assertEquals(3, result.first)
        assertEquals(listOf("one", "three"), result.second)
    }

    @Test
    fun openingAVanishedFileFailsRatherThanReadingNothing() {
        // withPinnedFile itself has no fallback — withSource is what reaches for the bundled asset
        // when the cache directory has been cleared. This pins that the open is where it fails, so
        // that fallback stays reachable and a half-run block is never retried.
        val gone = File(folder.root, "never-existed.txt")
        try {
            RandomSubredditList.withPinnedFile(gone) { open ->
                RandomSubredditList.countLines(open)
            }
            throw AssertionError("expected the open to fail")
        } catch (expected: java.io.FileNotFoundException) {
            // exactly what withSource catches
        }
    }

    @Test
    fun theDescriptorSurvivesRepeatedPasses() {
        // countLines and readLines each close what they are handed; a shared descriptor must
        // outlive both, and a third pass must still work.
        val path = folder.newFile()
        path.writeBytes(lines("a", "b", "c").toByteArray())

        val counts =
            RandomSubredditList.withPinnedFile(path) { open ->
                listOf(
                    RandomSubredditList.countLines(open),
                    RandomSubredditList.countLines(open),
                    RandomSubredditList.countLines(open),
                )
            }

        assertEquals(listOf(3, 3, 3), counts)
    }
}
