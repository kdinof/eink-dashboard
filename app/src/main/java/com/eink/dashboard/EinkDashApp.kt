package com.eink.dashboard

import android.app.Application
import com.eink.dashboard.app.AppGraph

/**
 * Application entry point.
 *
 * Intentionally empty for the T01 foundation: no DI container, no eager module
 * wiring, no background work. The dashboard module registry, refresh coordinator
 * and settings store are introduced by T02 on top of this shell.
 */
class EinkDashApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
