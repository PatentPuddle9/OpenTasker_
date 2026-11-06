package com.opentasker.core.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.serialization.encodeToString
import com.opentasker.core.model.ActionSpec
import com.opentasker.core.model.CollisionMode
import com.opentasker.core.model.Task
import com.opentasker.core.model.DEFAULT_PROJECT_ID

@Entity("tasks", indices = [Index("projectId")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val priority: Int,
    val collisionMode: String,
    val actionsJson: String,
    @androidx.room.ColumnInfo(defaultValue = "1") val projectId: Long = DEFAULT_PROJECT_ID,
) {
    fun toDomain(): Task = toDomainDecodeResult().requireDecoded()

    fun toDomainDecodeResult(): StorageDecodeResult<Task> {
        val mode = runCatching { CollisionMode.valueOf(collisionMode) }
            .getOrElse { error ->
                return StorageDecodeResult(
                    value = Task(id, name, priority, CollisionMode.ABORT_NEW, emptyList(), projectId),
                    issue = StorageDecodeIssue(
                        recordType = StorageRecordType.TASK,
                        recordId = id,
                        recordName = name,
                        fieldName = "collisionMode",
                        message = error.storageDecodeMessage(),
                    ),
                )
            }

        val decodedActions = runCatching { StorageJson.decodeFromString<List<ActionSpec>>(actionsJson) }
            .getOrElse { error ->
                return StorageDecodeResult(
                    value = Task(id, name, priority, mode, emptyList(), projectId),
                    issue = StorageDecodeIssue(
                        recordType = StorageRecordType.TASK,
                        recordId = id,
                        recordName = name,
                        fieldName = "actionsJson",
                        message = error.storageDecodeMessage(),
                    ),
                )
            }
        // Action IDs are per-task identities used by action-block references (for example,
        // Sign In fallback actions). Older tasks may have been saved before those identities
        // existed and contain zero/duplicate IDs. Normalize them deterministically on decode so
        // editors and the runtime resolve the same block, and the next edit persists the IDs.
        val actions = normalizeActionSpecIds(decodedActions)

        return StorageDecodeResult(
            value = Task(id, name, priority, mode, actions, projectId),
        )
    }
}

private fun normalizeActionSpecIds(actions: List<ActionSpec>): List<ActionSpec> {
    val usedIds = mutableSetOf<Long>()
    var candidate = actions.mapNotNull { it.id.takeIf { id -> id > 0L } }.maxOrNull() ?: 0L

    fun nextId(): Long {
        do {
            candidate = if (candidate == Long.MAX_VALUE) 1L else candidate + 1L
        } while (candidate <= 0L || candidate in usedIds)
        return candidate
    }

    return actions.map { action ->
        if (action.id > 0L && usedIds.add(action.id)) {
            action
        } else {
            val id = nextId()
            usedIds += id
            action.copy(id = id)
        }
    }
}

fun Task.toEntity() = TaskEntity(
    id, name, priority, collisionMode.name, StorageJson.encodeToString(actions), projectId
)

@Dao
interface TaskDao {
    @Insert suspend fun insert(t: TaskEntity): Long
    @Update suspend fun update(t: TaskEntity)
    @Delete suspend fun delete(t: TaskEntity)
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun getById(id: Long): TaskEntity?
    @Query("SELECT * FROM tasks") suspend fun getAll(): List<TaskEntity>
    @Query("SELECT * FROM tasks") fun getAllAsFlow(): kotlinx.coroutines.flow.Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE name = :name LIMIT 1") suspend fun getByName(name: String): TaskEntity?

    /** See [ProfileDao.getByNameIgnoreCase] for the ASCII-folding caveat. */
    @Query("SELECT * FROM tasks WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByNameIgnoreCase(name: String): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks") suspend fun countAll(): Int
    @Query("UPDATE tasks SET projectId = :targetProjectId WHERE projectId = :sourceProjectId")
    suspend fun reassignProject(sourceProjectId: Long, targetProjectId: Long)
}
