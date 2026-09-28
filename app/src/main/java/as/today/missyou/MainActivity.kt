package `as`.today.missyou

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import `as`.today.missyou.domain.model.AppLockMode
import `as`.today.missyou.domain.model.AppSettings
import `as`.today.missyou.ui.TodayNavHost
import `as`.today.missyou.ui.theme.TodayTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The single activity.
 *
 * It owns three things and nothing else: the edge-to-edge window, the `FLAG_SECURE`
 * window flag, and the Compose root. Keeping the surface area this small is what
 * makes the manifest's `configChanges` entry safe and stops an ordinary rotation
 * from tearing down the database.
 */
class MainActivity : androidx.fragment.app.FragmentActivity() {

    private val container: AppContainer get() = (application as TodayApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Sealing expired days must happen before the UI claims anything about
        // history, but it must never block the first frame.
        lifecycleScope.launch { container.initialise() }

        // Every foreground is a chance for the day to have rolled over, so the
        // rollover time is re-observed and expired entries are sealed.
        //
        // `repeatOnLifecycle` is registered here rather than in `onStart`: it
        // suspends until the state is reached and restarts cleanly, whereas
        // launching it per-`onStart` accumulates work over repeated foregrounds.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.settings.settings
                    .map { it.rolloverMinutes }
                    .distinctUntilChanged()
                    .collect { container.journal.synchroniseClock() }
            }
        }

        setContent {
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            SecureWindowEffect(enabled = settings.disableScreenshots || settings.appLockMode != AppLockMode.OFF)

            TodayTheme(settings = settings) {
                val navController = rememberNavController()
                val unlocked by container.appLock.isUnlocked.collectAsStateWithLifecycle()

                TodayNavHost(
                    container = container,
                    navController = navController,
                    settings = settings,
                    lockRequired = settings.appLockMode != AppLockMode.OFF && !unlocked,
                    onUnlocked = { container.appLock.markUnlocked() },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app re-locks, so the recent-apps switcher never reveals
        // journal text and returning requires authentication again.
        container.appLock.lock()
    }
}

/**
 * Applies or clears `FLAG_SECURE` for as long as the setting says so.
 *
 * The flag blocks screenshots and screen recording, and removes the window from
 * the recent-apps thumbnail, which is the concrete behaviour behind "secure
 * screenshots where appropriate" in the privacy settings.
 */
@Composable
private fun SecureWindowEffect(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window
        if (window != null) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        onDispose { }
    }
}
