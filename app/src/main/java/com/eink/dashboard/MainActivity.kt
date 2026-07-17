package com.eink.dashboard

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.eink.dashboard.dashboard.DashboardViewModel
import com.eink.dashboard.dashboard.ui.DashboardHost
import com.eink.dashboard.settings.DashboardSettings
import com.eink.dashboard.settings.OrientationSetting
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The single Activity that hosts the whole dashboard (single-activity + Compose —
 * see docs/adr/0001-architecture.md).
 *
 * Responsibilities kept at the Activity level (things Compose cannot own):
 * - immersive fullscreen (hide system bars) so the dashboard uses the full panel;
 * - applying the persisted orientation lock and keep-screen-on flag;
 * - forwarding foreground/background to the [DashboardViewModel] so the refresh
 *   coordinator ticks only while visible and refreshes immediately on resume.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersiveMode()

        setContent {
            DashboardHost(viewModel)
        }

        // Apply persisted display settings whenever they change, while at least
        // STARTED. distinctUntilChanged avoids redundant window churn.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings
                    .distinctUntilChanged { a, b ->
                        a.orientation == b.orientation && a.keepScreenOn == b.keepScreenOn
                    }
                    .collect(::applyDisplaySettings)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-assert immersive mode (system bars can reappear after a transient swipe).
        enterImmersiveMode()
        viewModel.onEnterForeground()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onEnterBackground()
    }

    private fun applyDisplaySettings(settings: DashboardSettings) {
        requestedOrientation = when (settings.orientation) {
            OrientationSetting.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationSetting.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationSetting.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun enterImmersiveMode() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
