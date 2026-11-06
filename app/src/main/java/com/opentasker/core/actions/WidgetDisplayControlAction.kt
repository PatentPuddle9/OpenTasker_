package com.opentasker.core.actions

import com.opentasker.core.engine.ActionContext
import com.opentasker.core.engine.ActionResult
import com.opentasker.widget.WidgetDisplayStateController

/** Changes the currently visible page of a selected Widget Display. */
class WidgetDisplayControlAction : DeclaredAction(ActionCatalog.require(ACTION_ID)) {
    override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
        val widgetId = args["widget_id"]?.trim()?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return ActionResult.Failure("Enter a valid Widget Display ID")
        val operation = args["operation"]?.trim()?.lowercase()
            ?.takeIf { it in setOf("next", "previous", "show") }
            ?: return ActionResult.Failure("Choose next, previous, or show a state")
        val stateIndex = args["state_index"]?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (operation == "show" && (stateIndex == null || stateIndex < 1)) {
            return ActionResult.Failure("Enter the state number to show")
        }

        val changed = WidgetDisplayStateController.control(
            context = ctx.app,
            widgetId = widgetId,
            operation = operation,
            stateIndex = stateIndex,
        )
        if (!changed) {
            return ActionResult.Failure(
                "Widget Display $widgetId could not change state. Check that the widget exists and the state number is valid.",
            )
        }
        ctx.logger("Widget Display $widgetId: $operation${stateIndex?.let { " state $it" } ?: ""}")
        return ActionResult.Success
    }

    companion object {
        const val ACTION_ID = "widget.display.control"
    }
}
