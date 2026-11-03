package com.opentasker.core.updates

import android.content.Context
import com.opentasker.app.BuildConfig
import com.opentasker.core.actions.HttpRequestAction
import com.opentasker.core.engine.ActionContext
import com.opentasker.core.engine.ActionResult
import com.opentasker.core.engine.VariableStore
import com.opentasker.core.logging.AppLogger

sealed interface UpdateCheckRunResult {
    data class Completed(val release: UpdateCheckResult) : UpdateCheckRunResult
    data object Failed : UpdateCheckRunResult
}

/** Shared implementation for scheduled/manual branch checks and branch discovery. */
object UpdateCheckRunner {
    private const val TAG = "UpdateCheckRunner"

    suspend fun fetchAvailableBranches(context: Context): List<String>? {
        val appContext = context.applicationContext
        val variables = VariableStore()
        val branches = linkedSetOf<String>()
        return try {
            for (page in 1..UpdateCheckProtocol.MAX_BRANCH_PAGES) {
                val requestResult = HttpRequestAction().run(
                    ActionContext(appContext, variables),
                    UpdateCheckProtocol.branchesRequestArguments(page),
                )
                if (requestResult !is ActionResult.Success) {
                    AppLogger.info(TAG, "GitHub branch list request did not complete")
                    return null
                }
                val payload = variables.get(UpdateCheckProtocol.BRANCHES_RESPONSE_VARIABLE) ?: return null
                val page = UpdateCheckProtocol.parseBranchPage(payload) ?: return null
                branches.addAll(page.branches)
                if (!page.hasMore) {
                    return UpdateCheckProtocol.sortBranches(branches)
                }
            }
            UpdateCheckProtocol.sortBranches(branches)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLogger.warn(TAG, "Could not load GitHub branches", error)
            null
        }
    }

    suspend fun checkAndRecord(
        context: Context,
        branch: String = BuildConfig.UPDATE_SOURCE_BRANCH,
    ): UpdateCheckRunResult {
        if (!UpdateCheckProtocol.isSelectableBranch(branch)) return UpdateCheckRunResult.Failed
        val appContext = context.applicationContext
        return try {
            val variables = VariableStore()
            val requestResult = HttpRequestAction().run(
                ActionContext(appContext, variables),
                UpdateCheckProtocol.requestArguments(branch),
            )
            if (requestResult !is ActionResult.Success) {
                // A missing branch channel simply means that branch has not published a build yet.
                if (variables.get(UpdateCheckProtocol.STATUS_VARIABLE) == "404") {
                    UpdateCheckSettings(appContext).record(UpdateCheckResult.NoUpdate, System.currentTimeMillis(), branch)
                    return UpdateCheckRunResult.Completed(UpdateCheckResult.NoUpdate)
                }
                AppLogger.info(TAG, "Branch release check request did not complete")
                return UpdateCheckRunResult.Failed
            }

            val payload = variables.get(UpdateCheckProtocol.RESPONSE_VARIABLE)
                ?: return UpdateCheckRunResult.Failed
            when (val parsed = UpdateCheckProtocol.parseBranchRelease(
                payload,
                com.opentasker.app.BuildConfig.VERSION_NAME,
                branch,
            )) {
                UpdateCheckResult.NoUpdate,
                is UpdateCheckResult.Available,
                -> {
                    UpdateCheckSettings(appContext).record(parsed, System.currentTimeMillis(), branch)
                    UpdateCheckRunResult.Completed(parsed)
                }
                is UpdateCheckResult.Invalid -> {
                    AppLogger.warn(TAG, "Branch release check returned an invalid response")
                    UpdateCheckRunResult.Failed
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLogger.warn(TAG, "Branch release check failed", error)
            UpdateCheckRunResult.Failed
        }
    }
}
