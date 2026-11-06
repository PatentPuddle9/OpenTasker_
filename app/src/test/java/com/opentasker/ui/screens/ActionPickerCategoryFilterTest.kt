package com.opentasker.ui.screens

import com.opentasker.core.actions.ActionMetadata
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
