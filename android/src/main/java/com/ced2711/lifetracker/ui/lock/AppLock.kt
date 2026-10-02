package com.ced2711.lifetracker.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.theme.LifeTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.model.AppLockTimeout
import com.ced2711.lifetracker.domain.model.appLockExpired
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.translateUiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Process-wide app lock state. The lock only gates the UI: it adds no encryption, so it never
 * holds keys and survives activity recreation without asking again. A new process starts locked.
 */
class AppLockController(
    settings: Flow<AppSettings>,
    scope: CoroutineScope,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private data class Config(val enabled: Boolean, val timeout: AppLockTimeout)

    private val config = settings
        .map { Config(it.appLockEnabled, it.appLockTimeout) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)
    private val unlocked = MutableStateFlow(false)
    private var backgroundedAt: Long? = null

    /** True while this lock's own prompt is showing; a credential screen must not relock the app. */
    @Volatile var authenticating: Boolean = false

    /** Null until settings have loaded, so the UI never flashes content before the lock appears. */
    val locked: StateFlow<Boolean?> = combine(config, unlocked) { current, isUnlocked ->
        current?.let { it.enabled && !isUnlocked }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun onUnlocked() {
        backgroundedAt = null
        unlocked.value = true
    }

    fun onBackground() {
        if (!authenticating && unlocked.value) backgroundedAt = elapsedRealtime()
    }

    fun onForeground() {
        val current = config.value ?: return
        if (current.enabled && appLockExpired(backgroundedAt, elapsedRealtime(), current.timeout)) {
            unlocked.value = false
        }
        backgroundedAt = null
    }
}

// Weak biometrics with the device credential is the only combination androidx.biometric supports
// on every API level from 26; the lock guards the UI, it does not unlock keys.
private const val APP_LOCK_AUTHENTICATORS = Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL

fun Context.isDeviceSecure(): Boolean =
    getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

/** Shows the system prompt. [onResult] receives null on success, otherwise a displayable error. */
fun FragmentActivity.authenticateWithDevice(
    title: String,
    onResult: (error: String?) -> Unit,
) {
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(null)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(errString.toString())
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(APP_LOCK_AUTHENTICATORS)
            .build(),
    )
}

internal fun Context.findHostActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return current as? FragmentActivity
}

@Composable
fun AppLockScreen(controller: AppLockController) {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    var error by remember { mutableStateOf<String?>(null) }
    val deviceSecure = remember { context.isDeviceSecure() }

    fun unlock() {
        val activity = context.findHostActivity() ?: return
        if (!deviceSecure) {
            // Without a screen lock there is nothing to verify; never strand the user's data.
            controller.onUnlocked()
            return
        }
        error = null
        controller.authenticating = true
        activity.authenticateWithDevice(translateUiText("Unlock Life Assistant", language)) { failure ->
            controller.authenticating = false
            if (failure == null) controller.onUnlocked() else error = failure
        }
    }

    // The prompt opens by itself; the button is there for a second try.
    LaunchedEffect(Unit) { if (deviceSecure) unlock() }

    AppLockContent(deviceSecure = deviceSecure, error = error, onUnlock = ::unlock)
}

/**
 * What a locked app shows instead of its content: the lock, one sentence, the last error, and
 * Unlock. Without a screen lock on the device there is nothing to check, so it warns and lets
 * the user continue.
 */
@Composable
internal fun AppLockContent(
    deviceSecure: Boolean,
    error: String?,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xxxl, vertical = Space.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterVertically),
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(LifeTheme.colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                text = localizedText("Life Assistant is locked"),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = localizedText(
                    if (deviceSecure) {
                        "Use your fingerprint, face or screen lock to continue."
                    } else {
                        "This device has no screen lock, so the app lock cannot verify you. Set a screen lock to protect the app."
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = if (deviceSecure) MaterialTheme.colorScheme.onSurfaceVariant else LifeTheme.colors.warning,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LifeTheme.colors.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
            Spacer(Modifier.height(Space.sm))
            Button(
                onClick = onUnlock,
                modifier = Modifier.widthIn(min = 200.dp).heightIn(min = 48.dp),
            ) {
                Text(localizedText(if (deviceSecure) "Unlock" else "Continue"))
            }
        }
    }
}
