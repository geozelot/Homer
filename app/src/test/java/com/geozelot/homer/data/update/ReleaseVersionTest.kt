package com.geozelot.homer.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a release's version actually comes from.
 *
 * The beta tag is moved rather than re-cut, so `v2.0.0-BETA` labels every beta build. CI puts the
 * rising version in the release title and keeps the asset name stable per tag, so each rebuild
 * replaces the APK. The updater has to read the title or it will never see a newer beta.
 */
class ReleaseVersionTest {

    /** What CI actually produces for a moving beta tag. */
    private fun beta(run: Int) = releaseVersion("2.0.0-BETA.$run", "homer-2.0.0-BETA.apk", "v2.0.0-BETA")

    @Test
    fun `the release title decides, not the tag`() {
        // The case the moving tag creates: same tag, same asset name, later build.
        assertEquals(AppVersion.parse("2.0.0-BETA.42"), beta(42))
    }

    @Test
    fun `successive beta builds under one tag order correctly`() {
        // Lexically "9" > "10"; numerically it is the other way, which is the whole point.
        assertTrue(beta(9) < beta(10))
    }

    @Test
    fun `a beta never outranks the release it leads to`() {
        assertTrue(beta(99) < releaseVersion("2.0.0", "homer-2.0.0.apk", "v2.0.0"))
    }

    @Test
    fun `the 2_0_0 beta outranks the 1_1_0 betas it replaces`() {
        // Devices in the field are on 1.1.0-BETA23; they must be offered the 2.0 line.
        assertTrue(AppVersion.parse("1.1.0-BETA23") < beta(1))
    }

    @Test
    fun `the asset name is used when the title is not a version`() {
        // A hand-edited release title must not silently become the version.
        assertEquals(
            AppVersion.parse("2.0.0-BETA.7"),
            releaseVersion("Second beta", "homer-2.0.0-BETA.7.apk", "v2.0.0-BETA"),
        )
    }

    @Test
    fun `the tag is the last resort`() {
        assertEquals(AppVersion.parse("2.0.0"), releaseVersion(null, "Homer-release.apk", "v2.0.0"))
        assertEquals(AppVersion.parse("2.0.0"), releaseVersion("", "homer-2.0.0.zip", "v2.0.0"))
        assertEquals(AppVersion.parse("2.0.0"), releaseVersion(null, null, "v2.0.0"))
        assertEquals(AppVersion.parse("2.0.0"), releaseVersion("Homer", "homer-.apk", "v2.0.0"))
    }

    @Test
    fun `a capitalised extension still parses`() {
        assertEquals(
            AppVersion.parse("2.0.0-BETA.7"),
            releaseVersion(null, "homer-2.0.0-BETA.7.APK", "v2.0.0-BETA"),
        )
    }

    @Test
    fun `a v prefix on the title is tolerated`() {
        assertEquals(AppVersion.parse("2.0.0"), releaseVersion("v2.0.0", "homer-2.0.0.apk", "v2.0.0"))
    }
}
