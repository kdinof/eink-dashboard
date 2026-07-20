package com.eink.dashboard.modules.taskforge.data

import android.content.Context
import android.util.Base64

data class CachedTaskForgeSnapshot(val bytes: ByteArray, val savedAtEpochMs: Long)

interface TaskForgeSnapshotCache {
    fun load(): CachedTaskForgeSnapshot?
    fun save(bytes: ByteArray, savedAtEpochMs: Long)
    fun clear()
}

class SharedPreferencesTaskForgeSnapshotCache(context: Context) : TaskForgeSnapshotCache {
    private val prefs = context.applicationContext.getSharedPreferences("taskforge_snapshot", Context.MODE_PRIVATE)

    override fun load(): CachedTaskForgeSnapshot? {
        val encoded = prefs.getString("bytes", null) ?: return null
        return runCatching {
            CachedTaskForgeSnapshot(Base64.decode(encoded, Base64.NO_WRAP), prefs.getLong("saved_at", 0L))
        }.getOrNull()
    }

    override fun save(bytes: ByteArray, savedAtEpochMs: Long) {
        prefs.edit()
            .putString("bytes", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .putLong("saved_at", savedAtEpochMs)
            .apply()
    }

    override fun clear() { prefs.edit().clear().apply() }
}
