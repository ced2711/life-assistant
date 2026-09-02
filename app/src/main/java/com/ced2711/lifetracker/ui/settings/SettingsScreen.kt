package com.ced2711.lifetracker.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ced2711.lifetracker.BuildConfig
import com.ced2711.lifetracker.domain.format.UserFormatting
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.ReminderOffsetPreset
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.WeekStart
import com.ced2711.lifetracker.ui.TaskLedgerViewModel
import com.ced2711.lifetracker.ui.adaptive.HingeSafeAlertDialog
import com.ced2711.lifetracker.ui.adaptive.rememberHingeSafePlatformDialogLauncher
import com.ced2711.lifetracker.ui.components.CustomReminderOffsetInput
import com.ced2711.lifetracker.ui.components.formatReminderOffset
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: TaskLedgerViewModel,
    onOpenVault: () -> Unit,
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    onDefaultReminderOffsetsChange: (Set<Long>) -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val platformDialogLauncher = rememberHingeSafePlatformDialogLauncher()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var choiceDialog by rememberSettingsDialogState()

    LaunchedEffect(viewModel) {
        viewModel.errors.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setNotificationsEnabled(granted)
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Notification permission was denied. Notifications remain off.",
                )
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = if (isWide) 32.dp else 16.dp,
                        end = if (isWide) 32.dp else 16.dp,
                        bottom = 32.dp,
                    ),
            ) {
                SettingsSectionTitle("Security")
                SettingsValueRow(
                    title = "Password vault",
                    value = "Encrypted on this device",
                    onClick = onOpenVault,
                )

                SettingsSectionTitle("Data")
                SettingsValueRow(
                    title = "Encrypted backup",
                    value = "Local .tlb file",
                    onClick = onOpenBackup,
                )

                SettingsSectionTitle("Appearance")
                SettingsValueRow(
                    title = "Theme",
                    value = settings.themeMode.label,
                    onClick = { choiceDialog = SettingsDialog.Theme },
                )
                HorizontalDivider()
                SettingsValueRow(
                    title = "Accent color",
                    value = settings.accentColor.label,
                    onClick = { choiceDialog = SettingsDialog.AccentColor },
                )

                SettingsSectionTitle("Regional preferences")
                SettingsValueRow(
                    title = "Week starts on",
                    value = settings.weekStart.label,
                    onClick = { choiceDialog = SettingsDialog.WeekStart },
                )
                HorizontalDivider()
                SettingsValueRow(
                    title = "Time format",
                    value = settings.timeFormat.label,
                    onClick = { choiceDialog = SettingsDialog.TimeFormat },
                )
                HorizontalDivider()
                SettingsValueRow(
                    title = "Date format",
                    value = settings.dateFormat.label,
                    onClick = { choiceDialog = SettingsDialog.DateFormat },
                )

                SettingsSectionTitle("Todo")
                SettingsValueRow(
                    title = "Quick add fields",
                    value = quickAddFieldsSummary(settings.todoQuickAddFields),
                    onClick = { choiceDialog = SettingsDialog.TodoQuickAddFields },
                )

                SettingsSectionTitle("Notifications")
                SettingsSwitchRow(
                    title = "Notifications",
                    supportingText = "Allow reminders and due-date notifications",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            viewModel.setNotificationsEnabled(false)
                        } else if (
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.setNotificationsEnabled(true)
                        } else {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
                HorizontalDivider()
                SettingsValueRow(
                    title = "Default reminders",
                    value = defaultReminderSummary(settings.defaultReminderOffsetsMinutes),
                    onClick = { choiceDialog = SettingsDialog.DefaultReminders },
                )
                HorizontalDivider()
                SettingsValueRow(
                    title = "All-day reminder time",
                    value = UserFormatting.formatMinuteOfDay(
                        minuteOfDay = settings.defaultAllDayReminderMinute,
                        option = settings.timeFormat,
                        systemUses24Hour = DateFormat.is24HourFormat(context),
                        locale = Locale.US,
                    ),
                    onClick = { choiceDialog = SettingsDialog.AllDayReminderTime },
                )

                SettingsSectionTitle("About")
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Life Tracker by ced2711", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} • Private and offline",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    when (choiceDialog) {
        SettingsDialog.Theme -> ChoiceDialog(
            title = "Theme",
            choices = ThemeMode.entries.map { Choice(it.label, it) },
            selected = settings.themeMode,
            onSelect = viewModel::setTheme,
            onDismiss = { choiceDialog = null },
        )

        SettingsDialog.AccentColor -> AccentColorDialog(
            selected = settings.accentColor,
            onSelect = viewModel::setAccentColor,
            onDismiss = { choiceDialog = null },
        )

        SettingsDialog.WeekStart -> ChoiceDialog(
            title = "Week starts on",
            choices = WeekStart.entries.map { Choice(it.label, it) },
            selected = settings.weekStart,
            onSelect = viewModel::setWeekStart,
            onDismiss = { choiceDialog = null },
        )

        SettingsDialog.TimeFormat -> ChoiceDialog(
            title = "Time format",
            choices = TimeFormatOption.entries.map { Choice(it.label, it) },
            selected = settings.timeFormat,
            onSelect = viewModel::setTimeFormat,
            onDismiss = { choiceDialog = null },
        )

        SettingsDialog.DateFormat -> ChoiceDialog(
            title = "Date format",
            choices = DateFormatOption.entries.map { Choice(it.label, it) },
            selected = settings.dateFormat,
            onSelect = viewModel::setDateFormat,
            onDismiss = { choiceDialog = null },
        )

        SettingsDialog.DefaultReminders -> DefaultRemindersDialog(
            selected = settings.defaultReminderOffsetsMinutes,
            onSave = onDefaultReminderOffsetsChange,
            onDismiss = { choiceDialog = null },
        )

        SettingsDialog.TodoQuickAddFields -> TodoQuickAddFieldsDialog(
            selected = settings.todoQuickAddFields,
            onSave = viewModel::setTodoQuickAddFields,
            onDismiss = { choiceDialog = null },
        )

        SettingsDialog.AllDayReminderTime -> {
            val uses24Hour = when (settings.timeFormat) {
                TimeFormatOption.SYSTEM -> DateFormat.is24HourFormat(context)
                TimeFormatOption.HOUR_12 -> false
                TimeFormatOption.HOUR_24 -> true
            }
            AllDayReminderTimeDialog(
                initialMinute = settings.defaultAllDayReminderMinute,
                uses24Hour = uses24Hour,
                onSave = viewModel::setAllDayReminderMinute,
                onOpenSystemPicker = {
                    val currentMinute = settings.defaultAllDayReminderMinute
                    platformDialogLauncher(
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                viewModel.setAllDayReminderMinute(hour * 60 + minute)
                                choiceDialog = null
                            },
                            currentMinute / 60,
                            currentMinute % 60,
                            uses24Hour,
                        ),
                    )
                },
                onDismiss = { choiceDialog = null },
            )
        }

        null -> Unit
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supportingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
            ) {
                choices.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = choice.value == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(choice.value)
                                    onDismiss()
                                },
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = choice.value == selected,
                            onClick = null,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AccentColorDialog(
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
    onDismiss: () -> Unit,
) {
    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accent color") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
            ) {
                AccentColor.entries.forEach { accent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = accent == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(accent)
                                    onDismiss()
                                },
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = accent == selected, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accent.previewColor)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(accent.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DefaultRemindersDialog(
    selected: Set<Long>,
    onSave: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberReminderOffsetsDraft(selected)

    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default reminders") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Automatically add these to new todos that have a deadline.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ReminderOffsetPreset.entries.forEach { preset ->
                    val offset = preset.minutesBeforeDue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = offset in draft,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    draft = if (checked) draft + offset else draft - offset
                                },
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = offset in draft,
                            onCheckedChange = null,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(preset.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                val presetOffsets = ReminderOffsetPreset.entries
                    .mapTo(mutableSetOf(), ReminderOffsetPreset::minutesBeforeDue)
                draft
                    .filterNot(presetOffsets::contains)
                    .sorted()
                    .forEach { offset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = true,
                                    role = Role.Checkbox,
                                    onValueChange = { checked ->
                                        if (!checked) draft = draft - offset
                                    },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = true, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(formatReminderOffset(offset), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                CustomReminderOffsetInput(
                    existingOffsets = draft,
                    onAdd = { offset -> draft = draft + offset },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(draft)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TodoQuickAddFieldsDialog(
    selected: Set<TodoQuickAddField>,
    onSave: (Set<TodoQuickAddField>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(selected, stateSaver = TodoQuickAddFieldSetSaver) {
        mutableStateOf(selected)
    }

    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick add fields") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Choose optional fields shown below the quick add description.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TodoQuickAddField.entries.forEach { field ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = field in draft,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    draft = if (checked) draft + field else draft - field
                                },
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = field in draft, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(field.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(draft)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AllDayReminderTimeDialog(
    initialMinute: Int,
    uses24Hour: Boolean,
    onSave: (Int) -> Unit,
    onOpenSystemPicker: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberAllDayReminderInput(initialMinute, uses24Hour)
    val parsedMinute = parseMinuteOfDay(input)

    HingeSafeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("All-day reminder time") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Time") },
                placeholder = { Text("9:30 AM or 21:30") },
                supportingText = {
                    Text(
                        if (parsedMinute == null) {
                            "Enter a valid time such as 9:30 AM or 21:30."
                        } else {
                            "The system picker is also available below."
                        },
                    )
                },
                isError = input.isNotBlank() && parsedMinute == null,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = parsedMinute != null,
                onClick = {
                    parsedMinute?.let(onSave)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onOpenSystemPicker) { Text("System picker") }
            }
        },
    )
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

private data class Choice<T>(val label: String, val value: T)

internal enum class SettingsDialog {
    Theme,
    AccentColor,
    WeekStart,
    TimeFormat,
    DateFormat,
    TodoQuickAddFields,
    DefaultReminders,
    AllDayReminderTime,
}

private val SettingsDialogSaver = Saver<SettingsDialog?, String>(
    save = { dialog -> dialog?.name },
    restore = { name -> SettingsDialog.entries.firstOrNull { it.name == name } },
)

private val ReminderOffsetSetSaver = Saver<Set<Long>, LongArray>(
    save = { offsets -> offsets.sorted().toLongArray() },
    restore = { offsets -> offsets.toSet() },
)

private val TodoQuickAddFieldSetSaver = Saver<Set<TodoQuickAddField>, ArrayList<String>>(
    save = { fields -> ArrayList(fields.map { it.name }) },
    restore = { saved ->
        saved.mapNotNullTo(mutableSetOf()) { name ->
            TodoQuickAddField.entries.firstOrNull { it.name == name }
        }
    },
)

@Composable
internal fun rememberSettingsDialogState(): MutableState<SettingsDialog?> = rememberSaveable(
    stateSaver = SettingsDialogSaver,
) {
    mutableStateOf(null)
}

@Composable
internal fun rememberReminderOffsetsDraft(selected: Set<Long>): MutableState<Set<Long>> =
    rememberSaveable(stateSaver = ReminderOffsetSetSaver) {
        mutableStateOf(selected.filter { it >= 0 }.toSet())
    }

@Composable
internal fun rememberAllDayReminderInput(
    initialMinute: Int,
    uses24Hour: Boolean,
): MutableState<String> = rememberSaveable {
    val safeMinute = initialMinute.coerceIn(0, 1_439)
    mutableStateOf(
        LocalTime.of(safeMinute / 60, safeMinute % 60).format(
            DateTimeFormatter.ofPattern(if (uses24Hour) "HH:mm" else "h:mm a", Locale.US),
        ),
    )
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System default"
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

private val AccentColor.previewColor: Color
    get() = when (this) {
        AccentColor.TEAL -> Color(0xFF00A896)
        AccentColor.BLUE -> Color(0xFF4D8ED1)
        AccentColor.VIOLET -> Color(0xFF8367C7)
        AccentColor.ROSE -> Color(0xFFC8587E)
        AccentColor.ORANGE -> Color(0xFFD77A11)
        AccentColor.GREEN -> Color(0xFF3A9B58)
    }

private val WeekStart.label: String
    get() = when (this) {
        WeekStart.SYSTEM -> "System default"
        WeekStart.SUNDAY -> "Sunday"
        WeekStart.MONDAY -> "Monday"
    }

private val TimeFormatOption.label: String
    get() = when (this) {
        TimeFormatOption.SYSTEM -> "System default"
        TimeFormatOption.HOUR_12 -> "12-hour"
        TimeFormatOption.HOUR_24 -> "24-hour"
    }

private val DateFormatOption.label: String
    get() = when (this) {
        DateFormatOption.SYSTEM -> "System default"
        DateFormatOption.MONTH_DAY_YEAR -> "MM/DD/YYYY"
        DateFormatOption.DAY_MONTH_YEAR -> "DD/MM/YYYY"
        DateFormatOption.YEAR_MONTH_DAY -> "YYYY-MM-DD"
    }

private val ReminderOffsetPreset.label: String
    get() = when (this) {
        ReminderOffsetPreset.AT_DUE -> "At due time"
        ReminderOffsetPreset.ONE_HOUR -> "1 hour before"
        ReminderOffsetPreset.ONE_DAY -> "1 day before"
        ReminderOffsetPreset.THREE_DAYS -> "3 days before"
        ReminderOffsetPreset.ONE_WEEK -> "1 week before"
    }

private val TodoQuickAddField.label: String
    get() = when (this) {
        TodoQuickAddField.DEADLINE -> "Deadline"
        TodoQuickAddField.PRIORITY -> "Priority"
        TodoQuickAddField.CATEGORY -> "Category"
        TodoQuickAddField.TAGS -> "Tags"
    }

private fun quickAddFieldsSummary(fields: Set<TodoQuickAddField>): String = when (fields.size) {
    0 -> "Description only"
    TodoQuickAddField.entries.size -> "All optional fields"
    else -> TodoQuickAddField.entries.filter(fields::contains).joinToString { it.label }
}

private fun defaultReminderSummary(offsets: Set<Long>): String = when (offsets.size) {
    0 -> "None"
    1 -> ReminderOffsetPreset.entries
        .firstOrNull { it.minutesBeforeDue == offsets.first() }
        ?.label
        ?: formatReminderOffset(offsets.first())
    else -> "${offsets.size} reminders"
}
