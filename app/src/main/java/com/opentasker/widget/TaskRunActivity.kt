package com.opentasker.widget

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.core.engine.ExecutionAdmissionRegistry
import com.opentasker.core.engine.executeAndLogTask
import com.opentasker.core.engine.ExecutionEnvelope
import com.opentasker.core.engine.logSkippedRun
import com.opentasker.core.logging.AppLogger
import com.opentasker.core.storage.recoveryMessage
import com.opentasker.ui.utils.ActionResultNotificationPreference
import android.os.Handler
import android.os.Looper
import com.opentasker.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TaskRunActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        // Kept only for existing non-widget shortcuts created by older versions.
        val legacyTaskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: SOURCE_SHORTCUT
        if (profileId <= 0L && legacyTaskId < 0L) {
            finishWithMessage("Invalid Action")
            return
        }

        // The widget stores a Profile/Action ID. Only here do we resolve its private execution
        // record, so the editor's individual ActionSpec steps never become independently runnable.
        val appContext = applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        finish()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            var admittedTaskId: Long? = null
            var isActionResult = false
            val message = try {
                val db = OpenTaskerApp_NoHilt.awaitDb()
                val action = if (profileId > 0L) {
                    val entity = db.profileDao().getById(profileId)
                    val decoded = entity?.toDomainDecodeResult()
                    if (decoded == null) {
                        null
                    } else {
                        val issue = decoded.issue
                        if (issue != null) {
                            AppLogger.error(TAG, issue.recoveryMessage())
                            null
                        } else {
                            decoded.value.takeIf { it.enterTaskId > 0L }
                        }
                    }
                } else {
                    null
                }
                val taskId = action?.enterTaskId ?: legacyTaskId
                if (taskId < 0L) {
                    appContext.getString(R.string.widget_task_missing)
                } else if (!RunningWidgetTasks.begin(taskId)) {
                    appContext.getString(R.string.widget_task_already_running)
                } else {
                    admittedTaskId = taskId
                    try {
                        val entity = db.taskDao().getById(taskId)
                        if (entity == null) {
                            appContext.getString(R.string.widget_task_missing)
                        } else {
                            val decoded = entity.toDomainDecodeResult()
                            val issue = decoded.issue
                            if (issue != null) {
                                val reason = issue.recoveryMessage()
                                AppLogger.error(TAG, reason)
                                logSkippedRun(db, decoded.value, source, reason)
                                "${action?.name ?: decoded.value.name} is unavailable; restore a database backup"
                            } else {
                                val task = decoded.value
                                val result = executeAndLogTask(
                                    appContext = appContext,
                                    db = db,
                                    task = task,
                                    source = source,
                                    execution = ExecutionEnvelope.create(task, source),
                                    visibleActivity = true,
                                    // Use the same admission controller as the in-app Run action.
                                    admissionController = ExecutionAdmissionRegistry.current(appContext),
                                )
                                isActionResult = true
                                val status = when {
                                    result.held -> "held"
                                    result.skippedReason != null -> "skipped"
                                    result.report.success -> "succeeded"
                                    else -> "failed"
                                }
                                "${action?.name ?: task.name} $status (${result.report.durationMs}ms)"
                            }
                        }
                    } finally {
                        RunningWidgetTasks.finish(taskId)
                        admittedTaskId = null
                    }
                }
            } catch (e: Exception) {
                isActionResult = true
                AppLogger.error(TAG, "Action run failed", e)
                "Action run failed"
            } finally {
                admittedTaskId?.let(RunningWidgetTasks::finish)
            }
            mainHandler.post {
                if (!isActionResult || ActionResultNotificationPreference.isEnabled(appContext)) {
                    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Two quick taps on a widget used to start two concurrent runs with real side effects. */
    private object RunningWidgetTasks {
        private val running = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

        fun begin(taskId: Long): Boolean = running.add(taskId)

        fun finish(taskId: Long) {
            running.remove(taskId)
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "com.opentasker.widget.TASK_ID"
        const val EXTRA_PROFILE_ID = "com.opentasker.widget.PROFILE_ID"
        const val EXTRA_SOURCE = "com.opentasker.widget.SOURCE"
        const val SOURCE_WIDGET = "Widget"
        const val SOURCE_SHORTCUT = "Shortcut"
        private const val TAG = "TaskRunActivity"
    }
}
