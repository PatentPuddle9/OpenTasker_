package com.opentasker.core.actions

data class NotificationTaskCandidate(
    val id: Long,
    val name: String,
)

sealed interface NotificationTaskReference {
    data class Id(val taskId: Long) : NotificationTaskReference
    data class LegacyName(val taskName: String) : NotificationTaskReference
    data class Invalid(val rawValue: String) : NotificationTaskReference
}

sealed interface NotificationTaskResolution {
    data class Bound(
        val task: NotificationTaskCandidate,
        val migratedFromLegacyName: Boolean,
    ) : NotificationTaskResolution

    data class Missing(val reference: NotificationTaskReference) : NotificationTaskResolution
    data class Ambiguous(val taskName: String, val matchCount: Int) : NotificationTaskResolution
    data class Invalid(val rawValue: String) : NotificationTaskResolution
}

sealed interface NotificationButtonPlan {
    val index: Int
    val label: String

    data class RunTask(
        override val index: Int,
        val task: NotificationTaskCandidate,
        override val label: String,
    ) : NotificationButtonPlan

    data class Dismiss(
        override val index: Int,
        override val label: String,
    ) : NotificationButtonPlan

    data class Invalid(
        override val index: Int,
        override val label: String,
        val reason: String,
    ) : NotificationButtonPlan
}

/** Stable notification-button bindings with a fail-closed compatibility path for legacy names. */
object NotificationTaskBindings {
    const val BUTTON_COUNT = 3

    fun taskIdKey(buttonIndex: Int): String = "button${buttonIndex}_task_id"

    fun legacyTaskNameKey(buttonIndex: Int): String = "button${buttonIndex}_task"

    fun parse(args: Map<String, String>, buttonIndex: Int): NotificationTaskReference? {
        if (buttonIndex !in 1..BUTTON_COUNT) return NotificationTaskReference.Invalid(buttonIndex.toString())

        val idKey = taskIdKey(buttonIndex)
        if (idKey in args) {
            val rawId = args[idKey].orEmpty().trim()
            if (rawId.isEmpty()) return null
            val taskId = rawId.toLongOrNull()
            return if (taskId != null && taskId > 0) {
                NotificationTaskReference.Id(taskId)
            } else {
                NotificationTaskReference.Invalid(rawId)
            }
        }

        val legacyKey = legacyTaskNameKey(buttonIndex)
        if (legacyKey !in args) return null
        val taskName = args[legacyKey].orEmpty().trim()
        return if (taskName.isNotEmpty()) {
            NotificationTaskReference.LegacyName(taskName)
        } else {
            null
        }
    }

    fun resolve(
        reference: NotificationTaskReference,
        candidates: List<NotificationTaskCandidate>,
    ): NotificationTaskResolution = when (reference) {
        is NotificationTaskReference.Id -> {
            val task = candidates.firstOrNull { it.id == reference.taskId }
            if (task == null) {
                NotificationTaskResolution.Missing(reference)
            } else {
                NotificationTaskResolution.Bound(task, migratedFromLegacyName = false)
            }
        }

        is NotificationTaskReference.LegacyName -> {
            val matches = candidates.filter { it.name == reference.taskName }
            when (matches.size) {
                0 -> NotificationTaskResolution.Missing(reference)
                1 -> NotificationTaskResolution.Bound(matches.single(), migratedFromLegacyName = true)
                else -> NotificationTaskResolution.Ambiguous(reference.taskName, matches.size)
            }
        }

        is NotificationTaskReference.Invalid -> NotificationTaskResolution.Invalid(reference.rawValue)
    }

    /**
     * A labeled button with no task binding is an intentional dismiss-only button.
     * An empty label and empty task binding means that button slot is unused.
     */
    fun planButton(
        args: Map<String, String>,
        buttonIndex: Int,
        candidates: List<NotificationTaskCandidate>,
    ): NotificationButtonPlan? {
        val label = args["button${buttonIndex}_label"].orEmpty().trim()
        val reference = parse(args, buttonIndex)
            ?: return if (buttonIndex in 1..BUTTON_COUNT && label.isNotEmpty()) {
                NotificationButtonPlan.Dismiss(buttonIndex, label)
            } else {
                null
            }

        return when (val resolution = resolve(reference, candidates)) {
            is NotificationTaskResolution.Bound -> NotificationButtonPlan.RunTask(
                index = buttonIndex,
                task = resolution.task,
                label = label.ifBlank { resolution.task.name },
            )
            else -> NotificationButtonPlan.Invalid(
                index = buttonIndex,
                label = label,
                reason = failureMessage(resolution),
            )
        }
    }

    fun failureMessage(resolution: NotificationTaskResolution): String = when (resolution) {
        is NotificationTaskResolution.Bound -> error("Bound task references do not have a failure message")
        is NotificationTaskResolution.Missing -> when (val reference = resolution.reference) {
            is NotificationTaskReference.Id -> "Task ID ${reference.taskId} no longer exists"
            is NotificationTaskReference.LegacyName -> "Task '${reference.taskName}' was not found"
            is NotificationTaskReference.Invalid -> "Task binding '${reference.rawValue}' is invalid"
        }
        is NotificationTaskResolution.Ambiguous ->
            "Task name '${resolution.taskName}' matches ${resolution.matchCount} tasks; reselect by ID"
        is NotificationTaskResolution.Invalid -> "Task binding '${resolution.rawValue}' is invalid"
    }
}
