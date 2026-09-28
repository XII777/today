package `as`.today.missyou.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `as`.today.missyou.AppContainer
import `as`.today.missyou.ui.common.containerViewModel
import kotlinx.coroutines.launch
import `as`.today.missyou.ui.components.PrimaryButton
import `as`.today.missyou.ui.components.SecondaryButton
import `as`.today.missyou.ui.theme.Spacing

/**
 * First-run experience (§50).
 *
 * Four short pages explaining the one idea that matters. There is no sign-up, no
 * account and no permission request: the app is already fully offline by the time
 * this is shown, so asking for anything here would be theatre.
 */
@Composable
fun OnboardingScreen(
    container: AppContainer,
    onComplete: () -> Unit,
) {
    val viewModel = containerViewModel { OnboardingViewModel(it) }
    val steps = remember { ONBOARDING_STEPS }
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == steps.lastIndex

    fun finish() {
        viewModel.markComplete()
        onComplete()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            OnboardingPage(
                icon = steps[page].icon,
                title = steps[page].title,
                body = steps[page].body,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                repeat(steps.size) { dotIndex ->
                    Box(
                        Modifier
                            .size(if (dotIndex == pagerState.currentPage) 22.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (dotIndex == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            )
                            .semantics {},
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            if (isLastPage) {
                PrimaryButton(
                    text = "Start writing",
                    onClick = { finish() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    PrimaryButton(
                        text = "Next",
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(text = "Skip", onClick = { finish() })
                }
            }
        }
    }
}

/** One onboarding step. Named `OnboardingStep` so it cannot collide with the
 * [OnboardingPage] composable, whose call signature is identical. */
private data class OnboardingStep(val icon: ImageVector, val title: String, val body: String)

private val ONBOARDING_STEPS = listOf(
    OnboardingStep(
        icon = Icons.Filled.LockClock,
        title = "Today becomes history",
        body = "You can edit today's entry until the day rolls over. After that it is preserved exactly as you wrote it, and nothing can change it.",
    ),
    OnboardingStep(
        icon = Icons.Filled.CloudOff,
        title = "Entirely offline",
        body = "No account, no server, no analytics, no internet permission. Your writing never leaves this device, because there is nowhere for it to go.",
    ),
    OnboardingStep(
        icon = Icons.Filled.Lock,
        title = "Private by construction",
        body = "Entries are encrypted with a key held in the device's secure hardware. Journal content is sealed before it reaches storage.",
    ),
    OnboardingStep(
        icon = Icons.Filled.Smartphone,
        title = "You own your journal",
        body = "Export everything to Markdown, plain text, HTML or JSON whenever you like, with no subscription and no account required.",
    ),
)

@Composable
private fun OnboardingPage(icon: ImageVector, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl, vertical = Spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
