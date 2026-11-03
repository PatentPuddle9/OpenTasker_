package com.opentasker.core.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTaskBindingsTest {
    private val tasks = listOf(
        NotificationTaskCandidate(11, "Morning"),
        NotificationTaskCandidate(12, "Evening"),
    )

    @Test
    fun labeledButtonWithoutTaskBindingBecomesDismissButton() {
        val plan = NotificationTaskBindings.planButton(
            mapOf("button1_label" to "Dismiss"),
            buttonIndex = 1,
            candidates = emptyList(),
        )

        assertEquals(NotificationButtonPlan.Dismiss(1, "Dismiss"), plan)
    }

    @Test
    fun explicitlyEmptyTaskFieldBecomesDismissButton() {
        val plan = NotificationTaskBindings.planButton(
            mapOf("button1_label" to "Cancel", "button1_task_id" to ""),
            buttonIndex = 1,
            candidates = emptyList(),
        )

        assertEquals(NotificationButtonPlan.Dismiss(1, "Cancel"), plan)
    }

    @Test
    fun emptyTaskAndEmptyLabelLeaveButtonSlotUnused() {
        val plan = NotificationTaskBindings.planButton(
            emptyMap(),
            buttonIndex = 2,
            candidates = tasks,
        )

        assertEquals(null, plan)
    }

    @Test
    fun selectedTaskStillRunsAndUsesItsNameWhenButtonLabelIsBlank() {
        val plan = NotificationTaskBindings.planButton(
            mapOf("button3_task_id" to "12"),
            buttonIndex = 3,
            candidates = tasks,
        )

        assertEquals(NotificationButtonPlan.RunTask(3, tasks[1], "Evening"), plan)
    }

    @Test
    fun missingSelectedTaskDoesNotSilentlyBecomeDismissButton() {
        val plan = NotificationTaskBindings.planButton(
            mapOf("button1_label" to "Run", "button1_task_id" to "99"),
            buttonIndex = 1,
            candidates = tasks,
        )

        assertTrue(plan is NotificationButtonPlan.Invalid)
    }

    @Test
    fun immutableIdSurvivesTaskRename() {
        val reference = NotificationTaskBindings.parse(mapOf("button1_task_id" to "11"), 1)!!
        val renamed = listOf(NotificationTaskCandidate(11, "Renamed"))

        val resolution = NotificationTaskBindings.resolve(reference, renamed)

        assertEquals(
            NotificationTaskResolution.Bound(renamed.single(), migratedFromLegacyName = false),
            resolution,
        )
    }

    @Test
    fun uniqueLegacyNameMigratesToImmutableId() {
        val reference = NotificationTaskBindings.parse(mapOf("button2_task" to "Evening"), 2)!!

        val resolution = NotificationTaskBindings.resolve(reference, tasks)

        assertEquals(
            NotificationTaskResolution.Bound(tasks[1], migratedFromLegacyName = true),
            resolution,
        )
    }

    @Test
    fun duplicateLegacyNameFailsClosed() {
        val reference = NotificationTaskReference.LegacyName("Duplicate")
        val candidates = listOf(
            NotificationTaskCandidate(1, "Duplicate"),
            NotificationTaskCandidate(2, "Duplicate"),
        )

        val resolution = NotificationTaskBindings.resolve(reference, candidates)

        assertEquals(NotificationTaskResolution.Ambiguous("Duplicate", 2), resolution)
        assertTrue(NotificationTaskBindings.failureMessage(resolution).contains("matches 2 tasks"))
    }

    @Test
    fun deletedIdProducesClearMissingOutcome() {
        val reference = NotificationTaskReference.Id(99)

        val resolution = NotificationTaskBindings.resolve(reference, tasks)

        assertEquals(NotificationTaskResolution.Missing(reference), resolution)
        assertTrue(NotificationTaskBindings.failureMessage(resolution).contains("no longer exists"))
    }

    @Test
    fun invalidIdNeverFallsBackToLegacyName() {
        val reference = NotificationTaskBindings.parse(
            mapOf("button1_task_id" to "not-an-id", "button1_task" to "Morning"),
            1,
        )

        assertEquals(NotificationTaskReference.Invalid("not-an-id"), reference)
        assertFalse(reference is NotificationTaskReference.LegacyName)
    }
}
