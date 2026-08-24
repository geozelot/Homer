package com.geozelot.homer.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.geozelot.homer.di.Bootstrap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Raised when an update could not be downloaded or installed; carries the reason for the UI. */
class UpdateInstallException(val reason: UpdateFailure, message: String) : IOException(message)

/**
 * Downloads a release APK and hands it to the system installer.
 *
 * Installing goes through [PackageInstaller] sessions rather than an `ACTION_VIEW` intent over a
 * `content://` URI. The session API takes the bytes directly, so **no `FileProvider` is needed** —
 * one fewer exported component, and no grant to get wrong. The user still confirms in the system
 * dialog; nothing here installs anything silently, and nothing can.
 *
 * The APK lands in `cacheDir`, which the OS may reclaim. That is the right place for it: it is
 * disposable the moment the install finishes, and a reclaimed file just means downloading again.
 */
@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @Bootstrap private val client: OkHttpClient,
) {
    /**
     * Whether Homer may install apps at all. Android gates this per-app behind a system toggle
     * that only the user can flip, so this is a fact to report, never something to work around.
     */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system screen where the user grants Homer permission to install apps. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Fetches [release]'s APK into the cache and verifies its signature.
     *
     * [onProgress] gets a 0..1 fraction, or null while the size is unknown. A previously completed
     * download of the same release is reused rather than re-fetched.
     */
    suspend fun download(release: UpdateRelease, onProgress: (Float?) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, DIR).apply { mkdirs() }
            val target = File(dir, "homer-${release.version.raw.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")

            // Any other file in here is a download for a release we are no longer offering.
            dir.listFiles()?.forEach { if (it != target) it.delete() }

            if (target.isFile && target.length() > 0 && sizeLooksRight(target, release)) {
                if (!ApkSignatures.matchesInstalled(context, target)) {
                    target.delete()
                    throw UpdateInstallException(UpdateFailure.SIGNATURE_MISMATCH, "cached apk signed by another key")
                }
                onProgress(1f)
                return@withContext target
            }

            val part = File(dir, "${target.name}.part")
            part.delete()
            onProgress(if (release.apkSizeBytes > 0) 0f else null)

            try {
                client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw UpdateInstallException(UpdateFailure.DOWNLOAD, "HTTP ${response.code}")
                    }
                    val body = response.body ?: throw UpdateInstallException(UpdateFailure.DOWNLOAD, "empty body")
                    // Content-Length beats the API's asset size when both are present: it is what
                    // this particular response will actually deliver.
                    val total = body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            var written = 0L
                            var lastReported = -1
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0) {
                                    // Report per whole percent: a progress bar cannot show more,
                                    // and every emission recomposes the settings screen.
                                    val percent = (written * 100 / total).toInt()
                                    if (percent != lastReported) {
                                        lastReported = percent
                                        onProgress(percent / 100f)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: UpdateInstallException) {
                part.delete()
                throw e
            } catch (e: IOException) {
                part.delete()
                throw UpdateInstallException(UpdateFailure.DOWNLOAD, e.message ?: "download failed")
            }

            if (release.apkSizeBytes > 0 && part.length() != release.apkSizeBytes) {
                part.delete()
                throw UpdateInstallException(
                    UpdateFailure.DOWNLOAD,
                    "expected ${release.apkSizeBytes} bytes, got ${part.length()}",
                )
            }

            // Rename last, so a target file existing always means a complete download — the same
            // rule the book downloader follows.
            if (!part.renameTo(target)) {
                part.delete()
                throw UpdateInstallException(UpdateFailure.DOWNLOAD, "could not finalise the download")
            }

            if (!ApkSignatures.matchesInstalled(context, target)) {
                target.delete()
                throw UpdateInstallException(UpdateFailure.SIGNATURE_MISMATCH, "apk signed by another key")
            }

            onProgress(1f)
            target
        }

    /**
     * Streams [apk] into a [PackageInstaller] session and commits it. Returns once the session is
     * committed — the confirmation dialog and the outcome arrive at [InstallResultReceiver].
     */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        if (!canInstallPackages()) {
            throw UpdateInstallException(UpdateFailure.INSTALL_NOT_ALLOWED, "install permission not granted")
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }

        val sessionId = try {
            installer.createSession(params)
        } catch (e: IOException) {
            throw UpdateInstallException(UpdateFailure.INSTALL_FAILED, e.message ?: "could not open a session")
        }

        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite(SESSION_ENTRY, 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output, BUFFER_BYTES) }
                    // Without fsync the session can commit an incomplete image.
                    session.fsync(output)
                }
                session.commit(resultSender(sessionId))
            }
        } catch (e: IOException) {
            runCatching { installer.abandonSession(sessionId) }
            throw UpdateInstallException(UpdateFailure.INSTALL_FAILED, e.message ?: "install failed")
        }
        Log.i(TAG, "install session $sessionId committed")
    }

    private fun resultSender(sessionId: Int): android.content.IntentSender {
        val intent = Intent(context, InstallResultReceiver::class.java)
            .setAction(InstallResultReceiver.ACTION_INSTALL_RESULT)
            .setPackage(context.packageName)
        // MUTABLE is required: the system fills in the status extras on this intent.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
    }

    private fun sizeLooksRight(file: File, release: UpdateRelease): Boolean =
        release.apkSizeBytes <= 0 || file.length() == release.apkSizeBytes

    private companion object {
        const val TAG = "HomerUpdate"
        const val DIR = "updates"
        const val SESSION_ENTRY = "homer"
        const val BUFFER_BYTES = 64 * 1024
    }
}
