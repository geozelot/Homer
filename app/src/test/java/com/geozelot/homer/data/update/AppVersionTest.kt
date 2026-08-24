package com.geozelot.homer.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ordering [AppVersion] exists to get right. Every case here is one the updater would get
 * wrong with a plain string compare, or one where getting it wrong offers the user a downgrade.
 */
class AppVersionTest {

    private fun v(s: String) = AppVersion.parse(s)

    private fun assertOlder(older: String, newer: String) {
        assertTrue("$older should be older than $newer", v(older) < v(newer))
        assertTrue("$newer should be newer than $older", v(newer) > v(older))
    }

    @Test
    fun `orders by numeric core`() {
        assertOlder("1.0.0", "1.1.0")
        assertOlder("1.1.0", "1.2.0")
        assertOlder("1.9.0", "1.10.0")
        assertOlder("1.1.0", "2.0.0")
        assertOlder("1.1.1", "1.1.2")
    }

    @Test
    fun `a prerelease is older than the release it leads to`() {
        assertOlder("1.1.0-BETA18", "1.1.0")
        assertOlder("1.1.0-rc1", "1.1.0")
    }

    @Test
    fun `beta numbers compare numerically, not lexically`() {
        // The regression this class exists for: "BETA9" > "BETA18" as strings, so a lexical
        // compare stops offering updates at BETA10 and never resumes.
        assertOlder("1.1.0-BETA9", "1.1.0-BETA18")
        assertOlder("1.1.0-BETA17", "1.1.0-BETA18")
        assertOlder("1.1.0-BETA2", "1.1.0-BETA10")
    }

    @Test
    fun `a later core beats any prerelease of an earlier one`() {
        assertOlder("1.1.0-BETA18", "1.2.0-BETA1")
        assertOlder("1.1.0", "1.2.0-BETA1")
    }

    @Test
    fun `the CI dev build sorts below every real release`() {
        assertOlder("0.0.0-dev123", "1.0.0")
        assertOlder("0.0.0-dev123", "1.1.0-BETA1")
    }

    @Test
    fun `a leading v is not part of the version`() {
        assertEquals(v("1.1.0"), v("v1.1.0"))
        assertOlder("v1.1.0-BETA18", "v1.1.0")
    }

    @Test
    fun `trailing zeros do not make a different version`() {
        assertEquals(v("1.1"), v("1.1.0"))
        assertEquals(v("1.1").hashCode(), v("1.1.0").hashCode())
        assertEquals(0, v("1.1").compareTo(v("1.1.0")))
    }

    @Test
    fun `suffix case does not matter`() {
        assertEquals(v("1.1.0-BETA18"), v("1.1.0-beta18"))
        assertEquals(0, v("1.1.0-BETA18").compareTo(v("1.1.0-beta18")))
    }

    @Test
    fun `build metadata is ignored`() {
        assertEquals(v("1.1.0"), v("1.1.0+ci7"))
        assertOlder("1.1.0-BETA1+ci9", "1.1.0")
    }

    @Test
    fun `a shorter suffix ranks before a longer one that extends it`() {
        assertOlder("1.1.0-BETA", "1.1.0-BETA2")
    }

    @Test
    fun `identifies prereleases`() {
        assertTrue(v("1.1.0-BETA18").isPrerelease)
        assertTrue(v("0.0.0-dev1").isPrerelease)
        assertFalse(v("1.1.0").isPrerelease)
        assertFalse(v("1.1.0+ci7").isPrerelease)
    }

    @Test
    fun `equal versions are equal both ways`() {
        assertEquals(v("1.1.0-BETA18"), v("1.1.0-BETA18"))
        assertEquals(0, v("1.1.0").compareTo(v("1.1.0")))
    }

    @Test
    fun `nonsense parses instead of throwing and sorts at the bottom`() {
        assertOlder("", "0.0.1")
        assertOlder("not-a-version", "0.0.1")
        // Two unparseable names must at least be self-consistent.
        assertEquals(0, v("garbage").compareTo(v("garbage")))
    }

    @Test
    fun `the real beta sequence sorts in release order`() {
        val shuffled = listOf("1.1.0", "1.1.0-BETA17", "1.0.0", "1.1.0-BETA9", "1.1.0-BETA18")
        assertEquals(
            listOf("1.0.0", "1.1.0-BETA9", "1.1.0-BETA17", "1.1.0-BETA18", "1.1.0"),
            shuffled.map(::v).sorted().map { it.raw },
        )
    }
}
