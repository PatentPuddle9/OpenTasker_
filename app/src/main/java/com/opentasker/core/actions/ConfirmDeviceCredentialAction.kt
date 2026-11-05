package com.opentasker.core.actions

import android.app.KeyguardManager
import android.provider.Settings
import com.opentasker.core.engine.ActionContext
import com.opentasker.core.engine.ActionResult

/** Requires the device owner to confirm with Android's system-managed screen credential. */
class ConfirmDeviceCredentialAction : DeclaredAction(ActionCatalog.require(ACTION_ID)) {

    override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
        val fallbackRaw = args["fallback_action_id"]?.trim()?.takeIf(String::isNotEmpty)
        val fallbackTaskId = fallbackRaw?.toLongOrNull()?.takeIf { it > 0L }
        if (fallbackRaw != null && fallbackTaskId == null) {
            return ActionResult.Failure("The selected fallback Action is no longer valid.")
        }

        val keyguard = try {
            ctx.app.getSystemService(KeyguardManager::class.java)
        } catch (error: Exception) {
            return ActionResult.Failure(
                "Could not check the device's security settings: ${error.message}",
            )
        } ?: return ActionResult.Failure(
            "Android's device security service is unavailable.",
        )

        val hasDeviceCredential = try {
            keyguard.isDeviceSecure
        } catch (error: Exception) {
            return ActionResult.Failure(
                "Could not check whether a device credential is configured: ${error.message}",
            )
        }

        if (!hasDeviceCredential) {
            ctx.logger("No device lock credential is configured; continuing.")
            return ActionResult.Success
        }

        // OpenTasker automations run from a foreground Service. Android restricts background
        // Activity launches; the user-granted overlay access is the supported exception that
        // lets this automated system prompt appear while OpenTasker is not visible.
        val canStartSystemPrompt = try {
            Settings.canDrawOverlays(ctx.app)
        } catch (error: Exception) {
            return ActionResult.Failure(
                "Could not check Display over other apps access: ${error.message}",
            )
        }
        if (!canStartSystemPrompt) {
            return ActionResult.Failure(
                "Display over other apps access is required to request device credential confirmation from an automation.",
            )
        }

        val title = args["title"]?.trim()?.takeIf(String::isNotEmpty) ?: DEFAULT_TITLE
        val description = args["description"]?.trim()?.takeIf(String::isNotEmpty) ?: DEFAULT_DESCRIPTION
        return when (val outcome = DeviceCredentialPromptRequests.request(ctx.app, title, description)) {
            DeviceCredentialPromptResult.Authenticated -> {
                ctx.logger("Device credential confirmed; continuing.")
                ActionResult.Success
            }
            is DeviceCredentialPromptResult.Rejected -> ActionResult.Failure(
                "Device credential was not confirmed: ${outcome.reason}",
                fallbackTaskId = fallbackTaskId,
            )
            is DeviceCredentialPromptResult.Unavailable -> ActionResult.Failure(
                "Android could not present the device credential prompt: ${outcome.reason}",
            )
        }
    }

    companion object {
        const val ACTION_ID = "auth.device_credential"
        private const val DEFAULT_TITLE = "Confirm your identity"
        private const val DEFAULT_DESCRIPTION = "Enter your device PIN, pattern, or password to continue."
    }
}
