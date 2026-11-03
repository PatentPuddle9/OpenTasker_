package com.opentasker.core.updates

import android.content.Context
import com.opentasker.app.BuildConfig
import com.opentasker.core.actions.HttpRequestAction
import com.opentasker.core.engine.ActionContext
import com.opentasker.core.engine.ActionResult
import com.opentasker.core.engine.VariableStore
import com.opentasker.core.logging.AppLogger
import java.util.concurrent.ConcurrentHashMap

sealed interface UpdateCheckRunResult {
    data class Completed(val release: UpdateCheckResult) : UpdateCheckRunResult
    data object Failed : UpdateCheckRunResult
}

/** Shared implementation for scheduled/manual branch checks and branch discovery. */
object UpdateCheckRunner {
    private const val TAG = "UpdateCheckRunner"
    private val actionCatalogCache = ConcurrentHashMap<String, Set<String>>()

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

    /**
     * Loads every published version for [branch] and compares its action catalogue with the actions
     * currently used by enabled profiles. Missing/unknown history is never presented as compatible.
     */
    suspend fun fetchAvailableVersions(
        context: Context,
        branch: String,
        usedActionIds: Set<String>,
    ): List<BranchVersion>? {
        if (!UpdateCheckProtocol.isSelectableBranch(branch)) return null
        val appContext = context.applicationContext
        val versions = mutableListOf<BranchVersion>()
        return try {
            for (pageNumber in 1..UpdateCheckProtocol.MAX_VERSION_PAGES) {
                val variables = VariableStore()
                val result = HttpRequestAction().run(
                    ActionContext(appContext, variables),
                    UpdateCheckProtocol.versionsRequestArguments(pageNumber, branch),
                )
                if (result !is ActionResult.Success) {
                    AppLogger.info(TAG, "GitHub version history request did not complete")
                    return null
                }
                val payload = variables.get(UpdateCheckProtocol.RESPONSE_VARIABLE) ?: return null
                val parsed = UpdateCheckProtocol.parseBranchVersions(payload, branch) ?: return null
                versions += parsed
                val hasMore = UpdateCheckProtocol.releasePageHasMore(payload) ?: return null
                if (!hasMore) break
            }

            val ordered = UpdateCheckProtocol.orderBranchVersions(versions, branch)
            ordered.map { version ->
                if (usedActionIds.isEmpty()) {
                    version.copy(compatibilityKnown = true)
                } else {
                    val commit = version.sourceCommit
                    // New releases carry a bounded action-catalogue snapshot in their notes.
                    // Older releases predate that marker, so fall back to the exact source commit.
                    val supported = version.supportedActionIds
                        ?: commit?.let { actionCatalogCache[it] ?: fetchActionCatalog(appContext, it) }
                    if (supported == null) {
                        version.copy(compatibilityKnown = false)
                    } else {
                        version.copy(
                            compatibilityKnown = true,
                            missingActionIds = (usedActionIds - supported).sorted(),
                        )
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppLogger.warn(TAG, "Could not load branch version history", error)
            null
        }
    }

    private suspend fun fetchActionCatalog(context: Context, commit: String): Set<String>? {
        actionCatalogCache[commit]?.let { return it }
        val variables = VariableStore()
        val result = HttpRequestAction().run(
            ActionContext(context, variables),
            mapOf(
                "method" to "GET",
                "url" to UpdateCheckProtocol.actionCatalogUrl(commit),
                "headers" to "Accept: text/plain",
                "response_var" to UpdateCheckProtocol.RESPONSE_VARIABLE,
                "max_response_bytes" to UpdateCheckProtocol.MAX_ACTION_CATALOG_BYTES.toString(),
                "timeout_sec" to UpdateCheckProtocol.REQUEST_TIMEOUT_SECONDS.toString(),
                "call_timeout_sec" to UpdateCheckProtocol.REQUEST_TIMEOUT_SECONDS.toString(),
                "connect_timeout_sec" to UpdateCheckProtocol.REQUEST_TIMEOUT_SECONDS.toString(),
                "read_timeout_sec" to UpdateCheckProtocol.REQUEST_TIMEOUT_SECONDS.toString(),
                "write_timeout_sec" to UpdateCheckProtocol.REQUEST_TIMEOUT_SECONDS.toString(),
                "redirects" to "none",
            ),
        )
        if (result !is ActionResult.Success) {
            AppLogger.info(TAG, "Could not read action catalogue for historical commit $commit")
            return null
        }
        val source = variables.get(UpdateCheckProtocol.RESPONSE_VARIABLE) ?: return null
        val parsed = UpdateCheckProtocol.parseActionCatalog(source) ?: return null
        actionCatalogCache[commit] = parsed
        return parsed
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
