package com.ced2711.lifetracker.data.backup

import com.ced2711.lifetracker.data.local.CategoryEntity
import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.LedgerOccurrenceExceptionEntity
import com.ced2711.lifetracker.data.local.LedgerSeriesEntity
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.local.TodoOccurrenceExceptionEntity
import com.ced2711.lifetracker.data.local.TodoReminderEntity
import com.ced2711.lifetracker.data.local.TodoSeriesEntity
import com.ced2711.lifetracker.data.local.TodoSeriesSubtaskEntity
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.AccentColor
import com.ced2711.lifetracker.domain.model.DateFormatOption
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.ThemeMode
import com.ced2711.lifetracker.domain.model.TimeFormatOption
import com.ced2711.lifetracker.domain.model.TodoPriority
import com.ced2711.lifetracker.domain.model.TodoQuickAddField
import com.ced2711.lifetracker.domain.model.TopLevelDestination
import com.ced2711.lifetracker.domain.model.VaultEntry
import com.ced2711.lifetracker.domain.model.WeekStart

internal fun fullBackupSnapshot(): BackupSnapshot {
    val attachmentBytes = fullBackupAttachmentBytes()
    return BackupSnapshot(
        createdAt = 9_000,
        settings = BackupSettings(
            themeMode = ThemeMode.DARK,
            accentColor = AccentColor.VIOLET,
            weekStart = WeekStart.SUNDAY,
            timeFormat = TimeFormatOption.HOUR_12,
            dateFormat = DateFormatOption.MONTH_DAY_YEAR,
            notificationsEnabled = false,
            defaultAllDayReminderMinute = 0,
            defaultReminderOffsetsMinutes = linkedSetOf(0, 60, 1_440),
            todoQuickAddFields = linkedSetOf(TodoQuickAddField.DEADLINE, TodoQuickAddField.TAGS),
            lastDestination = TopLevelDestination.CALENDAR,
        ),
        categories = listOf(CategoryEntity(1, "生活 / Life", null, 2, 100)),
        todoSeries = listOf(
            TodoSeriesEntity(
                id = 10, title = "学习\uD83D\uDCDA", description = "说明\n第二行", categoryId = 1,
                startEpochDay = 20_000, startMinute = null, recurrenceUnit = RecurrenceUnit.WEEK,
                intervalCount = 2, endEpochDay = 20_100, priority = TodoPriority.HIGH,
                tagsCsv = "学校,中文", reminderOffsetsCsv = "0,60", active = true,
                createdAt = 101, updatedAt = 102,
            ),
        ),
        todoSeriesSubtasks = listOf(TodoSeriesSubtaskEntity(10, 0, "章节一")),
        todoOccurrenceExceptions = listOf(TodoOccurrenceExceptionEntity(10, 20_014, 103)),
        todos = listOf(
            TodoEntity(
                id = 20, seriesId = 10, occurrenceEpochDay = 20_000, title = "学习\uD83D\uDCDA",
                description = "任意 Unicode ✓", categoryId = 1, deadlineEpochDay = 20_000,
                deadlineMinute = 720, priority = TodoPriority.HIGH, tagsCsv = "学校,中文",
                completedAt = null, createdAt = 104, updatedAt = 105, customOrder = 106,
                deletedAt = null, clientOperationToken = "todo-token",
            ),
        ),
        subtasks = listOf(SubtaskEntity(30, 20, "复习", false, 0)),
        todoReminders = listOf(TodoReminderEntity(40, 20, 60)),
        ledgerSeries = listOf(
            LedgerSeriesEntity(
                id = 50, type = LedgerType.EXPENSE, amountCents = 12_34, startEpochDay = 20_000,
                recurrenceUnit = RecurrenceUnit.MONTH, intervalCount = 1, endEpochDay = null,
                note = "订阅", merchant = "商家", tagsCsv = "固定", active = true,
                createdAt = 107, updatedAt = 108, clientOperationToken = "series-token",
            ),
        ),
        ledgerOccurrenceExceptions = listOf(LedgerOccurrenceExceptionEntity(50, 20_031, 109)),
        ledgerEntries = listOf(
            LedgerEntryEntity(
                id = 60, seriesId = 50, occurrenceEpochDay = 20_000, type = LedgerType.EXPENSE,
                amountCents = 12_34, epochDay = 20_000, minuteOfDay = 1_439,
                note = "午餐", merchant = "店铺", tagsCsv = "食物", createdAt = 110,
                updatedAt = 111, deletedAt = null, clientOperationToken = "ledger-token",
            ),
        ),
        attachments = listOf(
            BackupAttachment(
                id = 70, ownerType = AttachmentOwnerType.TODO, ownerId = 20,
                archivePath = attachmentArchivePath(70, sha256(attachmentBytes)),
                originalName = "收据.png", mimeType = "image/png", sizeBytes = attachmentBytes.size.toLong(),
                sha256 = sha256(attachmentBytes), createdAt = 112, pendingDeleteAt = null,
            ),
        ),
        vaultEntries = listOf(
            VaultEntry(
                id = "123e4567-e89b-12d3-a456-426614174000", label = "邮箱",
                account = "用户@example.com", password = "密碼\uD83D\uDD11", website = "https://例子.test",
                notes = "仅供测试", createdAt = 113, updatedAt = 114,
            ),
        ),
    )
}

internal fun fullBackupAttachmentBytes(): ByteArray = "收据\uD83E\uDDFE".encodeToByteArray()

internal fun fullBackupAttachmentSource(bytes: ByteArray = fullBackupAttachmentBytes()) =
    BackupAttachmentSource { java.io.ByteArrayInputStream(bytes) }
