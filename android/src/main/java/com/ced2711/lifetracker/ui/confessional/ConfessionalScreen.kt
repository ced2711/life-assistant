package com.ced2711.lifetracker.ui.confessional

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.data.confession.ConfessionStore
import com.ced2711.lifetracker.domain.model.ConfessionEntry
import com.ced2711.lifetracker.domain.model.MAX_CONFESSION_LENGTH
import com.ced2711.lifetracker.ui.components.ConfirmDialog
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.Panel
import com.ced2711.lifetracker.ui.design.ReadableWidth
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.lock.authenticateWithDevice
import com.ced2711.lifetracker.ui.lock.findHostActivity
import com.ced2711.lifetracker.ui.lock.isDeviceSecure
import com.ced2711.lifetracker.ui.theme.LifeTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// The one fixed colour in the app: fire looks the same in every theme and accent.
private val EmberColor = Color(0xFFFF7043)
private const val BURN_MILLIS = 1_400

/**
 * A place to say something and let it go. The text is kept only in memory (never in saved
 * state), so it is gone once burned or when the screen closes. Sealing keeps it encrypted on
 * this device only, readable after the device's own authentication.
 */
@Composable
fun ConfessionalScreen(
    store: ConfessionStore,
    modifier: Modifier = Modifier,
    isWide: Boolean,
) {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    val scope = rememberCoroutineScope()
    val sealed by store.entries.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var confirmBurnAll by remember { mutableStateOf(false) }
    var confirmBurnId by remember { mutableStateOf<String?>(null) }
    val burn = remember { Animatable(0f) }

    LaunchedEffect(store) {
        runCatching { store.load() }.onFailure { message = it.message }
    }

    fun launchSafely(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                message = translateUiText(error.message ?: "Something went wrong", language)
            }
        }
    }

    fun reveal() {
        val activity = context.findHostActivity() ?: return
        if (!context.isDeviceSecure()) {
            revealed = true
            return
        }
        activity.authenticateWithDevice(translateUiText("Open sealed confessions", language)) { error ->
            if (error == null) revealed = true else message = error
        }
    }

    ConfessionalContent(
        text = text,
        onTextChange = { if (it.length <= MAX_CONFESSION_LENGTH) text = it },
        burnProgress = burn.value,
        burning = burn.isRunning,
        message = message,
        sealed = sealed.orEmpty(),
        revealed = revealed,
        isWide = isWide,
        onBurn = {
            launchSafely {
                burn.snapTo(0f)
                burn.animateTo(1f, tween(durationMillis = BURN_MILLIS, easing = FastOutSlowInEasing))
                text = ""
                burn.snapTo(0f)
                message = translateUiText("Burned. It's gone.", language)
            }
        },
        onSeal = {
            launchSafely {
                store.seal(text)
                text = ""
                message = translateUiText("Sealed on this device.", language)
            }
        },
        onOpen = ::reveal,
        onHide = { revealed = false },
        onBurnAll = { confirmBurnAll = true },
        onBurnOne = { confirmBurnId = it },
        modifier = modifier,
    )

    confirmBurnId?.let { id ->
        ConfirmDialog(
            title = localizedText("Burn this confession?"),
            text = localizedText("It will be permanently deleted from this device."),
            confirmLabel = localizedText("Burn"),
            destructive = true,
            onDismiss = { confirmBurnId = null },
            onConfirm = {
                confirmBurnId = null
                launchSafely { store.burn(id) }
            },
        )
    }
    if (confirmBurnAll) {
        ConfirmDialog(
            title = localizedText("Burn every sealed confession?"),
            text = localizedText("They will be permanently deleted from this device."),
            confirmLabel = localizedText("Burn all"),
            destructive = true,
            onDismiss = { confirmBurnAll = false },
            onConfirm = {
                confirmBurnAll = false
                revealed = false
                launchSafely { store.burnAll() }
            },
        )
    }
}

/**
 * The page itself. [burnProgress] runs from 0 to 1 while the text burns: it fades, shrinks and
 * turns ember orange while a flame flares over it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConfessionalContent(
    text: String,
    onTextChange: (String) -> Unit,
    burnProgress: Float,
    burning: Boolean,
    message: String?,
    sealed: List<ConfessionEntry>,
    revealed: Boolean,
    isWide: Boolean,
    onBurn: () -> Unit,
    onSeal: () -> Unit,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onBurnAll: () -> Unit,
    onBurnOne: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = uiLocale(LocalUiLanguage.current)
    ReadableWidth(modifier.verticalScroll(rememberScrollState()), maxWidth = 720.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = if (isWide) Space.xxl else Space.lg).padding(top = Space.xs, bottom = Space.xxxl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Text(
                localizedText("Say what you need to say. Burn it to let it go for good, or seal it on this device. Nothing here is backed up or synced, and screenshots are blocked."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(contentAlignment = Alignment.Center) {
                LifeTextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = !burning,
                    minLines = 8,
                    placeholder = localizedText("Write it down…"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = lerp(MaterialTheme.colorScheme.onSurface, EmberColor, burnProgress)),
                    background = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().graphicsLayer {
                        alpha = 1f - burnProgress
                        scaleY = 1f - burnProgress * 0.35f
                        translationY = -burnProgress * 80f
                    },
                )
                if (burning) {
                    Icon(
                        Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = EmberColor,
                        modifier = Modifier.size(72.dp).graphicsLayer {
                            val flare = if (burnProgress < 0.6f) burnProgress / 0.6f else (1f - burnProgress) / 0.4f
                            alpha = flare.coerceIn(0f, 1f)
                            scaleX = 0.6f + burnProgress
                            scaleY = 0.6f + burnProgress
                        },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Button(
                    onClick = onBurn,
                    enabled = text.isNotBlank() && !burning,
                    colors = ButtonDefaults.buttonColors(containerColor = EmberColor, contentColor = Color.Black),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(localizedText("Burn"), modifier = Modifier.padding(start = Space.sm))
                }
                OutlinedButton(onClick = onSeal, enabled = text.isNotBlank() && !burning, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(localizedText("Seal"), modifier = Modifier.padding(start = Space.sm))
                }
            }
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            if (sealed.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            localizedText("Sealed confessions") + " (${sealed.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically).semantics { heading() },
                        )
                        Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                            if (revealed) {
                                TextButton(onClick = onHide) { Text(localizedText("Hide")) }
                                TextButton(onClick = onBurnAll) { Text(localizedText("Burn all"), color = LifeTheme.colors.danger) }
                            } else {
                                TextButton(onClick = onOpen) { Text(localizedText("Open")) }
                            }
                        }
                    }
                    if (revealed) {
                        val dateFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
                        sealed.forEach { entry ->
                            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(start = Space.lg, end = Space.xs, top = Space.xs, bottom = Space.lg)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        dateFormat.format(Date(entry.createdAt)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { onBurnOne(entry.id) }) {
                                        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = localizedText("Burn"), tint = EmberColor)
                                    }
                                }
                                Text(entry.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = Space.md))
                            }
                        }
                    }
                }
            }
        }
    }
}
