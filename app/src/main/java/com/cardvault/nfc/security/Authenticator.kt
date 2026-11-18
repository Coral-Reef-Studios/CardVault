package com.cardvault.nfc.security

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed interface AuthResult {
    data object Success : AuthResult
    /** Device has no screen lock / biometrics: let the user in but warn them. */
    data object Unprotected : AuthResult
    data class Failed(val message: String) : AuthResult
}

object Authenticator {

    private val allowed: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            // API 29 does not support BIOMETRIC_STRONG | DEVICE_CREDENTIAL together.
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        onResult: (AuthResult) -> Unit,
    ) {
        if (!isAvailable(activity)) {
            onResult(AuthResult.Unprotected)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(AuthResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(AuthResult.Failed(errString.toString()))
                }
                // onAuthenticationFailed = single bad attempt; the prompt stays open, nothing to do.
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let { setSubtitle(it) } }
            .setAllowedAuthenticators(allowed)
            .build()
        prompt.authenticate(info)
    }
}

fun Context.findFragmentActivity(): FragmentActivity {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    error("No FragmentActivity in context chain")
}
