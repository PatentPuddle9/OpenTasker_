package com.opentasker.ui.screens

import com.opentasker.core.actions.ActionMetadata
import com.opentasker.core.engine.FlowControl
import com.opentasker.core.model.ActionSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionPickerCategoryFilterTest {
    private fun item(id: String, category: String) = LocalizedActionMetadata(
        metadata = ActionMetadata(
            id = id,
            nameRes = 0,
            descriptionRes = 0,
            categoryRes = 0,
        ),
        name = id,
        description = "",
        category = category,
    )

    @Test
    fun allCategoryKeepsActionsFromEveryCategory() {
        val items = listOf(
            item("app.launch", "App"),
            item("toast.show", "Notification"),
            item("file.read", "File"),
        )

        assertEquals(items, filterActionPickerItemsByCategory(items, "All"))
    }

    @Test
    fun fallbackPickerListsConcreteActionBlocksButNotTheCurrentSignInOrFlowMarkers() {
        val actions = listOf(
            ActionSpec(id = 1, type = "auth.device_credential"),
            ActionSpec(id = 2, type = "notify.show"),
            ActionSpec(id = 3, type = FlowControl.TRY),
            ActionSpec(id = 6, type = "task.run"),
            ActionSpec(id = 0, type = "toast.show"),
            ActionSpec(id = 4, type = "toast.show"),
            ActionSpec(id = 5, type = "log"),
        )

        assertEquals(
            listOf(2L, 5L),
            selectableFallbackActionBlocks(actions, currentActionId = 4L).map { it.id },
        )
    }

    @Test
    fun selectedCategoryOnlyShowsActionsFromThatCategory() {
        val items = listOf(
            item("app.launch", "App"),
            item("toast.show", "Notification"),
            item("notify.show", "Notification"),
            item("file.read", "File"),
        )

        assertEquals(
            listOf("toast.show", "notify.show"),
            filterActionPickerItemsByCategory(items, "Notification").map { it.metadata.id },
        )
        assertEquals(
            listOf("app.launch"),
            filterActionPickerItemsByCategory(items, "App").map { it.metadata.id },
        )
    }
}
