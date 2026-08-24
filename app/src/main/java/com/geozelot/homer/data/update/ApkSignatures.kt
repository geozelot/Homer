package com.geozelot.homer.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Checks that a downloaded APK is signed by the same key as the installed app, BEFORE handing it
 * to the system installer.
 *
 * Android enforces this itself and would refuse the install regardless — the point of checking
 * here is the message. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` surfaces as an opaque installer error;
 * this turns it into a sentence naming the real cause, which is almost always that the running
 * build was compiled locally (debug key) while the release APK is signed by CI. It also means a
 * download that somehow arrived tampered is discarded rather than offered.
 */
internal object ApkSignatures {

    /**
     * True when [apk] shares a signing certificate with the installed app.
     *
     * Shared rather than identical, so that a future key rotation — where the histories overlap
     * but the sets differ — is not reported as tampering. Android still applies the strict rule at
     * install time; this is the early, legible check, not the authority.
     */
    fun matchesInstalled(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        val installed = fingerprints(installedInfo(pm, context.packageName))
        val candidate = fingerprints(archiveInfo(pm, apk.absolutePath))
        if (installed.isEmpty() || candidate.isEmpty()) {
            // Unreadable either side: say no. Offering an install we could not verify is worse
            // than making the user download it from the release page themselves.
            Log.w(TAG, "could not read signatures (installed=${installed.size} apk=${candidate.size})")
            return false
        }
        return installed.intersect(candidate).isNotEmpty()
    }

    @Suppress("DEPRECATION") // The PackageInfoFlags overloads only exist from API 33.
    private fun installedInfo(pm: PackageManager, pkg: String): PackageInfo? = runCatching {
        pm.getPackageInfo(pkg, signatureFlag())
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? = runCatching {
        pm.getPackageArchiveInfo(path, signatureFlag())
    }.getOrNull()

    private fun signatureFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    private fun fingerprints(info: PackageInfo?): Set<String> {
        if (info == null) return emptySet()
        val signatures: Array<Signature>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.let {
                    // The documented pairing: history is only meaningful for a single signer.
                    if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION") info.signatures
            }
        return signatures.orEmpty().mapNotNull { sha256(it.toByteArray()) }.toSet()
    }

    private fun sha256(bytes: ByteArray): String? = runCatching {
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private const val TAG = "HomerUpdate"
}
