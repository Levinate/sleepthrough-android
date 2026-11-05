package com.sleepthrough.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.getcapacitor.*
import com.getcapacitor.annotation.CapacitorPlugin
import java.security.KeyStore
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

@CapacitorPlugin(name = "SecureVault")
class SecureVaultPlugin : Plugin() {
    private val wrappingAlias = "sleepthrough-vault-wrap-v2"
    private val oaep = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    private fun wrappingStore(create: Boolean): KeyStore {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(wrappingAlias)) {
            if (!create || preferences().all.values.any { it is String && it.startsWith("v2:") }) throw VaultMissingKey()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(wrappingAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1).build())
            }.generateKeyPair()
            store.load(null)
        }
        return store
    }
    private fun decodeV2(value: String): String {
        val parts = value.split(":")
        if (parts.size != 4) throw VaultInvalidFormat()
        val wrapped = Base64.decode(parts[1], Base64.NO_WRAP)
        val iv = Base64.decode(parts[2], Base64.NO_WRAP)
        val bytes = Base64.decode(parts[3], Base64.NO_WRAP)
        if (iv.size != 12 || bytes.size < 16 || wrapped.size != 256) throw VaultInvalidFormat()
        val unwrap = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        at("unwrap_init") { unwrap.init(Cipher.DECRYPT_MODE, wrappingStore(false).getKey(wrappingAlias, null), oaep) }
        val raw = at("unwrap_finish") { unwrap.doFinal(wrapped) }
        try {
            if (raw.size != 32) throw VaultInvalidFormat()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(raw, "AES"), GCMParameterSpec(128, iv))
            return String(at("data_decrypt") { cipher.doFinal(bytes) }, Charsets.UTF_8)
        } finally { raw.fill(0) }
    }
    private inline fun <T> at(step: String, operation: () -> T): T = try { operation() } catch(e: Exception) { throw VaultStep(step,e) }
    private fun preferences() = context.applicationContext.getSharedPreferences("sleepthrough-encrypted", 0)
    private fun key(create: Boolean): SecretKey {
        val store = at("key_load") { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }
        val existing = at("key_get") { store.getKey("sleepthrough-vault-v1", null) }
        if (existing != null) return existing as SecretKey
        // Never replace a missing key when ciphertext still depends on it.
        if (!create || preferences().all.isNotEmpty()) throw VaultMissingKey()
        return at("key_create") { KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("sleepthrough-vault-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey() }
    }
    private fun reject(call: PluginCall, error: Exception, fallback: String) {
        val report = VaultFailure.describe(error,fallback)
        call.reject(if(fallback == "vault_read") "Secure storage could not be opened" else "Changes were not saved securely", report.getString("code"), report)
    }
    @Synchronized @PluginMethod
    fun get(call: PluginCall) {
        try {
            val name = call.getString("key") ?: throw IllegalArgumentException()
            val value = at("prefs_read") { preferences().getString(name, null) }
            if (value == null) { call.resolve(JSObject().put("value", org.json.JSONObject.NULL)); return }
            if (value.startsWith("v2:")) { call.resolve(JSObject().put("value", decodeV2(value))); return }
            val parts = value.split(":")
            if (parts.size != 2) throw VaultInvalidFormat()
            val iv: ByteArray; val ciphertext: ByteArray
            try { iv = Base64.decode(parts[0], Base64.NO_WRAP); ciphertext = Base64.decode(parts[1], Base64.NO_WRAP) }
            catch(e: IllegalArgumentException) { throw VaultInvalidFormat() }
            if (iv.size != 12 || ciphertext.size < 16) throw VaultInvalidFormat()
            val secret = key(false)
            val cipher = at("cipher_create") { Cipher.getInstance("AES/GCM/NoPadding") }
            at("cipher_init") { cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv)) }
            val plaintext = at("cipher_finish") { cipher.doFinal(ciphertext) }
            call.resolve(JSObject().put("value", String(plaintext, Charsets.UTF_8)))
        } catch (e: Exception) { reject(call,e,"vault_read") }
    }
    @Synchronized @PluginMethod
    fun set(call: PluginCall) {
        try {
            val name = call.getString("key") ?: throw IllegalArgumentException()
            val value = call.getString("value") ?: throw IllegalArgumentException()
            // Data encryption uses a fresh software AES key; only its RSA-wrapped form is stored.
            // This avoids broken AES Keystore decrypt operations on some API 28 emulators.
            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val secret = SecretKeySpec(raw, "AES")
            val cipher = at("cipher_create") { Cipher.getInstance("AES/GCM/NoPadding") }
            at("cipher_init") { cipher.init(Cipher.ENCRYPT_MODE, secret) }
            val plaintext = value.toByteArray(Charsets.UTF_8)
            val ciphertext = at("cipher_finish") { cipher.doFinal(plaintext) }
            // Obtain the final nonce after encryption and verify that the same key can read it.
            val iv = cipher.iv
            if (iv == null || iv.size != 12) throw VaultVerificationFailed()
            val wrap = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            at("wrap_init") { wrap.init(Cipher.ENCRYPT_MODE, wrappingStore(true).getCertificate(wrappingAlias).publicKey, oaep) }
            val wrapped = try { at("wrap_finish") { wrap.doFinal(raw) } } finally { raw.fill(0) }
            val encrypted = "v2:" + Base64.encodeToString(wrapped, Base64.NO_WRAP) + ":" + Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            at("write_verify") { if (decodeV2(encrypted) != value) throw VaultVerificationFailed() }
            at("prefs_write") { if (!preferences().edit().putString(name, encrypted).commit()) throw IllegalStateException() }
            call.resolve()
        } catch (e: Exception) { reject(call,e,"vault_write") }
    }
    @PluginMethod
    fun remove(call: PluginCall) {
        val name = call.getString("key") ?: return call.reject("Missing key")
        if (context.getSharedPreferences("sleepthrough-encrypted", 0).edit().remove(name).commit()) call.resolve()
        else call.reject("Could not remove local data", "storage_error")
    }
    @PluginMethod
    fun exportFile(call: PluginCall) {
        try {
            val text = call.getString("content") ?: throw IllegalArgumentException()
            val directory = java.io.File(context.cacheDir, "exports").apply { mkdirs() }; val csv = call.getString("format") == "csv"; val file = java.io.File(directory, if(csv) "sleepthrough-nights.csv" else "sleepthrough-export.json")
            file.writeText(text)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = if(csv) "text/csv" else "application/json"; putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(android.content.Intent.createChooser(intent, "Export your SleepThrough data"))
            call.resolve()
        } catch (e: Exception) { call.reject("Could not export your data", "export_error") }
    }
}
