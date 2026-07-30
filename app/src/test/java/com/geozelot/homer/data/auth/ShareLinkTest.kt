package com.geozelot.homer.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers [ShareLink.parse] — turning a pasted Nextcloud share URL into origin + token. */
class ShareLinkTest {

    @Test
    fun `parses a standard share url`() {
        val link = ShareLink.parse("https://cloud.example.com/s/AbC123xyz")
        assertEquals(ShareLink("https://cloud.example.com", "AbC123xyz"), link)
    }

    @Test
    fun `parses the index_php form`() {
        val link = ShareLink.parse("https://cloud.example.com/index.php/s/AbC123xyz")
        assertEquals(ShareLink("https://cloud.example.com", "AbC123xyz"), link)
    }

    @Test
    fun `adds https when the scheme is missing`() {
        val link = ShareLink.parse("cloud.example.com/s/tok")
        assertEquals(ShareLink("https://cloud.example.com", "tok"), link)
    }

    @Test
    fun `forces https on an http url`() {
        val link = ShareLink.parse("http://cloud.example.com/s/tok")
        assertEquals(ShareLink("https://cloud.example.com", "tok"), link)
    }

    @Test
    fun `keeps a non-default port`() {
        val link = ShareLink.parse("https://cloud.example.com:8443/s/tok")
        assertEquals(ShareLink("https://cloud.example.com:8443", "tok"), link)
    }

    @Test
    fun `ignores trailing path after the token`() {
        val link = ShareLink.parse("https://cloud.example.com/s/tok/download")
        assertEquals(ShareLink("https://cloud.example.com", "tok"), link)
    }

    @Test
    fun `returns null when there is no share segment`() {
        assertNull(ShareLink.parse("https://cloud.example.com/apps/files"))
        assertNull(ShareLink.parse("https://cloud.example.com/s/"))
        assertNull(ShareLink.parse(""))
        assertNull(ShareLink.parse("not a url"))
    }
}
