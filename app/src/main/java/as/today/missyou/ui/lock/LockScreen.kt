package `as`.today.missyou.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import `as`.today.missyou.domain.model.AppLockMode
import `as`.today.missyou.security.AppLockManager
import `as`.today.missyou.ui.components.PrimaryButton
import `as`.today.missyou.ui.components.SecondaryButton
import `as`.today.missyou.ui.components.rememberHaptics
import `as`.today.missyou.ui.theme.Radii
import `as`.today.missyou.ui.theme.Spacing

/**
 * The app lock screen (§20).
 *
 * Two ways in, matching the modes the user can enable: a PIN, and the device's own
 * biometric or credential prompt. There is deliberately no recovery code and no
 * "forgot PIN" escape hatch. A forgotten PIN means the encrypted journal cannot be
 * opened, and the copy says so rather than hiding it.
 */
@Composable
fun LockScreen(
    appLock: AppLockManager,
    mode: AppLockMode,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val canUsePin = mode == AppLockMode.PIN || mode == AppLockMode.PIN_OR_BIOMETRIC
    val canUseDeviceAuth = appLock.isDeviceAuthAvailable()

    fun launchDeviceAuth() {
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    haptics.strong()
                    appLock.markUnlocked()
                    onUnlocked()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (code != BiometricPrompt.ERROR_USER_CANCELED &&
                        code != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        error = message.toString()
                    }
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Today")
                .setSubtitle("Your journal stays on this device")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build(),
        )
    }

    // Offer device authentication straight away when that is the only way in.
    LaunchedEffect(mode) {
        if (mode == AppLockMode.BIOMETRIC && canUseDeviceAuth) launchDeviceAuth()
    }

    fun attemptPin() {
        haptics.medium()
        if (appLock.verifyPin(pin.toCharArray())) {
            onUnlocked()
        } else {
            error = "That PIN did not match."
            pin = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (canUsePin) Icons.Filled.Lock else Icons.Filled.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "Today is locked",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = if (canUsePin) "Enter your PIN to open your journal." else "Authenticate to open your journal.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (canUsePin) {
            Spacer(Modifier.height(Spacing.xl))
            PinField(
                value = pin,
                onValueChange = {
                    if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) {
                        pin = it
                        error = null
                    }
                },
                onDone = { if (pin.length == PIN_LENGTH) attemptPin() },
            )
            if (error != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PrimaryButton(
                    text = "Unlock",
                    enabled = pin.length == PIN_LENGTH,
                    onClick = { attemptPin() },
                )
                if (mode == AppLockMode.PIN_OR_BIOMETRIC && canUseDeviceAuth) {
                    Spacer(Modifier.width(Spacing.xs))
                    SecondaryButton(
                        text = "Use biometrics",
                        icon = Icons.Filled.Fingerprint,
                        onClick = { launchDeviceAuth() },
                    )
                }
            }
        } else {
            Spacer(Modifier.height(Spacing.xl))
            PrimaryButton(
                text = "Unlock",
                icon = Icons.Filled.Fingerprint,
                onClick = { launchDeviceAuth() },
            )
        }
    }
}

/**
 * A visible, masked PIN field.
 *
 * A row of dots with a hidden text field underneath is a common shortcut, but it
 * defeats TalkBack and hardware keyboards. A real field with a password visual
 * transformation is both accessible and simpler.
 */
@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            textAlign = TextAlign.Center,
            letterSpacing = 12.sp,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = PasswordVisualTransformation(),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(Radii.medium))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = Spacing.sm, horizontal = Spacing.md)
            .semantics { contentDescription = "Journal PIN" },
    )
}

private const val PIN_LENGTH = 6
