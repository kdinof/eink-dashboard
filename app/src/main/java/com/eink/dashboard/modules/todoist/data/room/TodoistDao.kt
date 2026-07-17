package com.eink.dashboard.modules.todoist.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverter

/** Room DAO for the Todoist cache and pending-op queue. */
@Dao
interface TodoistDao {

    // ---- Tasks ----

    @Query("DELETE FROM todoist_tasks WHERE view = :view")
    suspend fun clearTasks(view: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<CachedTaskEntity>)

    @Query("SELECT * FROM todoist_tasks WHERE view = :view ORDER BY `order` ASC")
    suspend fun tasks(view: String): List<CachedTaskEntity>

    @Query("UPDATE todoist_tasks SET locallyCompleted = :completed WHERE id = :taskId")
    suspend fun setLocallyCompleted(taskId: String, completed: Boolean)

    // ---- Projects ----

    @Query("DELETE FROM todoist_projects")
    suspend fun clearProjects()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<CachedProjectEntity>)

    @Query("SELECT * FROM todoist_projects")
    suspend fun projects(): List<CachedProjectEntity>

    // ---- Pending ops ----

    @Insert
    suspend fun insertPending(op: PendingOpEntity): Long

    @Query("SELECT * FROM todoist_pending_ops ORDER BY id ASC")
    suspend fun pendingOps(): List<PendingOpEntity>

    @Query("DELETE FROM todoist_pending_ops WHERE id = :opId")
    suspend fun removePending(opId: Long)

    @Query("UPDATE todoist_pending_ops SET attempts = attempts + 1 WHERE id = :opId")
    suspend fun bumpAttempts(opId: Long)
}

/** Persists a display-only `List<String>` of labels as one newline-joined column. */
class Converters {
    @TypeConverter
    fun fromLabels(labels: List<String>): String = labels.joinToString("\n")

    @TypeConverter
    fun toLabels(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("\n")
}
