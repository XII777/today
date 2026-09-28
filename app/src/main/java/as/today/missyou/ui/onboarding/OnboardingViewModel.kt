package `as`.today.missyou.ui.onboarding

import androidx.lifecycle.viewModelScope
import `as`.today.missyou.AppContainer
import `as`.today.missyou.ui.common.AppViewModel
import kotlinx.coroutines.launch

/**
 * Onboarding state.
 *
 * Deliberately tiny: onboarding records one fact — that it has been seen — and
 * nothing else. The screens do not need to survive process death mid-swipe,
 * because finishing early and finishing last are equivalent.
 */
class OnboardingViewModel(container: AppContainer) : AppViewModel(container) {

    fun markComplete() {
        viewModelScope.launch {
            container.settings.update { it.copy(onboardingComplete = true) }
        }
    }
}
