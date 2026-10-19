package com.ced2711.lifetracker.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.BuildConfig
import com.ced2711.lifetracker.data.cloud.CloudSyncIndicator
import com.ced2711.lifetracker.data.cloud.CloudSyncIndicatorState
import com.ced2711.lifetracker.data.settings.AppSettings
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.AppIdentity
import com.ced2711.lifetracker.domain.model.AppLockTimeout
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.ReminderOffsetPreset
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.domain.model.WeekStart
import com.ced2711.lifetracker.domain.model.normalizeVisibleDestinations
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog
import com.ced2711.lifetracker.ui.adaptive.LocalTopBarSyncStatus
import com.ced2711.lifetracker.ui.adaptive.label
import com.ced2711.lifetracker.ui.components.CustomReminderOffsetInput
import com.ced2711.lifetracker.ui.components.TimePickerButton
import com.ced2711.lifetracker.ui.components.formatReminderOffset
import com.ced2711.lifetracker.ui.design.IconTile
import com.ced2711.lifetracker.ui.design.LifeTextField
import com.ced2711.lifetracker.ui.design.Pill
import com.ced2711.lifetracker.ui.design.ReadableWidth
import com.ced2711.lifetracker.ui.design.Segmented
import com.ced2711.lifetracker.ui.design.Space
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.localizedText
import com.ced2711.lifetracker.ui.localization.translateUiText
import com.ced2711.lifetracker.ui.localization.uiLocale
import com.ced2711.lifetracker.ui.lock.AppLockController
import com.ced2711.lifetracker.ui.lock.authenticateWithDevice
import com.ced2711.lifetracker.ui.lock.findHostActivity
import com.ced2711.lifetracker.ui.lock.isDeviceSecure
import com.ced2711.lifetracker.ui.theme.LifeTheme
import com.ced2711.lifetracker.ui.theme.accentOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Settings: one scrolling page of groups. This part talks to Android (notification permission,
 * device authentication, the browser) and owns the dialogs; [SettingsContent] draws the page.
 */
