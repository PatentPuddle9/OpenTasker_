package com.opentasker.core.actions

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal sealed interface DeviceCredentialPromptResult {
    data object Authenticated : DeviceCredentialPromptResult
    data class Rejected(val reason: String) : DeviceCredentialPromptResult
    data class Unavailable(val reason: String) : DeviceCredentialPromptResult
}

internal enum class DeviceSignInPromptMode {
    DEVICE_CREDENTIAL_ONLY,
    BIOMETRIC_WITH_CREDENTIAL_FALLBACK,
    BIOMETRIC_OR_DEVICE_CREDENTIAL,
}

internal fun deviceSignInPromptModeForApi(apiLevel: Int): DeviceSignInPromptMode = when {
    apiLevel >= Build.VERSION_CODES.R -> DeviceSignInPromptMode.BIOMETRIC_OR_DEVICE_CREDENTIAL
    apiLevel >= Build.VERSION_CODES.Q -> DeviceSignInPromptMode.BIOMETRIC_OR_DEVICE_CREDENTIAL
    apiLevel >= Build.VERSION_CODES.P -> DeviceSignInPromptMode.BIOMETRIC_WITH_CREDENTIAL_FALLBACK
    else -> DeviceSignInPromptMode.DEVICE_CREDENTIAL_ONLY
}

/**
 * Bridges a foreground system sign-in Activity back to the suspended automation action.
 * Android verifies the fingerprint or screen-lock credential; OpenTasker only receives the
 * authentication result and never receives biometric data.
 */
internal object DeviceCredentialPromptRequests {
    private const val PROMPT_TIMEOUT_MS = 9 * 60 * 1000L

    private val pending = ConcurrentHashMap<String, CompletableDeferred<DeviceCredentialPromptResult>>()

    suspend fun request(
        context: Context,
        title: String,
        description: String,
    ): DeviceCredentialPromptResult {
        val requestId = UUID.randomUUID().toString()
        val result = CompletableDeferred<DeviceCredentialPromptResult>()
        pending[requestId] = result

        return try {
            context.startActivity(
                Intent(context, DeviceCredentialPromptActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(DeviceCredentialPromptActivity.EXTRA_REQUEST_ID, requestId)
                    putExtra(DeviceCredentialPromptActivity.EXTRA_TITLE, title)
                    putExtra(DeviceCredentialPromptActivity.EXTRA_DESCRIPTION, description)
                },
            )
            withTimeoutOrNull(PROMPT_TIMEOUT_MS) { result.await() }
                ?: DeviceCredentialPromptResult.Rejected("The sign-in prompt timed out.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DeviceCredentialPromptResult.Unavailable(
                "Android could not open the sign-in prompt: ${error.message ?: error.javaClass.simpleName}",
            )
        } finally {
            pending.remove(requestId, result)
        }
    }

    internal fun complete(requestId: String, result: DeviceCredentialPromptResult) {
        pending[requestId]?.complete(result)
    }
}

/**
 * A private Activity is required because Android's biometric and device-credential prompts are
 * Activity/system UI, not APIs that a background Service can display or inspect directly.
 */
@Suppress("DEPRECATION")
class DeviceCredentialPromptActivity : Activity() {
    private var requestId: String? = null
    private var completed = false
    private var keyguard: KeyguardManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        if (requestId.isNullOrBlank()) {
            finish()
            return
        }
        // The system owns the authentication UI if this Activity is recreated.
        if (savedInstanceState != null) return

        keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isDeviceSecure != true) {
            complete(DeviceCredentialPromptResult.Authenticated)
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf(String::isNotBlank)
            ?: DEFAULT_TITLE
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()

        when (deviceSignInPromptModeForApi(Build.VERSION.SDK_INT)) {
            DeviceSignInPromptMode.DEVICE_CREDENTIAL_ONLY -> {
                showLegacyDeviceCredentialPrompt(requireNotNull(keyguard), title, description)
            }
            DeviceSignInPromptMode.BIOMETRIC_WITH_CREDENTIAL_FALLBACK -> {
                showFingerprintWithCredentialFallback(title, description)
            }
            DeviceSignInPromptMode.BIOMETRIC_OR_DEVICE_CREDENTIAL -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    showBiometricOrDeviceCredentialPrompt(title, description)
                } else {
                    showBiometricWithDeviceCredentialOption(title, description)
                }
            }
        }
    }

    /**
     * Android 11+ presents fingerprint and device credential in one system prompt. The system
     * automatically offers the PIN/pattern/password fallback when the user needs it.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun showBiometricOrDeviceCredentialPrompt(title: String, description: String) {
        try {
            val builder = BiometricPrompt.Builder(this)
                .setTitle(title)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            if (description.isNotBlank()) builder.setDescription(description)

            builder.build().authenticate(
                CancellationSignal(),
                mainExecutor,
                biometricCallback(title, description, fallbackToDeviceCredentialOnBiometricError = true),
            )
        } catch (error: Exception) {
            complete(
                DeviceCredentialPromptResult.Unavailable(
                    "Android could not show the fingerprint or screen-lock prompt: ${error.message ?: error.javaClass.simpleName}",
                ),
            )
        }
    }

    /**
     * Android 10 has the biometric prompt plus the built-in device-credential fallback switch.
     * This API was replaced by setAllowedAuthenticators on Android 11.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun showBiometricWithDeviceCredentialOption(title: String, description: String) {
        try {
            val builder = BiometricPrompt.Builder(this)
                .setTitle(title)
                .setDeviceCredentialAllowed(true)
            if (description.isNotBlank()) builder.setDescription(description)

            builder.build().authenticate(
                CancellationSignal(),
                mainExecutor,
                biometricCallback(title, description, fallbackToDeviceCredentialOnBiometricError = true),
            )
        } catch (error: Exception) {
            complete(
                DeviceCredentialPromptResult.Unavailable(
                    "Android could not show the fingerprint or screen-lock prompt: ${error.message ?: error.javaClass.simpleName}",
                ),
            )
        }
    }

    /**
     * Android 9 introduced BiometricPrompt but not its combined authenticator selector. Offer
     * fingerprint first and use the system's older screen-credential Activity as the explicit
     * fallback. If no fingerprint is enrolled, go straight to the screen credential.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun showFingerprintWithCredentialFallback(title: String, description: String) {
        val fingerprintManager = getSystemService(FingerprintManager::class.java)
        val fingerprintReady = runCatching {
            fingerprintManager?.let { manager ->
                manager.isHardwareDetected && manager.hasEnrolledFingerprints()
            } == true
        }.getOrDefault(false)
        if (!fingerprintReady) {
            showLegacyDeviceCredentialPrompt(requireNotNull(keyguard), title, description)
            return
        }

        try {
            val builder = BiometricPrompt.Builder(this)
                .setTitle(title)
                .setNegativeButton(
                    "Use device PIN, pattern, or password",
                    mainExecutor,
                    DialogInterface.OnClickListener { _, _ ->
                        showLegacyDeviceCredentialPrompt(requireNotNull(keyguard), title, description)
                    },
                )
            if (description.isNotBlank()) builder.setDescription(description)

            builder.build().authenticate(
                CancellationSignal(),
                mainExecutor,
                biometricCallback(title, description, fallbackToDeviceCredentialOnBiometricError = true),
            )
        } catch (error: Exception) {
            complete(
                DeviceCredentialPromptResult.Unavailable(
                    "Android could not show the fingerprint prompt: ${error.message ?: error.javaClass.simpleName}",
                ),
            )
        }
    }

    private fun biometricCallback(
        title: String,
        description: String,
        fallbackToDeviceCredentialOnBiometricError: Boolean,
    ) = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            complete(DeviceCredentialPromptResult.Authenticated)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            when {
                errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL -> {
                    // The user may have removed their screen lock after the action's initial check.
                    // With no device credential remaining, preserve the action's no-lock behavior.
                    complete(DeviceCredentialPromptResult.Authenticated)
                }
                fallbackToDeviceCredentialOnBiometricError && errorCode in DEVICE_CREDENTIAL_FALLBACK_ERRORS -> {
                    showLegacyDeviceCredentialPrompt(requireNotNull(keyguard), title, description)
                }
                else -> complete(
                    DeviceCredentialPromptResult.Rejected(
                        errString.toString().ifBlank { "Sign-in was not completed." },
                    ),
                )
            }
        }

        override fun onAuthenticationFailed() {
            // An unrecognized fingerprint is not a completed cancellation. Android keeps the
            // system prompt open so the user can try again or choose the credential fallback.
        }
    }

    private fun showLegacyDeviceCredentialPrompt(
        keyguard: KeyguardManager,
        title: String,
        description: String,
    ) {
        try {
            val challenge = keyguard.createConfirmDeviceCredentialIntent(title, description)
            if (challenge == null) {
                if (keyguard.isDeviceSecure) {
                    complete(DeviceCredentialPromptResult.Unavailable("Android did not provide a device credential challenge."))
                } else {
                    complete(DeviceCredentialPromptResult.Authenticated)
                }
            } else {
                startActivityForResult(challenge, REQUEST_CONFIRM_DEVICE_CREDENTIAL)
            }
        } catch (error: Exception) {
            complete(
                DeviceCredentialPromptResult.Unavailable(
                    "Android could not show the device credential prompt: ${error.message ?: error.javaClass.simpleName}",
                ),
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIRM_DEVICE_CREDENTIAL) return
        if (resultCode == RESULT_OK) {
            complete(DeviceCredentialPromptResult.Authenticated)
        } else {
            complete(DeviceCredentialPromptResult.Rejected("Device sign-in was not completed."))
        }
    }

    private fun complete(result: DeviceCredentialPromptResult) {
        if (completed) return
        completed = true
        requestId?.let { DeviceCredentialPromptRequests.complete(it, result) }
        finish()
    }

    companion object {
        const val EXTRA_REQUEST_ID = "com.opentasker.extra.DEVICE_CREDENTIAL_REQUEST_ID"
        const val EXTRA_TITLE = "com.opentasker.extra.DEVICE_CREDENTIAL_TITLE"
        const val EXTRA_DESCRIPTION = "com.opentasker.extra.DEVICE_CREDENTIAL_DESCRIPTION"

        private const val REQUEST_CONFIRM_DEVICE_CREDENTIAL = 7204
        private const val DEFAULT_TITLE = "Sign in to continue"

        private val DEVICE_CREDENTIAL_FALLBACK_ERRORS = setOf(
            BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
            BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT,
            BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS,
        )
    }
}
