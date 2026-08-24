package com.geozelot.homer.data.update

import android.util.Log
import com.geozelot.homer.BuildConfig
import com.geozelot.homer.di.Bootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Raised when GitHub answered, but refused. Distinguishes "can't check" from "nothing new". */
class UpdateCheckException(val reason: UpdateFailure, message: String) : IOException(message)

/**
 * The newest release on a channel, whether or not it is installable.
 *
 * [release] is null when that release has no APK attached. It is reported rather than skipped so
 * the caller can say "the newest release has no APK" instead of silently offering the previous one
 * — but only once it has established the release is newer than the running build, which is why
 * this type exists rather than the source throwing on the spot.
 */
data class NewestRelease(val version: AppVersion, val release: UpdateRelease?)

/**
 * Reads Homer's own GitHub releases to find the newest build worth offering.
 *
 * Uses the [Bootstrap] (unauthenticated) client on purpose: this talks to a third party, and the
 * [com.geozelot.homer.di.Authed] client would attach the Nextcloud credentials to every request.
 * Nothing identifying is sent — no version, no install id, no account — so the request says only
 * that somebody asked what the latest release is.
 *
 * The whole list is fetched rather than `/releases/latest`, because that endpoint always skips
 * pre-releases and the beta channel needs to see them. The newest release is picked by comparing
 * [AppVersion]s rather than by trusting the list order, which is by creation date: re-tagging or a
 * late edit to an old release would otherwise reorder it.
 */
@Singleton
class GitHubReleaseSource @Inject constructor(
    @Bootstrap private val client: OkHttpClient,
    private val json: Json,
) {
    @Serializable
    private data class ApiRelease(
        val tag_name: String = "",
        val name: String? = null,
        val prerelease: Boolean = false,
        val draft: Boolean = false,
        val body: String? = null,
        val html_url: String = "",
        val assets: List<ApiAsset> = emptyList(),
    )

    @Serializable
    private data class ApiAsset(
        val name: String = "",
        val browser_download_url: String = "",
        val size: Long = 0,
    )

    /**
     * The newest release on [channel], or null when the channel has none at all.
     *
     * @throws UpdateCheckException when the answer could not be obtained — never confuse that with
     *   "you are up to date", which is what a null here means.
     */
    suspend fun newestRelease(channel: UpdateChannel): NewestRelease? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=$PAGE_SIZE"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            // GitHub requires a User-Agent. Deliberately carries no version and no install id —
            // the check is anonymous, and the comparison happens on this device.
            .header("User-Agent", "Homer-Android")
            .build()

        val body = try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> response.body?.string()
                    // GitHub reports the hourly cap as 403 (older) or 429 (newer).
                    response.code == 403 || response.code == 429 ->
                        throw UpdateCheckException(UpdateFailure.RATE_LIMITED, "HTTP ${response.code}")
                    else ->
                        throw UpdateCheckException(UpdateFailure.NETWORK, "HTTP ${response.code}")
                }
            }
        } catch (e: UpdateCheckException) {
            throw e
        } catch (e: IOException) {
            throw UpdateCheckException(UpdateFailure.NETWORK, e.message ?: "network failure")
        } ?: throw UpdateCheckException(UpdateFailure.NETWORK, "empty response")

        val releases = try {
            json.decodeFromString<List<ApiRelease>>(body)
        } catch (e: Exception) {
            Log.w(TAG, "could not parse the release list", e)
            throw UpdateCheckException(UpdateFailure.NETWORK, "unreadable response")
        }

        // The newest release is chosen BEFORE asking whether it has an APK, so that a release
        // whose upload failed is reported rather than skipped. Falling through to the previous one
        // would tell the user they were up to date while a newer version existed.
        val newest = releases
            .filterNot { it.draft }
            .filter { channel == UpdateChannel.BETA || !it.prerelease }
            .maxByOrNull { AppVersion.parse(it.tag_name) }
            ?: return@withContext null

        NewestRelease(AppVersion.parse(newest.tag_name), newest.toUpdateRelease())
    }

    /** Null when the release carries no APK — a source-only tag, or a CI upload that failed. */
    private fun ApiRelease.toUpdateRelease(): UpdateRelease? {
        val apk = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        if (apk.browser_download_url.isBlank()) return null
        return UpdateRelease(
            version = AppVersion.parse(tag_name),
            tag = tag_name,
            prerelease = prerelease,
            notes = body?.trim()?.ifBlank { null },
            apkUrl = apk.browser_download_url,
            apkSizeBytes = apk.size,
            pageUrl = html_url,
        )
    }

    private companion object {
        const val TAG = "HomerUpdate"

        /** Comfortably more than the betas cut between two stable releases. */
        const val PAGE_SIZE = 30
    }
}
