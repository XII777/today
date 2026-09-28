package `as`.today.missyou.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import `as`.today.missyou.AppContainer

/**
 * Makes the [AppContainer] reachable from `containerViewModel` factories.
 *
 * The container is provided once at the top of the Compose graph. ViewModels then
 * take their dependencies through this helper instead of each screen assembling
 * them by hand, which keeps constructors uniform and trivially testable.
 */
val LocalAppContainerView = staticCompositionLocalOf<AppContainer?> { null }

@Composable
fun ProvideAppContainer(container: AppContainer, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppContainerView provides container, content = content)
}

/** Base class for view models that only need the application container. */
abstract class AppViewModel(protected val container: AppContainer) : ViewModel()

/**
 * Creates a view model from a lambda that receives the container.
 *
 * ```
 * val viewModel = containerViewModel { HomeViewModel(it) }
 * ```
 *
 * The factory is remembered so a configuration change reuses the same instance
 * rather than constructing a second one.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = requireNotNull(LocalAppContainerView.current) {
        "AppContainer was not provided to the composition"
    }
    val factory = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create(container) as T
        }
    }
    return viewModel(modelClass = VM::class.java, key = key, factory = factory)
}
