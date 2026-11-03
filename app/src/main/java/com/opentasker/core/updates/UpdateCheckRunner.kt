package com.opentasker.core.updates

import android.content.Context
import com.opentasker.core.actions.HttpRequestAction
import com.opentasker.core.engine.ActionContext
import com.opentasker.core.engine.ActionResult
import com.opentasker.core.engine.VariableStore
import com.opentasker.core.logging.AppLogger

sealed interface UpdateCheckRunResult {
    data class Completed(val release: UpdateCheckResult) : UpdateCheckRunResult
    data object Failed : UpdateCheckRunResult
}

/**
 * Shared implementation for the scheduled and user-requested release checks.
 *
 * This only checks release metadata and records the result. It does not download or install an APK.
 */
object UpdateCheckRunner {
    private const val TAG = "UpdateCheckRunner"

    suspend fun checkAndRecord(context: Context): UpdateCheckRunResult {
        val appContext = context.applicationContext
        return try {
            val variables = VariableStore()
            val requestResult = HttpRequestAction().run(
                ActionContext(appContext, variables),
                UpdateCheckProtocol.requestArguments(),
            )
            if (requestResult !is ActionResult.Success) {
                AppLogger.info(TAG, "Release check request did not complete")
                return UpdateCheckRunResult.Failed
            }

            val payload = variables.get(UpdateCheckProtocol.RESPONSE_VARIABLE)
                ?: return UpdateCheckRunResult.Failed
            when (val parsed = UpdateCheckProtocol.parseLatestRelease(payload, com.opentasker.app.BuildConfig.VERSION_NAME)) {
                UpdateCheckResult.NoUpdate,
                is UpdateCheckResult.Available,
                -> {
                    UpdateCheckSettings(appContext).record(parsed, System.currentTimeMillis())
                    UpdateCheckRunResult.Completed(parsed)
                }
                is UpdateCheckResult.Invalid -> {
                    AppLogger.warn(TAG, "Release check returned an invalid response")
                    UpdateCheckRunResult.Failed
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLogger.warn(TAG, "Release check failed", error)
            UpdateCheckRunResult.Failed
        }
    }
}
