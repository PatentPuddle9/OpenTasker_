package com.opentasker.core.actions

import android.content.Intent
import android.provider.Settings
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.core.engine.ActionContext
import com.opentasker.core.engine.ActionResult

/**
 * Displays a real, dismissible Android dialog above other apps.
 *
 * A labeled button without a task is dismiss-only. A button with a selected task runs it after
 * dismissing the dialog.
 */
class ShowPopupAction : DeclaredAction(ActionCatalog.require("notify.show")) {

    override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
        val canDrawOverlays = try {
            Settings.canDrawOverlays(ctx.app)
        } catch (error: Exception) {
            return ActionResult.Failure("Could not check Display over other apps access: " + error.message, error)
        }
        if (!canDrawOverlays) {
            return ActionResult.Failure(
                "Display over other apps access is required to show popup dialogs. Enable it in Setup.",
            )
        }

        val hasTaskBindings = (1..NotificationTaskBindings.BUTTON_COUNT).any { index ->
            NotificationTaskBindings.parse(args, index) != null
        }
        val candidates = if (hasTaskBindings) {
            try {
                OpenTaskerApp_NoHilt.db.taskDao().getAll()
                    .map { NotificationTaskCandidate(it.id, it.name) }
            } catch (error: Exception) {
                return ActionResult.Failure("Could not load popup button tasks: " + error.message, error)
            }
        } else {
            emptyList()
        }

        val buttons = mutableListOf<NotificationButtonPlan>()
        for (index in 1..NotificationTaskBindings.BUTTON_COUNT) {
            when (val plan = NotificationTaskBindings.planButton(args, index, candidates)) {
                null -> Unit
                is NotificationButtonPlan.Invalid -> return ActionResult.Failure(
                    "Popup button " + plan.index + " is not runnable: " + plan.reason,
                )
                is NotificationButtonPlan.RunTask,
                is NotificationButtonPlan.Dismiss -> buttons += plan
            }
        }

        val intent = Intent(ctx.app, PopupDialogService::class.java).apply {
            putExtra(PopupDialogService.EXTRA_TITLE, args["title"]?.trim().orEmpty().ifBlank { "OpenTasker" })
            putExtra(PopupDialogService.EXTRA_MESSAGE, args["text"].orEmpty())
            putExtra(PopupDialogService.EXTRA_BUTTON_COUNT, buttons.size)
            buttons.forEachIndexed { offset, plan ->
                val buttonIndex = offset + 1
                putExtra(PopupDialogService.buttonLabelExtra(buttonIndex), plan.label)
                if (plan is NotificationButtonPlan.RunTask) {
                    putExtra(PopupDialogService.buttonTaskIdExtra(buttonIndex), plan.task.id)
                }
            }
        }

        return try {
            val started = ctx.app.startService(intent)
                ?: return ActionResult.Failure("Android did not start the popup dialog service")
            ctx.logger("Popup dialog shown (" + buttons.size + " button(s))")
            ActionResult.Success
        } catch (error: Exception) {
            ActionResult.Failure("Could not show popup dialog: " + error.message, error)
        }
    }
}
