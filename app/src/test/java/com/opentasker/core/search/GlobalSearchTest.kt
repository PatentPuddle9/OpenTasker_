package com.opentasker.core.search

import com.opentasker.core.model.ActionExecutionBinding
import com.opentasker.core.model.ActionSpec
import com.opentasker.core.model.resolveActionExecutionBindings
import com.opentasker.core.model.Profile
import com.opentasker.core.model.Scene
import com.opentasker.core.model.SceneElement
import com.opentasker.core.model.SceneElementType
import com.opentasker.core.model.Task
import com.opentasker.core.model.Variable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchTest {
    private val task = Task(
        id = 10,
        name = "Build report",
        actions = listOf(
            ActionSpec(
                id = 100,
                type = "notify.show",
                label = "Report ready",
                args = mapOf("text" to "%ReportName"),
            ),
        ),
    )
    private val profile = Profile(id = 20, name = "Morning report", enterTaskId = task.id)
    private val variable = Variable(name = "ReportName", value = "Quarterly report", isGlobal = true)
    private val scene = Scene(
        id = 30,
        name = "Report dashboard",
        widthDp = 320,
        heightDp = 240,
        elements = listOf(
            SceneElement(
                type = SceneElementType.BUTTON,
                xDp = 0,
                yDp = 0,
                widthDp = 100,
                heightDp = 48,
                config = mapOf("label" to "Build report"),
                tapTaskId = task.id,
            ),
        ),
    )

    @Test
    fun searchFindsTopLevelActionsAndVariablesButNotNestedExecutionSteps() {
        val results = searchGlobalEntities(
            "reportname",
            listOf(profile),
            listOf(variable),
            listOf(scene),
        )

        assertTrue(results.any { it.kind == GlobalSearchResultKind.VARIABLE && it.variableName == "ReportName" })
        assertTrue(results.none { it.title == task.name })
        assertTrue(results.none { it.detail.contains("action 1", ignoreCase = true) })
    }

    @Test
    fun searchDoesNotExposeBackingExecutionRecordsAsStandaloneResults() {
        val results = searchGlobalEntities(
            "build report",
            listOf(profile),
            emptyList(),
            listOf(scene),
        )

        assertEquals(
            setOf(GlobalSearchResultKind.SCENE),
            results.map { it.kind }.toSet(),
        )
    }

    @Test
    fun secretValueIsNotSearchable() {
        val secret = variable.copy(name = "ApiToken", value = "never-index-this", isSecret = true)

        val results = searchGlobalEntities("never-index-this", emptyList(), emptyList(), listOf(secret), emptyList())

        assertTrue(results.isEmpty())
    }
    @Test
    fun actionBindingResolverHidesWidgetOwnedAndMissingExecutionRecords() {
        val zulu = Profile(id = 1, name = "Zulu", enterTaskId = 10)
        val alpha = Profile(id = 2, name = "Alpha", enterTaskId = 20)
        val missing = Profile(id = 3, name = "Missing", enterTaskId = 30)
        val widgetOwned = Profile(id = 4, name = "Widget", enterTaskId = 40, widgetId = 7)
        val executions = listOf(
            Task(id = 10, name = "internal zulu"),
            Task(id = 20, name = "internal alpha"),
            Task(id = 40, name = "internal widget"),
        )

        val resolved = resolveActionExecutionBindings(
            actions = listOf(zulu, missing, widgetOwned, alpha),
            executionRecords = executions,
        )

        assertEquals(listOf("Alpha", "Zulu"), resolved.map { it.action.name })
        assertEquals(listOf(20L, 10L), resolved.map { it.execution.id })
        assertTrue(resolved.none { it.action.widgetId != null })
        assertTrue(resolved.none { it.action.name == "Missing" })
    }

}
