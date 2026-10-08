package com.geozelot.homer.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals and opens small secrets, binding each to the [associatedData] it was sealed under — so a
 * value copied into a different slot does not open there.
 */
interface SecretCipher {
    fun seal(plain: ByteArray, associatedData: ByteArray): ByteArray
    fun open(sealed: ByteArray, associatedData: ByteArray): ByteArray
}

/**
 * AES-256-GCM, with a fresh 96-bit IV per value written in front of the ciphertext.
 *
 * The key comes from [key], which is the whole of the difference between the app and its tests:
 * on a device it lives in the Android Keystore and never leaves it ([keystoreKey]); in a JVM test it
 * is an ordinary key, and the sealing code under test is this same class.
 */
class AesGcmCipher(private val key: () -> SecretKey) : SecretCipher {

    override fun seal(plain: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // No IV passed: the provider generates one, which is the only way the Keystore allows it.
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "unexpected IV length ${iv.size}" }
        return iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray, associatedData: ByteArray): ByteArray {
        require(sealed.size > IV_BYTES) { "too short to be sealed" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * The AES key under [alias] in the Android Keystore, made on first use.
 *
 * Non-exportable by construction: the key material never enters this process, and every seal and
 * open happens inside the Keystore. No user-authentication requirement, deliberately — Homer's own
 * app lock is a separate, optional gate, and tying the key to it would make every background sync
 * fail while the phone is locked.
 */
fun keystoreKey(alias: String): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build(),
    )
    return generator.generateKey()
}

/** Removes the Keystore entry under [alias], if there is one. */
fun deleteKeystoreKey(alias: String) {
    KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias)
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
