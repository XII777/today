package `as`.today.missyou.ui

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import `as`.today.missyou.AppContainer
import `as`.today.missyou.domain.model.AppSettings
import `as`.today.missyou.ui.backup.BackupScreen
import `as`.today.missyou.ui.calendar.CalendarScreen
import `as`.today.missyou.ui.editor.EditorScreen
import `as`.today.missyou.ui.home.HomeScreen
import `as`.today.missyou.ui.insights.InsightsScreen
import `as`.today.missyou.ui.journal.JournalScreen
import `as`.today.missyou.ui.lock.LockScreen
import `as`.today.missyou.ui.onboarding.OnboardingScreen
import `as`.today.missyou.ui.reader.ReaderScreen
import `as`.today.missyou.ui.settings.SettingsScreen
import `as`.today.missyou.ui.common.ProvideAppContainer
import java.time.LocalDate

/** Routes beyond the five top-level destinations. */
object Routes {
    const val ARG_DATE = "date"
    const val ARG_ENTRY_ID = "entryId"

    fun entry(date: LocalDate): String = "entry/${ARG_DATE}=$date"
    fun editor(date: LocalDate): String = "editor/${ARG_DATE}=$date"

    const val BACKUP = "backup"
    const val ONBOARDING = "onboarding"
}

@Composable
fun TodayNavHost(
    container: AppContainer,
    navController: NavHostController,
    settings: AppSettings,
    lockRequired: Boolean,
    onUnlocked: () -> Unit,
) {
    val start = remember(settings.onboardingComplete) {
        if (settings.onboardingComplete) "home" else Routes.ONBOARDING
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ProvideAppContainer(container) {
            if (lockRequired) {
                // The lock replaces the whole tree rather than overlaying it, so a
                // locked app cannot be screenshotted, recorded or inspected by an
                // accessibility service that is reading the window.
                LockScreen(
                    appLock = container.appLock,
                    mode = settings.appLockMode,
                    onUnlocked = onUnlocked,
                )
                return@ProvideAppContainer
            }

        val reduceMotion = settings.reduceMotion
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTag = "nav-host" },
            // Horizontal slide with a light fade: directional, not fade-only (§13).
            enterTransition = {
                if (reduceMotion) {
                    fadeIn(tween(90))
                } else {
                    slideInHorizontally(NavigationOffset, initialOffsetX = { it / 6 }) + fadeIn(tween(160))
                }
            },
            exitTransition = {
                if (reduceMotion) fadeOut(tween(90)) else fadeOut(tween(140))
            },
            popEnterTransition = {
                if (reduceMotion) {
                    fadeIn(tween(90))
                } else {
                    slideInHorizontally(NavigationOffset, initialOffsetX = { -it / 6 }) + fadeIn(tween(160))
                }
            },
            popExitTransition = {
                if (reduceMotion) {
                    fadeOut(tween(90))
                } else {
                    slideOutHorizontally(NavigationOffset, targetOffsetX = { it / 6 }) + fadeOut(tween(140))
                }
            },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    container = container,
                    onComplete = {
                        navController.navigate("home") {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable("home") {
                HomeScreen(
                    container = container,
                    onOpenEntry = { date -> navController.navigate(Routes.entry(date)) },
                    onWrite = { date -> navController.navigate(Routes.editor(date)) },
                    onNavigate = { destination -> navController.navigateTo(destination) },
                )
            }

            composable("journal") {
                JournalScreen(
                    container = container,
                    onOpenEntry = { date -> navController.navigate(Routes.entry(date)) },
                    onNavigate = { destination -> navController.navigateTo(destination) },
                )
            }

            composable("calendar") {
                CalendarScreen(
                    container = container,
                    onOpenEntry = { date -> navController.navigate(Routes.entry(date)) },
                    onNavigate = { destination -> navController.navigateTo(destination) },
                )
            }

            composable("insights") {
                InsightsScreen(
                    container = container,
                    onNavigate = { destination -> navController.navigateTo(destination) },
                )
            }

            composable("settings") {
                SettingsScreen(
                    container = container,
                    onNavigate = { destination -> navController.navigateTo(destination) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.BACKUP) {
                BackupScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "entry/{${Routes.ARG_DATE}}",
                arguments = listOf(navArgument(Routes.ARG_DATE) { type = NavType.StringType }),
            ) { backStackEntry ->
                val date = backStackEntry.arguments?.getString(Routes.ARG_DATE)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: container.journal.currentJournalDate()
                ReaderScreen(
                    container = container,
                    date = date,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.editor(date)) },
                )
            }

            composable(
                route = "editor/{${Routes.ARG_DATE}}",
                arguments = listOf(navArgument(Routes.ARG_DATE) { type = NavType.StringType }),
            ) { backStackEntry ->
                val date = backStackEntry.arguments?.getString(Routes.ARG_DATE)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: container.journal.currentJournalDate()
                EditorScreen(
                    container = container,
                    date = date,
                    onBack = { navController.popBackStack() },
                )
            }
            }
        }
    }
}

/**
 * Moves between top-level destinations without stacking duplicates.
 *
 * A single-top launch with `saveState`/`restoreState` is what makes the back
 * button behave sensibly after switching tabs several times.
 */
private fun NavHostController.navigateTo(destination: `as`.today.missyou.ui.navigation.Destination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Horizontal slide spec for navigation transitions.
 *
 * `slideIn/OutHorizontally` animates an [IntOffset], so the `Float` spring in
 * `Springs.Offset` cannot be handed to it. A dedicated spec keeps the navigation
 * feel consistent and records why this token is duplicated rather than shared.
 */
private val NavigationOffset: FiniteAnimationSpec<IntOffset> = spring(
    dampingRatio = 0.9f,
    stiffness = 500f,
)
