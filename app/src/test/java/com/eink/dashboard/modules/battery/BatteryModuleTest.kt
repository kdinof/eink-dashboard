package com.eink.dashboard.modules.battery

import com.eink.dashboard.core.time.TimeSource
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.battery.data.BatterySource
import com.eink.dashboard.modules.battery.data.BatteryStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Battery percent mapping (from the raw `level`/`scale` an `ACTION_BATTERY_CHANGED`
 * event carries) and the module state machine. `adb shell dumpsys battery set …`
 * feeds the same level/scale/status extras, so this covers the emulated-event path.
 */
class BatteryModuleTest {

    @Test
    fun compute_percentFromLevelAndScale() {
        assertThat(BatteryStatus.compute(level = 84, scale = 100, charging = false).percent).isEqualTo(84)
        // Some devices report a non-100 scale.
        assertThat(BatteryStatus.compute(level = 50, scale = 200, charging = false).percent).isEqualTo(25)
        assertThat(BatteryStatus.compute(level = 1, scale = 3, charging = false).percent).isEqualTo(33)
    }

    @Test
    fun compute_guardsBadScale_andClamps() {
        assertThat(BatteryStatus.compute(level = 50, scale = 0, charging = false).percent).isEqualTo(0)
        assertThat(BatteryStatus.compute(level = 250, scale = 100, charging = false).percent).isEqualTo(100)
    }

    @Test
    fun compute_carriesChargingFlag() {
        assertThat(BatteryStatus.compute(90, 100, charging = true).charging).isTrue()
        assertThat(BatteryStatus.compute(90, 100, charging = false).charging).isFalse()
    }

    @Test
    fun contract_isEveryMinute_andNotDemo() {
        val m = BatteryModule(BatterySource { BatteryStatus(80, false) })
        assertThat(m.id).isEqualTo("battery")
        assertThat(m.refreshPolicy).isEqualTo(RefreshPolicy.EveryMinute)
        assertThat(m.isDemo).isFalse()
    }

    @Test
    fun refresh_readsSource_intoOkState(): Unit = runBlocking {
        // Simulate a discharge event, then a charge event on the next tick.
        var reading = BatteryStatus(64, charging = false)
        val m = BatteryModule(BatterySource { reading }, timeSource = TimeSource { 777L })

        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Ok::class.java)
        assertThat(m.state.value.lastUpdatedEpochMs).isEqualTo(777L)
        assertThat(m.status.value).isEqualTo(BatteryStatus(64, false))

        reading = BatteryStatus(65, charging = true)
        m.refresh(RefreshReason.MINUTE_TICK)
        assertThat(m.status.value?.charging).isTrue()
        assertThat(m.status.value?.percent).isEqualTo(65)
    }

    @Test
    fun refresh_withUnreadableSource_yieldsError(): Unit = runBlocking {
        val m = BatteryModule(BatterySource { null })
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Error::class.java)
        assertThat(m.status.value).isNull()
    }
}
