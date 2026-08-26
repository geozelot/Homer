package com.geozelot.homer.data.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Covers [safeStorageSegment] — the deterministic name mapping that lets Homer write to public
 * (FUSE/vfat) volumes that reject characters the app-private dir tolerates. It must be stable
 * (same input → same output) so a file written can always be found again.
 */
class StorageAreaTest {

    @Test
    fun `a legal name is unchanged`() {
        assertEquals("Author Name - Book 1", safeStorageSegment("Author Name - Book 1"))
    }

    @Test
    fun `illegal volume characters become underscores`() {
        // colon, star, question, quote, angle brackets, pipe between a..h, then a trailing
        // backslash (which maps to a trailing underscore — trimEnd only strips spaces/dots).
        assertEquals("a_b_c_d_e_f_g_h_", safeStorageSegment("a:b*c?d\"e<f>g|h\\"))
    }

    @Test
    fun `a colon in a real title is sanitized`() {
        assertEquals("Leviathan erwacht_ The Expanse", safeStorageSegment("Leviathan erwacht: The Expanse"))
    }

    @Test
    fun `control characters become underscores`() {
        assertEquals("a_b", safeStorageSegment("ab"))
    }

    @Test
    fun `trailing dots and spaces are trimmed`() {
        assertEquals("name", safeStorageSegment("name.  "))
    }

    @Test
    fun `a name that reduces to nothing falls back to underscore`() {
        assertEquals("_", safeStorageSegment("..."))
        assertEquals("_", safeStorageSegment("   "))
    }

    @Test
    fun `mapping is deterministic`() {
        val input = "Weird: name?*"
        assertEquals(safeStorageSegment(input), safeStorageSegment(input))
    }
}

/**
 * Covers [finalizeByCopy] — the fallback for a `.part` rename that will not go.
 *
 * A rename can fail for reasons `File.renameTo` refuses to name, and one of them turned out to be
 * routine: cancelling a download deletes the book folder while the uninterruptible byte copy is
 * still running, so the finalize lands on a folder that no longer exists. That case must NOT be
 * recovered — recreating the folder would undo the cancel.
 */
class FinalizeByCopyTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `copies the bytes into place and drops the part file`() {
        val dir = temp.newFolder("book")
        val part = File(dir, "chapter.mp3.part").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val target = File(dir, "chapter.mp3")

        finalizeByCopy("downloads/book/chapter.mp3", part, target)

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), target.readBytes())
        assertFalse("the .part must not survive a successful finalize", part.exists())
    }

    @Test
    fun `overwrites an existing target rather than failing`() {
        val dir = temp.newFolder("book")
        val part = File(dir, "chapter.mp3.part").apply { writeBytes(byteArrayOf(9, 9)) }
        val target = File(dir, "chapter.mp3").apply { writeBytes(byteArrayOf(1, 1, 1, 1, 1, 1)) }

        finalizeByCopy("downloads/book/chapter.mp3", part, target)

        assertArrayEquals(byteArrayOf(9, 9), target.readBytes())
    }

    @Test
    fun `a folder deleted mid-write is reported and NOT recreated`() {
        // Exactly what cancelling a download from the notification does: cancelUniqueWork, then
        // deleteBook wipes the folder while the worker's blocking copy runs on.
        val dir = temp.newFolder("book")
        val part = File(dir, "chapter.mp3.part").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val target = File(dir, "chapter.mp3")
        dir.deleteRecursively()

        val thrown = runCatching { finalizeByCopy("downloads/book/chapter.mp3", part, target) }.exceptionOrNull()

        assertTrue("expected an IOException, got $thrown", thrown is IOException)
        assertTrue(
            "the message should say the folder went away: ${thrown?.message}",
            thrown?.message?.contains("removed while it was being written") == true,
        )
        assertFalse("the cancelled book's folder must stay deleted", dir.exists())
    }

    @Test
    fun `a copy that cannot start leaves no half-written target and says what it saw`() {
        val dir = temp.newFolder("book")
        val part = File(dir, "chapter.mp3.part").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        // A directory where the file should go: opening it for writing fails, which stands in for
        // any backend that accepts the folder but refuses the file.
        val target = File(dir, "chapter.mp3").apply { mkdirs() }

        val thrown = runCatching { finalizeByCopy("downloads/book/chapter.mp3", part, target) }.exceptionOrNull()

        assertTrue("expected an IOException, got $thrown", thrown is IOException)
        // The diagnostics are the whole point: the old message was "could not finalize <path>".
        assertTrue(
            "the message should describe what it found: ${thrown?.message}",
            thrown?.message?.contains("target=a directory") == true &&
                thrown?.message?.contains("part=3 bytes") == true,
        )
    }
}
