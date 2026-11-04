package com.opentasker.core.search

import com.opentasker.core.model.Profile
import com.opentasker.core.model.Scene
import com.opentasker.core.model.Variable
import java.util.Locale

enum class GlobalSearchResultKind {
    PROFILE,
    VARIABLE,
    SCENE,
}

data class GlobalSearchResult(
    val kind: GlobalSearchResultKind,
    val entityId: Long,
    val title: String,
    val detail: String,
    val projectId: Long,
    val actionIndex: Int? = null,
    val variableName: String? = null,
)

/**
 * Searches the current in-memory entity snapshots. Keeping this pure makes the index live as
 * Room flows change, while keeping search unavailable to secret plaintext and network services.
 */
fun searchGlobalEntities(
    query: String,
    profiles: List<Profile>,
    variables: List<Variable>,
    scenes: List<Scene>,
): List<GlobalSearchResult> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT).removePrefix("%")
    if (normalizedQuery.isEmpty()) return emptyList()

    val matching = buildList {
        variables.forEach { variable ->
            val searchable = listOf(
                variable.name,
                if (variable.isSecret) "" else variable.value,
            ).joinToString(" ")
            if (searchable.contains(normalizedQuery, ignoreCase = true)) {
                add(
                    GlobalSearchResult(
                        kind = GlobalSearchResultKind.VARIABLE,
                        entityId = stableVariableId(variable),
                        title = "%${variable.name}",
                        detail = if (variable.isSecret) "Secret variable" else "Global variable",
                        projectId = variable.projectId,
                        variableName = variable.name,
                    )
                )
            }
        }

        profiles.forEach { profile ->
            val contextText = buildString {
                profile.contexts.forEach { context ->
                    append(context.type.name)
                    context.config.forEach { (key, value) ->
                        append(' ')
                        append(key)
                        append(' ')
                        append(value)
                    }
                }
            }
            val searchable = listOf(
                profile.name,
                profile.group.orEmpty(),
                contextText,
            ).joinToString(" ")
            if (searchable.contains(normalizedQuery, ignoreCase = true)) {
                add(
                    GlobalSearchResult(
                        kind = GlobalSearchResultKind.PROFILE,
                        entityId = profile.id,
                        title = profile.name,
                        detail = "Action${profile.group?.let { " • $it" }.orEmpty()}",
                        projectId = profile.projectId,
                    )
                )
            }
        }

        scenes.forEach { scene ->
            val searchable = buildString {
                append(scene.name)
                scene.elements.forEach { element ->
                    append(' ').append(element.type.name)
                    element.config.forEach { (key, value) -> append(' ').append(key).append(' ').append(value) }
                }
            }
            if (searchable.contains(normalizedQuery, ignoreCase = true)) {
                add(
                    GlobalSearchResult(
                        kind = GlobalSearchResultKind.SCENE,
                        entityId = scene.id,
                        title = scene.name,
                        detail = "${scene.elements.size} elements",
                        projectId = scene.projectId,
                    )
                )
            }
        }
    }
    return matching.sortedWith(compareBy({ it.kind.ordinal }, { it.title.lowercase(Locale.ROOT) }, { it.entityId }))
}

/** Variable names are not database IDs; this is only a stable UI key for a result row. */
private fun stableVariableId(variable: Variable): Long =
    (variable.projectId * 31L + variable.name.lowercase(Locale.ROOT).hashCode()).coerceAtLeast(0L)
