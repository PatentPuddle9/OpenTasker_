package com.opentasker.core.actions

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
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

/**
 * Bridges a foreground system credential Activity back to the suspended automation action.
 * The system receives and verifies the credential; OpenTasker only receives the final outcome.
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
                ?: DeviceCredentialPromptResult.Rejected("The device credential prompt timed out.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DeviceCredentialPromptResult.Unavailable(
                "Android could not open the device credential prompt: ${error.message ?: error.javaClass.simpleName}",
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
 * A tiny, private Activity is required because Android's credential challenge is an Activity/
 * system prompt, not an API that a background Service can show or inspect directly.
 */
@Suppress("DEPRECATION")
class DeviceCredentialPromptActivity : Activity() {
    private var requestId: String? = null
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        if (requestId.isNullOrBlank()) {
            finish()
            return
        }
        // The credential screen is already owned by the system if this Activity is recreated.
        if (savedInstanceState != null) return

        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isDeviceSecure != true) {
            complete(DeviceCredentialPromptResult.Authenticated)
            return
        }

        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf(String::isNotBlank)
            ?: DEFAULT_TITLE
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showDeviceCredentialPrompt(title, description)
        } else {
            showLegacyDeviceCredentialPrompt(keyguard, title, description)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun showDeviceCredentialPrompt(title: String, description: String) {
        try {
            val builder = BiometricPrompt.Builder(this)
                .setTitle(title)
                .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            if (description.isNotBlank()) builder.setDescription(description)

            builder.build().authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        complete(DeviceCredentialPromptResult.Authenticated)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL) {
                            // The user may have removed their lock credential after the action's
                            // initial isDeviceSecure check. Treat that exactly like no password.
                            complete(DeviceCredentialPromptResult.Authenticated)
                        } else {
                            complete(
                                DeviceCredentialPromptResult.Rejected(
                                    errString.toString().ifBlank { "Device credential was not confirmed." },
                                ),
                            )
                        }
                    }
                },
            )
        } catch (error: Exception) {
            complete(
                DeviceCredentialPromptResult.Unavailable(
                    "Android could not show the device credential prompt: ${error.message ?: error.javaClass.simpleName}",
                ),
            )
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
            complete(DeviceCredentialPromptResult.Rejected("Device credential was not confirmed."))
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
        private const val DEFAULT_TITLE = "Confirm your identity"
    }
}
