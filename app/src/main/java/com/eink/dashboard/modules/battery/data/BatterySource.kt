package com.eink.dashboard.modules.battery.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.roundToInt

/**
 * Present battery state, reduced to what the block shows: a whole-percent [percent]
 * and whether the device is [charging]. Kept free of Android types so [compute] is
 * unit-tested directly (the mapping is the part worth testing).
 */
data class BatteryStatus(val percent: Int, val charging: Boolean) {
    companion object {
        /**
         * Percent from the raw `level`/`scale` pair (as delivered by
         * `ACTION_BATTERY_CHANGED`), clamped to 0..100. A non-positive `scale` (which
         * some emulated events send) yields 0 rather than dividing by zero.
         */
        fun compute(level: Int, scale: Int, charging: Boolean): BatteryStatus {
            val percent = if (scale > 0) {
                ((level.toDouble() / scale) * 100).roundToInt().coerceIn(0, 100)
            } else {
                0
            }
            return BatteryStatus(percent = percent, charging = charging)
        }
    }
}

/**
 * Reads the current battery state on demand. The Android implementation reads the
 * **sticky** `ACTION_BATTERY_CHANGED` broadcast synchronously, so there is no live
 * receiver and no background work — the shell's foreground-only refresh drives it,
 * honouring the frozen module contract (no self-scheduled loops).
 */
fun interface BatterySource {
    /** Latest known battery state, or null if it could not be read. */
    fun read(): BatteryStatus?
}

/**
 * Real source over the sticky battery broadcast. `registerReceiver(null, filter)`
 * returns the last broadcast Intent immediately without registering a receiver, so
 * this is a cheap synchronous read that still uses the platform's BroadcastReceiver
 * plumbing (T05: "implement BatteryManager/BroadcastReceiver").
 */
class AndroidBatterySource(private val context: Context) : BatterySource {
    override fun read(): BatteryStatus? {
        val intent: Intent = context.applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return null

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        if (level < 0 || scale <= 0) return null
        return BatteryStatus.compute(level = level, scale = scale, charging = charging)
    }
}
