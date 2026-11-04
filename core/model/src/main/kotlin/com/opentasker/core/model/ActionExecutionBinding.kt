package com.opentasker.core.model

/**
 * A user-facing Action and the private execution record that stores its ordered action steps.
 *
 * The execution record remains an implementation detail for compatibility with the automation
 * engine. UI pickers should display [action] and persist [execution]'s id only as a reference.
 */
data class ActionExecutionBinding(
    val action: Profile,
    val execution: Task,
)

/** Resolves the backing execution records for selectable, non-widget Actions. */
fun resolveActionExecutionBindings(
    actions: List<Profile>,
    executionRecords: List<Task>,
): List<ActionExecutionBinding> {
    val recordsById = executionRecords.associateBy(Task::id)
    return actions.asSequence()
        .filter { it.widgetId == null && it.enterTaskId > 0L }
        .mapNotNull { action ->
            recordsById[action.enterTaskId]?.let { execution ->
                ActionExecutionBinding(action = action, execution = execution)
            }
        }
        .sortedBy { it.action.name.lowercase() }
        .toList()
}
