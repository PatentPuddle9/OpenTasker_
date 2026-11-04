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

/**
 * Returns only user-facing top-level Actions. The individual ActionSpec entries inside their
 * execution records are editor implementation details and must never become picker items.
 */
fun selectableActionProfiles(profiles: List<Profile>): List<Profile> =
    profiles.asSequence()
        .filter { it.widgetId == null && it.enterTaskId > 0L }
        .sortedBy { it.name.lowercase() }
        .toList()

/** Resolves private execution records for fields that link to another top-level Action. */
fun resolveActionExecutionBindings(
    actions: List<Profile>,
    executionRecords: List<Task>,
): List<ActionExecutionBinding> {
    val recordsById = executionRecords.associateBy(Task::id)
    return selectableActionProfiles(actions).mapNotNull { action ->
        recordsById[action.enterTaskId]?.let { execution ->
            ActionExecutionBinding(action = action, execution = execution)
        }
    }
}
