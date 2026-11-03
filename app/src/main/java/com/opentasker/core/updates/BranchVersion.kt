package com.opentasker.core.updates

import com.opentasker.core.model.Profile
import com.opentasker.core.model.Task

/**
 * A published APK version for one update source. [versionCode] is nullable because the earliest
 * releases did not publish it in their notes; those entries can still be opened on GitHub.
 */
data class BranchVersion(
    val tag: String,
    val version: String,
    val versionCode: Long?,
    val releaseUrl: String,
    val apkUrl: String?,
    val apkSizeBytes: Long?,
    val apkSha256: String?,
    val sourceCommit: String?,
    val publishedAt: String?,
    val supportsInAppUpdates: Boolean,
    val supportedActionIds: Set<String>? = null,
    val missingActionIds: List<String> = emptyList(),
    val compatibilityKnown: Boolean = false,
    val isLatest: Boolean = false,
) {
    val canInstallInPlace: Boolean
        get() = apkUrl != null && apkSizeBytes != null && apkSha256 != null &&
            versionCode != null
}

/**
 * Finds actions used by enabled profiles, including their enter/exit/fallback tasks and nested
 * task.run calls. Disabled profiles and unreferenced draft tasks do not affect old-version warnings.
 */
fun collectEnabledProfileActionIds(
    profiles: List<Profile>,
    tasks: List<Task>,
    globalFallbackTaskId: Long? = null,
): Set<String> {
    val tasksById = tasks.associateBy(Task::id)
    val pending = ArrayDeque<Long>()
    profiles.asSequence()
        .filter(Profile::enabled)
        .forEach { profile ->
            pending += profile.enterTaskId
            profile.exitTaskId?.let(pending::addLast)
            profile.fallbackTaskId?.let(pending::addLast)
        }
    globalFallbackTaskId?.let(pending::addLast)

    val visitedTasks = mutableSetOf<Long>()
    val usedActions = linkedSetOf<String>()
    while (pending.isNotEmpty() && visitedTasks.size <= tasks.size) {
        val taskId = pending.removeFirst()
        if (!visitedTasks.add(taskId)) continue
        val task = tasksById[taskId] ?: continue
        task.actions.forEach { action ->
            usedActions += action.type
            if (action.type == "task.run") {
                val reference = listOf("task", "name", "id")
                    .firstNotNullOfOrNull { key -> action.args[key]?.trim()?.takeIf(String::isNotBlank) }
                    ?: return@forEach
                val child = reference.toLongOrNull()
                    ?.let(tasksById::get)
                    ?: tasks.firstOrNull { it.name.equals(reference, ignoreCase = true) }
                child?.let { pending.addLast(it.id) }
            }
        }
    }
    return usedActions
}
