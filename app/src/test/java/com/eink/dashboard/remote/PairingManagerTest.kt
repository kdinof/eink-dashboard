package com.eink.dashboard.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PairingManagerTest {
    private lateinit var context: Context
    private var now = 1_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("remote_pairing", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun successfulPairStoresOnlyHashAndRotatesPin() {
        val manager = PairingManager(context, clock = { now })
        val originalPin = manager.pin.value.value

        val result = manager.pair(originalPin, "My phone", "192.168.1.4") as PairResult.Success

        assertThat(manager.authenticate(result.token)?.clientName).isEqualTo("My phone")
        assertThat(manager.pin.value.value).isNotEqualTo(originalPin)
        assertThat(manager.sessions.value.single().tokenHash).isNotEqualTo(result.token)
        val persisted = context.getSharedPreferences("remote_pairing", Context.MODE_PRIVATE)
            .getString("sessions", "")
        assertThat(persisted).doesNotContain(result.token)
    }

    @Test
    fun sessionRemainsValidUntilRevoked() {
        val manager = PairingManager(context, clock = { now })
        val result = manager.pair(manager.pin.value.value, "PC", "192.168.1.5") as PairResult.Success

        now += 365L * 24 * 60 * 60 * 1_000
        assertThat(manager.authenticate(result.token)).isNotNull()

        manager.revoke(result.session.id)
        assertThat(manager.authenticate(result.token)).isNull()
    }

    @Test
    fun sixthFailureFromSameAddressIsRateLimited() {
        val manager = PairingManager(context, clock = { now })

        repeat(5) {
            assertThat(manager.pair("wrong", "Browser", "192.168.1.9")).isEqualTo(PairResult.Rejected)
        }
        assertThat(manager.pair(manager.pin.value.value, "Browser", "192.168.1.9"))
            .isEqualTo(PairResult.RateLimited)
    }

    @Test
    fun expiredPinIsRejected() {
        val manager = PairingManager(context, clock = { now })
        val pin = manager.pin.value.value
        now += 10 * 60_000L

        assertThat(manager.pair(pin, "Browser", "192.168.1.7")).isEqualTo(PairResult.Rejected)
    }
}
