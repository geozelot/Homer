package com.geozelot.homer.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test

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
