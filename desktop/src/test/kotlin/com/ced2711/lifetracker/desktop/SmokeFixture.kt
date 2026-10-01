package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.TodoPriority
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    require(args.size == 1) { "Pass one isolated APPDATA directory." }
    val appData = File(args.single()).absoluteFile
    val appDirectory = File(appData, "Life Tracker")
    require(!File(appDirectory, "life-tracker-local.tlb").exists()) {
        "Refusing to replace an existing smoke fixture. Pass an empty directory."
    }
    val password = smokePassword()
    val sourceAttachment = File(appData, "fixture-attachment.txt")
    try {
        appData.mkdirs()
        sourceAttachment.writeText("Synthetic Life Tracker desktop smoke-test attachment.")
        val store = DesktopDataStore(appDirectory)
        check(store.open(password.copyOf()))

        check(store.addCategory("Personal"))
        val personal = store.currentSnapshot()!!.categories.single().id
        check(store.addCategory("Health", personal))
        val health = store.currentSnapshot()!!.categories.first { it.name == "Health" }.id
        val today = LocalDate.now().toEpochDay()
        check(store.upsertTodo(null, "Morning walk", "Walk for thirty minutes", today, TodoPriority.MEDIUM, health, "health,daily", false))
        check(store.upsertTodo(null, "Plan the week", "Review the calendar and choose three priorities", today + 1, TodoPriority.HIGH, personal, "planning", false))
        check(store.upsertTodo(null, "Read a chapter", "Finish the current book chapter", today - 1, TodoPriority.LOW, personal, "reading", true))

        check(store.upsertLedger(null, LedgerType.INCOME, 325_000, today - 3, "Monthly pay", "Employer", "income"))
        check(store.upsertLedger(null, LedgerType.EXPENSE, 2_485, today, "Groceries", "Neighborhood Market", "food"))
        check(store.upsertLedger(null, LedgerType.EXPENSE, 1_250, today - 1, "Lunch", "Cafe", "food"))

        // Recurring items, subtasks and diary pages so every page has something to show.
        checkNotNull(store.saveLedger(
            com.ced2711.lifetracker.domain.model.LedgerDraft(type = LedgerType.EXPENSE, amountCents = 145_000, epochDay = today - 62, minuteOfDay = 9 * 60,
                merchant = "Rent", tags = listOf("home"), recurrence = com.ced2711.lifetracker.domain.model.RecurrenceRule(com.ced2711.lifetracker.domain.model.RecurrenceUnit.MONTH)),
            com.ced2711.lifetracker.domain.model.SeriesEditScope.ONLY_THIS_OCCURRENCE,
        ))
        listOf(4 to 3_250L, 6 to 1_890L, 9 to 6_420L, 12 to 899L).forEach { (daysAgo, cents) ->
            check(store.upsertLedger(null, LedgerType.EXPENSE, cents, today - daysAgo, "Weekly shop", "Supermarket", "food"))
        }
        checkNotNull(store.saveTodo(
            com.ced2711.lifetracker.domain.model.TodoDraft(description = "Water the plants", deadlineEpochDay = today, deadlineMinute = 18 * 60,
                priority = TodoPriority.LOW, subtasks = listOf("Balcony", "Kitchen herbs"), reminderOffsetsMinutes = listOf(0),
                recurrence = com.ced2711.lifetracker.domain.model.RecurrenceRule(com.ced2711.lifetracker.domain.model.RecurrenceUnit.DAY, 3)),
            com.ced2711.lifetracker.domain.model.SeriesEditScope.ONLY_THIS_OCCURRENCE,
        ))
        checkNotNull(store.saveTodo(
            com.ced2711.lifetracker.domain.model.TodoDraft(description = "Renew passport", deadlineEpochDay = today - 2, priority = TodoPriority.URGENT,
                subtasks = listOf("Photos", "Fill in form", "Book appointment")),
            com.ced2711.lifetracker.domain.model.SeriesEditScope.ONLY_THIS_OCCURRENCE,
        ))
        check(store.upsertDiary(today - 1, "Long walk by the river. Finished the book."))
        check(store.upsertDiary(today - 4, "Busy day at work, cooked pasta in the evening."))

        check(store.addNoteFolder("Projects"))
        val projects = store.currentSnapshot()!!.noteFolders.single().id
        check(store.addNoteFolder("Life Tracker", projects))
        val projectFolder = store.currentSnapshot()!!.noteFolders.first { it.name == "Life Tracker" }.id
        check(
            store.upsertNote(
                null,
                projectFolder,
                "Desktop release checklist",
                "Verify the Windows installer, encrypted backup round trip, calendar details, and Google Drive conflict handling.",
                true,
            ),
        )
        val noteId = store.currentSnapshot()!!.notes.single().id
        check(store.attachFile(AttachmentOwnerType.NOTE, noteId, sourceAttachment))
        check(store.upsertVault(null, "Example account", "demo@example.invalid", "sample-only", "https://example.invalid", "Synthetic smoke fixture"))

        DesktopCredentialStore(File(appDirectory, "credentials")).save(
            DesktopCredentialStore.LOCAL_PASSWORD,
            password.copyOf(),
        )
        store.close()
    } finally {
        password.fill('\u0000')
        sourceAttachment.delete()
    }
    println(appData.absolutePath)
}

private fun smokePassword(): CharArray = charArrayOf(
    's', 'm', 'o', 'k', 'e', '-', 'f', 'i', 'x', 't', 'u', 'r', 'e', '-', '2', '6', '!',
)
