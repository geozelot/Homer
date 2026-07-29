package com.geozelot.homer.data.webdav

import com.geozelot.homer.data.auth.NextcloudCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers [WebDavClient.relativePathFromHref] — the decoder that turns a server-supplied PROPFIND
 * href into a storage-relative path. The path-traversal rejection is security-relevant: a
 * malicious/compromised server must not be able to smuggle a `..` that escapes the download root.
 */
class WebDavClientTest {

    private val creds = NextcloudCredentials(
        serverUrl = "https://cloud.example.com",
        loginName = "alice",
        appPassword = "secret",
    )

    @Test
    fun `strips the marker and username, leaving the library-relative path`() {
        val href = "/remote.php/dav/files/alice/Audiobooks/Author/Book/01.mp3"
        assertEquals("Audiobooks/Author/Book/01.mp3", WebDavClient.relativePathFromHref(href, creds))
    }

    @Test
    fun `decodes percent-encoding`() {
        val href = "/remote.php/dav/files/alice/Author%20Name/Book%20One/01.mp3"
        assertEquals("Author Name/Book One/01.mp3", WebDavClient.relativePathFromHref(href, creds))
    }

    @Test
    fun `the files root itself maps to the empty string`() {
        val href = "/remote.php/dav/files/alice/"
        assertEquals("", WebDavClient.relativePathFromHref(href, creds))
    }

    @Test
    fun `an href without the files marker is rejected`() {
        assertNull(WebDavClient.relativePathFromHref("/some/other/path", creds))
    }

    @Test
    fun `a parent-traversal segment is rejected`() {
        val href = "/remote.php/dav/files/alice/../../etc/passwd"
        assertNull(WebDavClient.relativePathFromHref(href, creds))
    }

    @Test
    fun `a traversal buried mid-path is rejected`() {
        val href = "/remote.php/dav/files/alice/Author/../../../escape/x.mp3"
        assertNull(WebDavClient.relativePathFromHref(href, creds))
    }

    @Test
    fun `a current-dir segment is rejected`() {
        val href = "/remote.php/dav/files/alice/Author/./Book/01.mp3"
        assertNull(WebDavClient.relativePathFromHref(href, creds))
    }
}
