package com.geozelot.homer.data.auth

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One set of credentials as one sealed string: JSON, sealed by [cipher] under the slot's name, with
 * a format byte in front, in Base64.
 *
 * The slot name is the associated data, so the library's credentials cannot be passed off as the
 * sync account's by copying one value over the other. The format byte is there so a later change to
 * any of this can tell its own values from these rather than guessing.
 */
class CredentialCodec(private val cipher: SecretCipher) {

    fun seal(slot: String, credentials: NextcloudCredentials): String {
        val json = Json.encodeToString(
            Stored.serializer(),
            Stored(credentials.serverUrl, credentials.loginName, credentials.appPassword, credentials.kind.name),
        )
        val sealed = cipher.seal(json.toByteArray(Charsets.UTF_8), slot.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(byteArrayOf(FORMAT) + sealed)
    }

    /**
     * What [seal] wrote for [slot]. Throws when it cannot be opened — a value from another slot, a
     * damaged one, or a key the Keystore no longer has — which the store treats as being signed out.
     */
    fun open(slot: String, value: String): NextcloudCredentials {
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.isNotEmpty() && bytes[0] == FORMAT) { "unknown credential format" }
        val plain = cipher.open(bytes.copyOfRange(1, bytes.size), slot.toByteArray(Charsets.UTF_8))
        val stored = json.decodeFromString(Stored.serializer(), plain.toString(Charsets.UTF_8))
        return NextcloudCredentials(
            serverUrl = stored.server,
            loginName = stored.login,
            appPassword = stored.password,
            // Unknown to this build means a newer one wrote it; an account is the safe reading,
            // and the same default the old store gave a value written before shares existed.
            kind = runCatching { WebDavKind.valueOf(stored.kind) }.getOrDefault(WebDavKind.ACCOUNT),
        )
    }

    /** The JSON shape — kept apart from [NextcloudCredentials] so renaming a field there breaks nothing stored. */
    @Serializable
    private data class Stored(val server: String, val login: String, val password: String, val kind: String)

    private companion object {
        const val FORMAT: Byte = 1
        val json = Json { ignoreUnknownKeys = true }
    }
}
