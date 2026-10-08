package com.geozelot.homer.data.auth

import java.util.Base64
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * How credentials are sealed, with the production [AesGcmCipher] under an ordinary key.
 *
 * The Keystore is the one part a JVM cannot have, and it only supplies the key — everything about
 * the format, the slot binding and what refuses to open is tested here. The instrumented
 * `KeystoreCredentialStoreTest` covers the Keystore itself.
 */
class CredentialCodecTest {

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmCipher { key }
    private val codec = CredentialCodec(cipher)

    private val account = NextcloudCredentials("https://cloud.example.com", "alice", "app-pass-123", WebDavKind.ACCOUNT)
    private val share = NextcloudCredentials("https://cloud.example.com", "AbCdToken", "", WebDavKind.SHARE)

    @Test
    fun `an account round-trips`() {
        assertEquals(account, codec.open("library", codec.seal("library", account)))
    }

    @Test
    fun `a share keeps its kind and its empty password`() {
        assertEquals(share, codec.open("library", codec.seal("library", share)))
    }

    @Test
    fun `a password in any script round-trips`() {
        val unusual = account.copy(appPassword = "pässwört-密码-🔑")
        assertEquals(unusual, codec.open("sync", codec.seal("sync", unusual)))
    }

    @Test
    fun `the same credentials seal differently every time`() {
        // A fresh IV per value: equal ciphertexts would say two slots hold the same secret.
        assertNotEquals(codec.seal("library", account), codec.seal("library", account))
    }

    @Test
    fun `a value does not open in a slot it was not sealed for`() {
        val library = codec.seal("library", account)
        assertThrows(Exception::class.java) { codec.open("sync", library) }
    }

    @Test
    fun `a damaged value does not open`() {
        val bytes = Base64.getDecoder().decode(codec.seal("library", account))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { codec.open("library", Base64.getEncoder().encodeToString(bytes)) }
    }

    @Test
    fun `a value in a format this build does not know is refused rather than guessed`() {
        val bytes = Base64.getDecoder().decode(codec.seal("library", account))
        bytes[0] = 99
        assertThrows(IllegalArgumentException::class.java) {
            codec.open("library", Base64.getEncoder().encodeToString(bytes))
        }
    }

    @Test
    fun `a kind this build does not know reads as an account`() {
        val json = """{"server":"https://c","login":"a","password":"p","kind":"SOMETHING_NEWER"}"""
        val sealed = cipher.seal(json.toByteArray(), "library".toByteArray())
        val value = Base64.getEncoder().encodeToString(byteArrayOf(1) + sealed)
        assertEquals(WebDavKind.ACCOUNT, codec.open("library", value).kind)
    }
}
