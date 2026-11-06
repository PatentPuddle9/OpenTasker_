package com.opentasker.core.references

import com.opentasker.core.model.ContextExpressionNode
import com.opentasker.core.model.Profile
import com.opentasker.core.model.Scene
import com.opentasker.core.model.Task
import com.opentasker.core.validation.InputValidation
import java.util.Locale

/** Pure copy policy shared by the workspace duplicate actions and their tests. */
object AutomationDuplicator {
    fun copyName(
        original: String,
        existingNames: Collection<String>,
        strings: AutomationDuplicateStrings = AutomationDuplicateStrings.English,
    ): String {
        val cleanOriginal = original.trim().ifBlank { strings.untitled() }
        val occupied = existingNames.map { it.trim().lowercase(Locale.ROOT) }.toSet()
        var copyNumber = 1
        while (true) {
            val suffix = strings.copySuffix(copyNumber)
            val prefixLength = (InputValidation.MAX_NAME_LENGTH - suffix.length).coerceAtLeast(1)
            val candidate = cleanOriginal.take(prefixLength).trimEnd() + suffix
            if (candidate.lowercase(Locale.ROOT) !in occupied) return candidate
            copyNumber++
        }
    }

    fun taskPayload(source: Task, name: String): Task {
        var nextActionId = source.actions.maxOfOrNull { it.id.coerceAtLeast(0L) } ?: 0L
        val newIdsByOriginalId = linkedMapOf<Long, Long>()
        val assignedIds = source.actions.map { action ->
            nextActionId = nextFreshId(nextActionId)
            if (action.id > 0L) newIdsByOriginalId.putIfAbsent(action.id, nextActionId)
            nextActionId
        }
        val actions = source.actions.mapIndexed { index, action ->
            val args = action.args.toMutableMap()
            // A Sign In fallback references a sibling ActionSpec, not a top-level Action/task.
            // Rebind that reference to the matching sibling in the duplicate so it doesn't point
            // back into the original setup after the copied steps receive fresh IDs.
            args["fallback_action_spec_id"]?.toLongOrNull()?.let(newIdsByOriginalId::get)?.let { newId ->
                args["fallback_action_spec_id"] = newId.toString()
            }
            action.copy(id = assignedIds[index], args = args)
        }
        return source.copy(id = 0L, name = name, actions = actions)
    }

    fun profilePayload(source: Profile, name: String): Profile = source.copy(
        id = 0L,
        name = name,
        enabled = false,
        // A copy carries the original's powers, so it has to carry its review requirement too.
        // Clearing this let a user duplicate an imported profile that the app refuses to enable
        // and then enable the copy without ever seeing the risk disclosure.
        requiresRiskAcknowledgement = source.requiresRiskAcknowledgement,
        lifetimeConsumed = false,
        contexts = source.contexts.map { context -> context.copy(config = context.config.toMap()) },
        contextExpression = source.contextExpression?.deepCopy(),
    )

    fun scenePayload(source: Scene, name: String): Scene {
        var nextElementId = source.elements.maxOfOrNull { it.id.coerceAtLeast(0L) } ?: 0L
        val elements = source.elements.map { element ->
            nextElementId = nextFreshId(nextElementId)
            element.copy(id = nextElementId, config = element.config.toMap())
        }
        return source.copy(id = 0L, name = name, elements = elements)
    }

    private fun nextFreshId(previous: Long): Long = if (previous == Long.MAX_VALUE) 1L else previous + 1L

    private fun ContextExpressionNode.deepCopy(): ContextExpressionNode = copy(
        children = children.map { it.deepCopy() },
    )
}