@Composable
fun SettingsScreen(
    viewModel: TaskLedgerViewModel,
    onOpenVault: () -> Unit,
    onOpenBackup: () -> Unit,
    appLock: AppLockController,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    onDefaultReminderOffsetsChange: (Set<Long>) -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiLanguage = LocalUiLanguage.current
    var dialog by rememberSettingsDialogState()
    val systemUses24Hour = DateFormat.is24HourFormat(context)

    fun showMessage(text: String) {
        scope.launch { snackbarHostState.showSnackbar(translateUiText(text, uiLanguage)) }
    }

    // Both turning the lock on and off need the device's own authentication, so whoever holds an
    // unlocked phone cannot quietly remove it, and nobody can lock themselves out.
    fun changeAppLock(enable: Boolean) {
        val activity = context.findHostActivity() ?: return
        if (!context.isDeviceSecure()) {
            if (enable) showMessage("Set a screen lock on this device first.") else viewModel.setAppLockEnabled(false)
            return
        }
        appLock.authenticating = true
        activity.authenticateWithDevice(
            translateUiText(if (enable) "Turn on app lock" else "Turn off app lock", uiLanguage),
        ) { error ->
            appLock.authenticating = false
            if (error != null) {
                showMessage(error)
            } else {
                if (enable) appLock.onUnlocked()
                viewModel.setAppLockEnabled(enable)
            }
        }
    }

    fun openSource() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppIdentity.SOURCE_URL)))
        } catch (_: ActivityNotFoundException) {
            showMessage("No browser is available to open the source link.")
        } catch (_: SecurityException) {
            showMessage("The source link could not be opened.")
        }
    }

    LaunchedEffect(viewModel, uiLanguage) {
        viewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(translateUiText(message, uiLanguage))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setNotificationsEnabled(granted)
        if (!granted) showMessage("Notification permission was denied. Notifications remain off.")
    }

    Box(modifier.fillMaxSize()) {
        SettingsContent(
            settings = settings,
            sync = LocalTopBarSyncStatus.current?.indicator,
            versionName = BuildConfig.VERSION_NAME,
            systemUses24Hour = systemUses24Hour,
            isWide = isWide,
            onOpenBackup = onOpenBackup,
            onTheme = viewModel::setTheme,
            onAccent = viewModel::setAccentColor,
            onLanguage = viewModel::setUiLanguage,
            onVisibleDestinations = viewModel::setVisibleDestinations,
            onWeekStart = viewModel::setWeekStart,
            onTimeFormat = viewModel::setTimeFormat,
            onDateFormat = viewModel::setDateFormat,
            onQuickAddFields = viewModel::setTodoQuickAddFields,
            onNotifications = { enabled ->
                // Android 13 and later ask the user before the first notification.
                if (!enabled) {
                    viewModel.setNotificationsEnabled(false)
                } else if (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                ) {
                    viewModel.setNotificationsEnabled(true)
                } else {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDefaultReminders = onDefaultReminderOffsetsChange,
            onEditAllDayTime = { dialog = SettingsDialog.AllDayReminderTime },
            onOpenVault = onOpenVault,
            onAppLock = ::changeAppLock,
            onAppLockTimeout = viewModel::setAppLockTimeout,
            onOpenLicense = { dialog = SettingsDialog.License },
            onOpenSource = ::openSource,
        )
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    when (dialog) {
        SettingsDialog.AllDayReminderTime -> AllDayReminderTimeDialog(
            initialMinute = settings.defaultAllDayReminderMinute,
            uses24Hour = UserFormatting.uses24HourClock(settings.timeFormat, systemUses24Hour),
            onSave = viewModel::setAllDayReminderMinute,
            onDismiss = { dialog = null },
        )

        SettingsDialog.License -> LicenseDialog(
            licenseText = remember(context) { readBundledLegalText(context, AppIdentity.LICENSE_RESOURCE) },
            permissionText = remember(context) { readBundledLegalText(context, AppIdentity.PERMISSION_RESOURCE) },
            noticeText = remember(context) { readBundledLegalText(context, AppIdentity.NOTICE_RESOURCE) },
            onDismiss = { dialog = null },
            onOpenSource = ::openSource,
        )

        null -> Unit
    }
}

/** The settings page itself, without any Android service: groups of rows, top to bottom. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsContent(
    settings: AppSettings,
    sync: CloudSyncIndicator?,
    versionName: String,
    systemUses24Hour: Boolean,
    isWide: Boolean,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    scrollState: ScrollState = rememberScrollState(),
    onOpenBackup: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onAccent: (AccentColor) -> Unit,
    onLanguage: (UiLanguage) -> Unit,
    onVisibleDestinations: (Set<TopLevelDestination>) -> Unit,
    onWeekStart: (WeekStart) -> Unit,
    onTimeFormat: (TimeFormatOption) -> Unit,
    onDateFormat: (DateFormatOption) -> Unit,
    onQuickAddFields: (Set<TodoQuickAddField>) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onDefaultReminders: (Set<Long>) -> Unit,
    onEditAllDayTime: () -> Unit,
    onOpenVault: () -> Unit,
    onAppLock: (Boolean) -> Unit,
    onAppLockTimeout: (AppLockTimeout) -> Unit,
    onOpenLicense: () -> Unit,
    onOpenSource: () -> Unit,
) {
    val language = LocalUiLanguage.current
    val locale = uiLocale(language)
    val pillSpacing = Arrangement.spacedBy(Space.sm)

    @Composable
    fun syncGroup() {
        SettingsGroup(localizedText("Sync & backup")) {
            val attention = sync?.state == CloudSyncIndicatorState.NEEDS_ATTENTION
            val state = localizedText(
                when (sync?.state) {
                    null -> "Sync is off"
                    CloudSyncIndicatorState.SYNCING -> "Syncing…"
                    CloudSyncIndicatorState.NEEDS_ATTENTION -> "Sync needs attention"
                    CloudSyncIndicatorState.PENDING -> "Changes not synced yet"
                    CloudSyncIndicatorState.UP_TO_DATE -> "Up to date"
                },
            )
            val lastSynced = localizedText("Last synced")
            val detail = when {
                sync == null -> localizedText("Daily backups are kept on this device")
                sync.lastSyncAt != null -> "$lastSynced ${formatSyncTime(sync.lastSyncAt)}"
                else -> localizedText("Never synced")
            }
            SettingRow(
                title = localizedText("Backup & sync"),
                supporting = "$state · $detail",
                supportingColor = if (attention) LifeTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                leading = {
                    IconTile(
                        icon = when (sync?.state) {
                            null -> Icons.Rounded.CloudOff
                            CloudSyncIndicatorState.SYNCING -> Icons.Rounded.Sync
                            CloudSyncIndicatorState.NEEDS_ATTENTION -> Icons.Rounded.SyncProblem
                            CloudSyncIndicatorState.PENDING -> Icons.Rounded.CloudUpload
                            CloudSyncIndicatorState.UP_TO_DATE -> Icons.Rounded.CloudDone
                        },
                        tint = when {
                            attention -> LifeTheme.colors.danger
                            sync == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                },
                onClick = onOpenBackup,
            ) { RowChevron() }
        }
    }

    @Composable
    fun appearanceGroup() {
        SettingsGroup(localizedText("Appearance")) {
            SettingRow(localizedText("Theme")) {
                Segmented(ThemeMode.entries, settings.themeMode, onTheme, { localizedText(it.label) })
            }
            SettingDivider()
            SettingRow(localizedText("Accent color")) {
                FlowRow(Modifier.selectableGroup()) {
                    AccentColor.entries.forEach { accent ->
                        AccentSwatch(accent, selected = accent == settings.accentColor, onClick = { onAccent(accent) })
                    }
                }
            }
            SettingDivider()
            SettingRow(localizedText("Language")) {
                Segmented(UiLanguage.entries, settings.uiLanguage, onLanguage, { if (it == UiLanguage.ENGLISH) "English" else "简体中文" })
            }
            SettingDivider()
            val visible = normalizeVisibleDestinations(settings.visibleDestinations).toSet()
            SettingBlock(
                title = localizedText("Modules in menu"),
                supporting = localizedText("Hidden modules keep their data. At least one stays visible."),
            ) {
                FlowRow(horizontalArrangement = pillSpacing, verticalArrangement = pillSpacing) {
                    TopLevelDestination.entries.forEach { module ->
                        val shown = module in visible
                        Pill(
                            text = localizedText(module.label),
                            selected = shown,
                            // The last visible module cannot be hidden.
                            enabled = !shown || visible.size > 1,
                            onClick = { onVisibleDestinations(if (shown) visible - module else visible + module) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun datesGroup() {
        SettingsGroup(localizedText("Dates and times")) {
            SettingRow(localizedText("Week starts on")) {
                Segmented(WeekStart.entries, settings.weekStart, onWeekStart, { localizedText(it.label) })
            }
            SettingDivider()
            SettingRow(localizedText("Time format")) {
                Segmented(TimeFormatOption.entries, settings.timeFormat, onTimeFormat, { localizedText(it.label) })
            }
            SettingDivider()
            SettingBlock(
                title = localizedText("Date format"),
                supporting = localizedText("Also the order dates are read in when you type them."),
            ) {
                val system = localizedText("System")
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = pillSpacing, verticalArrangement = pillSpacing) {
                    DateFormatOption.entries.forEach { option ->
                        Pill(
                            // An example date says more than the name of a format.
                            text = if (option == DateFormatOption.SYSTEM) system else UserFormatting.formatDate(today, option, locale),
                            selected = option == settings.dateFormat,
                            exclusive = true,
                            onClick = { onDateFormat(option) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun todoGroup() {
        SettingsGroup(localizedText("Todo")) {
            SettingBlock(
                title = localizedText("Quick add fields"),
                supporting = localizedText("Optional fields shown below the quick add description."),
            ) {
                FlowRow(horizontalArrangement = pillSpacing, verticalArrangement = pillSpacing) {
                    TodoQuickAddField.entries.forEach { field ->
                        val on = field in settings.todoQuickAddFields
                        Pill(
                            text = localizedText(field.label),
                            selected = on,
                            onClick = { onQuickAddFields(if (on) settings.todoQuickAddFields - field else settings.todoQuickAddFields + field) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun remindersGroup() {
        SettingsGroup(localizedText("Reminders")) {
            SettingSwitchRow(
                title = localizedText("Notifications"),
                supporting = localizedText("Allow reminders and due-date notifications"),
                checked = settings.notificationsEnabled,
                onCheckedChange = onNotifications,
            )
            SettingDivider()
            val offsets = settings.defaultReminderOffsetsMinutes.filterTo(sortedSetOf()) { it >= 0 }
            val presets = ReminderOffsetPreset.entries.map { it.minutesBeforeDue }
            SettingBlock(
                title = localizedText("Default reminders"),
                supporting = localizedText("Added automatically to new todos that have a date."),
            ) {
                FlowRow(horizontalArrangement = pillSpacing, verticalArrangement = pillSpacing) {
                    (presets + offsets.filterNot(presets::contains)).forEach { offset ->
                        val on = offset in offsets
                        Pill(
                            text = localizedText(formatReminderOffset(offset)),
                            selected = on,
                            onClick = { onDefaultReminders(if (on) offsets - offset else offsets + offset) },
                        )
                    }
                    CustomReminderOffsetInput(existingOffsets = offsets, onAdd = { onDefaultReminders(offsets + it) })
                }
            }
            SettingDivider()
            SettingRow(
                title = localizedText("All-day reminder time"),
                supporting = localizedText("For todos with a date but no time."),
                onClick = onEditAllDayTime,
            ) {
                Text(
                    UserFormatting.formatMinuteOfDay(settings.defaultAllDayReminderMinute, settings.timeFormat, systemUses24Hour, locale),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = Space.md, vertical = Space.sm),
                )
            }
        }
    }

    @Composable
    fun securityGroup() {
        SettingsGroup(localizedText("Security")) {
            SettingRow(
                title = localizedText("Password vault"),
                supporting = localizedText("Encrypted on this device"),
                leading = { IconTile(Icons.Rounded.Key) },
                onClick = onOpenVault,
            ) { RowChevron() }
            SettingDivider()
            SettingSwitchRow(
                title = localizedText("App lock"),
                supporting = localizedText("Ask for fingerprint, face or screen lock when opening the app"),
                checked = settings.appLockEnabled,
                onCheckedChange = onAppLock,
            )
            if (settings.appLockEnabled) {
                SettingDivider()
                SettingBlock(localizedText("Lock after leaving the app")) {
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = pillSpacing, verticalArrangement = pillSpacing) {
                        AppLockTimeout.entries.forEach { timeout ->
                            Pill(
                                text = localizedText(timeout.label),
                                selected = timeout == settings.appLockTimeout,
                                exclusive = true,
                                onClick = { onAppLockTimeout(timeout) },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun aboutGroup() {
        SettingsGroup(localizedText("About")) {
            SettingRow(
                title = localizedText(AppIdentity.NAME) + " " + versionName,
                supporting = AppIdentity.COPYRIGHT,
            )
            SettingDivider()
            SettingRow(
                title = localizedText("Private and offline"),
                supporting = localizedText("Your data stays on this device unless you turn on cloud sync, and then it is encrypted before it leaves."),
            )
            SettingDivider()
            SettingRow(
                title = localizedText("License and notices"),
                supporting = AppIdentity.LICENSE_LABEL,
                onClick = onOpenLicense,
            ) { RowChevron() }
            SettingDivider()
            SettingRow(
                title = localizedText("View source"),
                supporting = AppIdentity.SOURCE_URL.removePrefix("https://"),
                onClick = onOpenSource,
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    Box(modifier.fillMaxSize().verticalScroll(scrollState)) {
        ReadableWidth(maxWidth = if (isWide) 1120.dp else 760.dp) {
            GroupColumns(
                twoColumns = isWide,
                modifier = Modifier.padding(horizontal = if (isWide) Space.xxl else Space.lg).padding(top = Space.xs, bottom = Space.xxxl),
                first = {
                    syncGroup()
                    appearanceGroup()
                    datesGroup()
                    todoGroup()
                },
                second = {
                    remindersGroup()
                    securityGroup()
                    aboutGroup()
                },
            )
        }
    }
}

/** One accent colour to pick: a filled circle, ringed and ticked when it is the current one. */
@Composable
private fun AccentSwatch(accent: AccentColor, selected: Boolean, onClick: () -> Unit) {
    val name = localizedText(accent.label)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                .padding(4.dp)
                .clip(CircleShape)
                .background(accentOf(accent, LifeTheme.colors.dark)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** The time for reminders of todos without a time: typed, or chosen with the clock. */
@Composable
internal fun AllDayReminderTimeDialog(
    initialMinute: Int,
    uses24Hour: Boolean,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberAllDayReminderInput(initialMinute, uses24Hour)
    val parsedMinute = parseMinuteOfDay(input)
    val invalid = input.isNotBlank() && parsedMinute == null

    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("All-day reminder time")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    localizedText("Todos with a date but no time remind you at this time."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LifeTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = localizedText("9:30 AM or 21:30"),
                    isError = invalid,
                    background = MaterialTheme.colorScheme.surfaceContainerLowest,
                    trailing = {
                        TimePickerButton(
                            initialMinute = parsedMinute ?: initialMinute.coerceIn(0, 1_439),
                            is24Hour = uses24Hour,
                            onPicked = { input = formatAllDayReminderInput(it, uses24Hour) },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (invalid) {
                    Text(
                        localizedText("Enter a valid time such as 9:30 AM or 21:30."),
                        style = MaterialTheme.typography.bodySmall,
                        color = LifeTheme.colors.danger,
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localizedText("Cancel")) } },
        confirmButton = {
            TextButton(
                enabled = parsedMinute != null,
                onClick = {
                    parsedMinute?.let(onSave)
                    onDismiss()
                },
            ) { Text(localizedText("Save"), fontWeight = FontWeight.SemiBold) }
        },
    )
}

/** The licence, the additional permissions and the notices, with the link to the source. */
@Composable
internal fun LicenseDialog(
    licenseText: String,
    permissionText: String,
    noticeText: String,
    onDismiss: () -> Unit,
    onOpenSource: () -> Unit,
) {
    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedText("License and notices")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(
                    "${AppIdentity.COPYRIGHT} · v${AppIdentity.VERSION}\n${AppIdentity.LICENSE_LABEL}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(localizedText("This software is provided as-is, without warranty of any kind. Use it at your own risk."))
                LegalText(localizedText("License"), licenseText)
                LegalText(localizedText("Additional permissions"), permissionText)
                LegalText(localizedText("Notices"), noticeText)
            }
        },
        dismissButton = { TextButton(onClick = onOpenSource) { Text(localizedText("View source")) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(localizedText("Close"), fontWeight = FontWeight.SemiBold) } },
    )
}

@Composable
private fun LegalText(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun readBundledLegalText(context: Context, path: String): String =
    runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrElse {
        "${AppIdentity.NAME}: this legal document is unavailable in this build."
    }

private fun formatSyncTime(epochMillis: Long): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val pattern = if (time.toLocalDate() == LocalDate.now()) "HH:mm" else "MM-dd HH:mm"
    return time.format(DateTimeFormatter.ofPattern(pattern))
}

private fun parseMinuteOfDay(input: String): Int? {
    val value = input.trim().uppercase(Locale.US)
    if (value.isBlank()) return null
    val formats = listOf(
        DateTimeFormatter.ofPattern("h:mm a", Locale.US),
        DateTimeFormatter.ofPattern("h a", Locale.US),
        DateTimeFormatter.ofPattern("H:mm", Locale.US),
    )
    return formats.firstNotNullOfOrNull { formatter ->
        try {
            LocalTime.parse(value, formatter).let { it.hour * 60 + it.minute }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

private fun formatAllDayReminderInput(minute: Int, uses24Hour: Boolean): String {
    val safeMinute = minute.coerceIn(0, 1_439)
    return LocalTime.of(safeMinute / 60, safeMinute % 60)
        .format(DateTimeFormatter.ofPattern(if (uses24Hour) "HH:mm" else "h:mm a", Locale.US))
}

/** The dialogs of the settings page; saved, so an open dialog survives rotation. */
internal enum class SettingsDialog {
    AllDayReminderTime,
    License,
}

private val SettingsDialogSaver = Saver<SettingsDialog?, String>(
    save = { dialog -> dialog?.name },
    restore = { name -> SettingsDialog.entries.firstOrNull { it.name == name } },
)

@Composable
internal fun rememberSettingsDialogState(): MutableState<SettingsDialog?> = rememberSaveable(
    stateSaver = SettingsDialogSaver,
) {
    mutableStateOf(null)
}

/** The typed all-day reminder time; saved, so a half-typed time survives rotation. */
@Composable
internal fun rememberAllDayReminderInput(
    initialMinute: Int,
    uses24Hour: Boolean,
): MutableState<String> = rememberSaveable {
    mutableStateOf(formatAllDayReminderInput(initialMinute, uses24Hour))
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

private val AccentColor.label: String
    get() = when (this) {
        AccentColor.TEAL -> "Teal"
        AccentColor.BLUE -> "Blue"
        AccentColor.VIOLET -> "Violet"
        AccentColor.ROSE -> "Rose"
        AccentColor.ORANGE -> "Orange"
        AccentColor.GREEN -> "Green"
    }

private val WeekStart.label: String
    get() = when (this) {
        WeekStart.SYSTEM -> "System"
        WeekStart.SUNDAY -> "Sunday"
        WeekStart.MONDAY -> "Monday"
    }

private val TimeFormatOption.label: String
    get() = when (this) {
        TimeFormatOption.SYSTEM -> "System"
        TimeFormatOption.HOUR_12 -> "12-hour"
        TimeFormatOption.HOUR_24 -> "24-hour"
    }

private val TodoQuickAddField.label: String
    get() = when (this) {
        TodoQuickAddField.DEADLINE -> "Deadline"
        TodoQuickAddField.PRIORITY -> "Priority"
        TodoQuickAddField.CATEGORY -> "Category"
        TodoQuickAddField.TAGS -> "Tags"
    }

private val AppLockTimeout.label: String
    get() = when (this) {
        AppLockTimeout.IMMEDIATELY -> "Immediately"
        AppLockTimeout.ONE_MINUTE -> "1 minute"
        AppLockTimeout.FIVE_MINUTES -> "5 minutes"
    }
