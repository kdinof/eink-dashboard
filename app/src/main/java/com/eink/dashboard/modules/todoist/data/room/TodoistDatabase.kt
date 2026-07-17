package com.eink.dashboard.modules.todoist.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for the Todoist module cache (schema v1).
 *
 * Holds cached tasks, projects and the durable pending-operation queue — never the
 * token. `exportSchema = true` writes the schema JSON to `app/schemas/` so future
 * schema changes ship an explicit migration (there are none at v1). When R8 lands
 * (T09), keep rules for these entities are already noted in `proguard-rules.pro`.
 */
@Database(
    entities = [CachedTaskEntity::class, CachedProjectEntity::class, PendingOpEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TodoistDatabase : RoomDatabase() {

    abstract fun dao(): TodoistDao

    companion object {
        @Volatile
        private var instance: TodoistDatabase? = null

        fun get(context: Context): TodoistDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TodoistDatabase::class.java,
                    "todoist.db",
                ).build().also { instance = it }
            }
    }
}
