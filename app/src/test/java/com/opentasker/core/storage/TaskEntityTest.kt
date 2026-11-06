package com.opentasker.core.storage

import com.opentasker.core.model.CollisionMode
import com.opentasker.core.model.ActionSpec
import com.opentasker.core.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TaskEntityTest {
    @Test
    fun taskEntityRoundTripPreservesActions() {
        val task = Task(
            id = 8,
            name = "Notify",
            collisionMode = CollisionMode.WAIT,
            actions = listOf(
                ActionSpec(
                    id = 3,
                    type = "notify.show",
                    args = mapOf("text" to "hello"),
                    continueOnError = true,
                    condition = "%armed == true",
                ),
            ),
        )

        assertEquals(task, task.toEntity().toDomain())
    }

    @Test
    fun legacyActionBlocksReceiveStableUniqueIdsWhenDecoded() {
        val entity = TaskEntity(
            id = 10,
            name = "Legacy actions",
            priority = 5,
            collisionMode = CollisionMode.ABORT_NEW.name,
            actionsJson = StorageJson.encodeToString(
                listOf(
                    ActionSpec(type = "notify.show"),
                    ActionSpec(type = "toast.show"),
                    ActionSpec(id = 42, type = "log"),
                    ActionSpec(id = 42, type = "vibrate"),
                ),
            ),
        )

        val first = entity.toDomain().actions
        val second = entity.toDomain().actions

        assertEquals(listOf(43L, 44L, 42L, 45L), first.map { it.id })
        assertEquals(first, second)
        assertEquals(first.size, first.map { it.id }.distinct().size)
    }

    @Test
    fun malformedActionsJsonReturnsFallbackWithDecodeIssue() {
        val entity = TaskEntity(
            id = 9,
            name = "Corrupted task",
            priority = 5,
            collisionMode = CollisionMode.WAIT.name,
            actionsJson = "{not-json",
        )
        val result = entity.toDomainDecodeResult()

        assertEquals(emptyList<com.opentasker.core.model.ActionSpec>(), result.value.actions)
        assertEquals(CollisionMode.WAIT, result.value.collisionMode)
        val issue = result.issue
        assertNotNull(issue)
        issue!!
        assertEquals(StorageRecordType.TASK, issue.recordType)
        assertEquals(9L, issue.recordId)
        assertEquals("actionsJson", issue.fieldName)
        assertThrows(CorruptStoredRecordException::class.java) { entity.toDomain() }
    }
}
