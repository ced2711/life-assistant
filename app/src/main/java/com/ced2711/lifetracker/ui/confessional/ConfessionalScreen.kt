package com.ced2711.lifetracker.ui.confessional

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.data.confession.ConfessionStore
import com.ced2711.lifetracker.domain.model.MAX_CONFESSION_LENGTH
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.lock.authenticateWithDevice
import com.ced2711.lifetracker.ui.lock.findHostActivity
import com.ced2711.lifetracker.ui.lock.isDeviceSecure
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val EmberColor = Color(0xFFFF7043)

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
    val burn = remember { Animatable(0f) }
    val burning = burn.isRunning

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

    fun burnText() = launchSafely {
        burn.snapTo(0f)
        burn.animateTo(1f, tween(durationMillis = 1_400, easing = FastOutSlowInEasing))
        text = ""
        burn.snapTo(0f)
        message = translateUiText("Burned. It's gone.", language)
    }

    fun sealText() = launchSafely {
        store.seal(text)
        text = ""
        message = translateUiText("Sealed on this device.", language)
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

    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .then(if (isWide) Modifier.widthIn(max = 720.dp) else Modifier)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(localizedText("Confessional"), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = localizedText(
                    "Say what you need to say. Burn it to let it go for good, or seal it on this device. " +
                        "Nothing here is backed up or synced, and screenshots are blocked.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(contentAlignment = Alignment.Center) {
                val progress = burn.value
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_CONFESSION_LENGTH) text = it },
                    enabled = !burning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                        .graphicsLayer {
                            alpha = 1f - progress
                            scaleY = 1f - progress * 0.35f
                            translationY = -progress * 80f
                        },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = lerp(MaterialTheme.colorScheme.onSurface, EmberColor, progress),
                    ),
                    placeholder = { Text(localizedText("Write it down…")) },
                )
                if (burning) {
                    Icon(
                        imageVector = Icons.Outlined.LocalFireDepartment,
                        contentDescription = null,
                        tint = EmberColor,
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                val flare = if (progress < 0.6f) progress / 0.6f else (1f - progress) / 0.4f
                                alpha = flare.coerceIn(0f, 1f)
                                scaleX = 0.6f + progress
                                scaleY = 0.6f + progress
                            },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = ::burnText,
                    enabled = text.isNotBlank() && !burning,
                    colors = ButtonDefaults.buttonColors(containerColor = EmberColor, contentColor = Color.Black),
                ) {
                    Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null)
                    Text(localizedText("Burn"), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = ::sealText, enabled = text.isNotBlank() && !burning) {
                    Icon(Icons.Outlined.Lock, contentDescription = null)
                    Text(localizedText("Seal"), modifier = Modifier.padding(start = 8.dp))
                }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            val sealedEntries = sealed.orEmpty()
            if (sealedEntries.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = localizedText("Sealed confessions") + " (${sealedEntries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (revealed) {
                        TextButton(onClick = { revealed = false }) { Text(localizedText("Hide")) }
                        TextButton(onClick = { confirmBurnAll = true }) { Text(localizedText("Burn all")) }
                    } else {
                        TextButton(onClick = ::reveal) { Text(localizedText("Open")) }
                    }
                }
                if (revealed) {
                    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                    sealedEntries.forEach { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dateFormat.format(Date(entry.createdAt)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { launchSafely { store.burn(entry.id) } }) {
                                        Icon(
                                            Icons.Outlined.LocalFireDepartment,
                                            contentDescription = localizedText("Burn"),
                                            tint = EmberColor,
                                        )
                                    }
                                }
                                Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmBurnAll) {
        AlertDialog(
            onDismissRequest = { confirmBurnAll = false },
            title = { Text(localizedText("Burn every sealed confession?")) },
            text = { Text(localizedText("They will be permanently deleted from this device.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmBurnAll = false
                    revealed = false
                    launchSafely { store.burnAll() }
                }) { Text(localizedText("Burn all")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBurnAll = false }) { Text(localizedText("Cancel")) }
            },
        )
    }
}
