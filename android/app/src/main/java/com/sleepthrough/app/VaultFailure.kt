package com.sleepthrough.app

import android.os.Build
import com.getcapacitor.JSObject

internal class VaultStep(val step: String, cause: Exception) : Exception(null, cause)
internal class VaultMissingKey : Exception()
internal class VaultInvalidFormat : Exception()
internal class VaultVerificationFailed : Exception()

/** Technical identifiers only: never include exception messages, stack traces or stored values. */
object VaultFailure {
    private val knownTypes = setOf(
        "MissingKey", "VaultMissingKey", "VaultInvalidFormat", "VaultVerificationFailed",
        "KeyPermanentlyInvalidatedException", "UserNotAuthenticatedException", "UnrecoverableKeyException",
        "KeyStoreException", "BackendBusyException", "ProviderException", "InvalidKeyException",
        "InvalidAlgorithmParameterException", "AEADBadTagException", "BadPaddingException",
        "IllegalBlockSizeException", "ClassCastException", "NullPointerException", "IOException",
        "IllegalStateException", "IllegalArgumentException", "NoSuchAlgorithmException", "NoSuchPaddingException"
    )
    fun describe(error: Exception, fallback: String): JSObject {
        val chain = mutableListOf<Throwable>()
        var current: Throwable? = error
        while(current != null && chain.size < 8 && chain.none { it === current }) {
            chain.add(current); current = current.cause
        }
        val keystore = chain.filterIsInstance<android.security.KeyStoreException>().firstOrNull()
        val modern = Build.VERSION.SDK_INT >= 33 && keystore != null
        val code = when {
            chain.any { it is VaultMissingKey } || (modern && keystore!!.numericErrorCode == android.security.KeyStoreException.ERROR_KEY_DOES_NOT_EXIST) -> "vault_key_missing"
            chain.any { it is android.security.keystore.KeyPermanentlyInvalidatedException } || (modern && keystore!!.numericErrorCode == android.security.KeyStoreException.ERROR_KEY_CORRUPTED) -> "vault_key_invalid"
            chain.any { it is android.security.keystore.UserNotAuthenticatedException } || (modern && keystore!!.requiresUserAuthentication()) -> "vault_locked"
            chain.any { it is VaultInvalidFormat || it is ClassCastException } -> "vault_format"
            chain.any { it is javax.crypto.BadPaddingException } -> "vault_decrypt"
            chain.any { it is VaultVerificationFailed } -> "vault_verify"
            else -> fallback
        }
        val retryable = code in setOf("vault_read", "vault_write") &&
            (if(modern) keystore!!.isTransientFailure else chain.any {
                it is java.security.ProviderException || it is java.security.UnrecoverableKeyException ||
                it is java.io.IOException || it is java.security.InvalidKeyException || it is android.security.KeyStoreException
            })
        val step = chain.filterIsInstance<VaultStep>().firstOrNull()?.step ?: "unknown"
        val types = chain.filterNot { it is VaultStep }.map { it.javaClass.simpleName.let { name -> if(name in knownTypes) name else "OtherException" } }.distinct().joinToString("/")
        val diagnostic = step + "/" + types + if(modern) "/KS" + keystore!!.numericErrorCode else ""
        val busy = if(Build.VERSION.SDK_INT >= 31) chain.filterIsInstance<android.security.keystore.BackendBusyException>().firstOrNull() else null
        val retryAfter = if(Build.VERSION.SDK_INT >= 31 && busy != null) busy.backOffHintMillis.coerceAtLeast(5000) else 5000L
        return JSObject().put("code", code).put("diagnostic", diagnostic).put("retryable", retryable)
            .put("retryAfterMs", retryAfter).put("device", Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT)
    }
}
