package com.opentasker.core.actions

import android.app.AlertDialog
import android.app.Service
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.os.IBinder
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import com.opentasker.core.external.AutomationTargetContract
import com.opentasker.core.external.InternalTaskRunSource
import com.opentasker.core.logging.AppLogger

/**
 * Hosts a modal dialog as an application-overlay window.
 *
 * This is intentionally a regular (non-foreground) service: the automation engine already owns
 * foreground execution, and showing a popup must not create a second notification.
 */
class PopupDialogService : Service() {

    private var activeDialog: AlertDialog? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || !Settings.canDrawOverlays(this)) {
            AppLogger.warn(TAG, "Popup dialog was not shown because overlay access is unavailable")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val buttonCount = intent.getIntExtra(EXTRA_BUTTON_COUNT, 0).coerceIn(0, MAX_BUTTONS)
        val buttons = (1..buttonCount).mapNotNull { index ->
            val label = intent.getStringExtra(buttonLabelExtra(index))?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val taskId = intent.getLongExtra(buttonTaskIdExtra(index), INVALID_TASK_ID)
                .takeIf { it > 0L }
            PopupButton(label, taskId)
        }

        dismissActiveDialog()
        val deviceTheme = popupDialogThemeResource(resources.configuration.uiMode)
        val themedContext = ContextThemeWrapper(this, deviceTheme)
        val builder = AlertDialog.Builder(themedContext)
        if (title.isNotBlank()) builder.setTitle(title)
        if (message.isNotBlank()) builder.setMessage(message)

        if (buttons.isEmpty()) {
            builder.setPositiveButton(android.R.string.ok, null)
        } else {
            buttons.forEachIndexed { index, button ->
                val listener = DialogInterface.OnClickListener { _, _ ->
                    button.taskId?.let { runTask(it, button.label) }
                }
                when (index) {
                    0 -> builder.setPositiveButton(button.label, listener)
                    1 -> builder.setNegativeButton(button.label, listener)
                    2 -> builder.setNeutralButton(button.label, listener)
                }
            }
        }

        val dialog = builder.create().apply {
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }
        dialog.setOnDismissListener {
            if (activeDialog === dialog) {
                activeDialog = null
                stopSelfResult(startId)
            }
        }
        dialog.window?.let { window ->
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply {
                dimAmount = DIM_AMOUNT
                gravity = Gravity.CENTER
            }
        }

        activeDialog = dialog
        return try {
            dialog.show()
            dialog.window?.let { window ->
                val width = (resources.displayMetrics.widthPixels * DIALOG_WIDTH_FRACTION)
                    .toInt()
                    .coerceAtLeast(1)
                window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
                window.decorView.filterTouchesWhenObscured = true
            }
            AppLogger.info(TAG, "Popup dialog displayed")
            START_NOT_STICKY
        } catch (error: Exception) {
            activeDialog = null
            AppLogger.error(TAG, "Could not display popup dialog", error)
            stopSelfResult(startId)
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        activeDialog?.setOnDismissListener(null)
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroy()
    }

    private fun dismissActiveDialog() {
        activeDialog?.setOnDismissListener(null)
        activeDialog?.dismiss()
        activeDialog = null
    }

    private fun runTask(taskId: Long, buttonLabel: String) {
        try {
            val runIntent = AutomationTargetContract.internalRunTaskIntent(
                context = this,
                taskId = taskId,
                source = InternalTaskRunSource.POPUP_DIALOG,
            )
            sendBroadcast(runIntent, AutomationTargetContract.PERMISSION)
            AppLogger.info(TAG, "Popup button '" + buttonLabel + "' submitted task " + taskId)
        } catch (error: Exception) {
            AppLogger.error(TAG, "Popup button '" + buttonLabel + "' could not submit task " + taskId, error)
        }
    }

    private data class PopupButton(
        val label: String,
        val taskId: Long?,
    )

    companion object {
        private const val TAG = "PopupDialogService"
        private const val MAX_BUTTONS = 3
        private const val INVALID_TASK_ID = -1L
        private const val DIM_AMOUNT = 0.55f
        private const val DIALOG_WIDTH_FRACTION = 0.9f

        const val EXTRA_TITLE = "com.opentasker.extra.POPUP_TITLE"
        const val EXTRA_MESSAGE = "com.opentasker.extra.POPUP_MESSAGE"
        const val EXTRA_BUTTON_COUNT = "com.opentasker.extra.POPUP_BUTTON_COUNT"

        fun buttonLabelExtra(index: Int): String {
            require(index in 1..MAX_BUTTONS)
            return "com.opentasker.extra.POPUP_BUTTON_" + index + "_LABEL"
        }

        fun buttonTaskIdExtra(index: Int): String {
            require(index in 1..MAX_BUTTONS)
            return "com.opentasker.extra.POPUP_BUTTON_" + index + "_TASK_ID"
        }
    }
}

/**
 * Uses the Android device's own dialog styling rather than OpenTasker's in-app palette.
 * The service is not an Activity, so the dialog theme must be selected explicitly.
 */
internal fun popupDialogThemeResource(uiMode: Int): Int =
    if ((uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
        android.R.style.Theme_DeviceDefault_Dialog_Alert
    } else {
        android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
    }
