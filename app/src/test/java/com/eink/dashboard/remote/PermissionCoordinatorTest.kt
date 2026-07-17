package com.eink.dashboard.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PermissionCoordinatorTest {
    @Test
    fun allowsOnlyOnePendingRequestAndCompletesById() {
        val coordinator = PermissionCoordinator()
        val first = coordinator.request(RemotePermission.CALENDAR)!!

        assertThat(coordinator.request(RemotePermission.LOCATION)).isNull()
        coordinator.complete("other-id")
        assertThat(coordinator.pending.value).isEqualTo(first)
        coordinator.complete(first.id)
        assertThat(coordinator.pending.value).isNull()
    }

}
