package `as`.today.missyou.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Explicit dispatcher set.
 *
 * Nothing in the app is allowed to touch a dispatcher directly, which makes the
 * "never block the main thread" rule mechanically checkable and lets unit tests
 * substitute deterministic dispatchers.
 */
interface AppDispatchers {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

object DefaultAppDispatchers : AppDispatchers {
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val main: CoroutineDispatcher get() = Dispatchers.Main
}
