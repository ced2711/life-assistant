package com.ced2711.lifetracker.domain.model

import com.ced2711.lifetracker.data.local.LedgerEntryEntity
import com.ced2711.lifetracker.data.local.TodoEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Money in and out over some days, in cents. */
data class MoneyTotals(val incomeCents: Long, val expenseCents: Long) {
    val netCents: Long get() = incomeCents - expenseCents

    companion object {
        val ZERO = MoneyTotals(0, 0)

        fun of(entries: Iterable<LedgerEntryEntity>): MoneyTotals {
            var income = 0L
            var expense = 0L
            entries.forEach { entry ->
                when (entry.type) {
                    LedgerType.INCOME -> income += entry.amountCents
                    LedgerType.EXPENSE -> expense += entry.amountCents
                }
            }
            return MoneyTotals(income, expense)
        }
    }
}

/**
 * Everything the Today page shows, worked out the same way on the phone and the PC: what is
 * overdue, due today and coming up this week, what got done today, and money today and this
 * month. Deleted items are left out.
 */
data class TodayOverview(
    val date: LocalDate,
    val overdue: List<TodoEntity>,
    val dueToday: List<TodoEntity>,
    /** Open todos due in the next seven days, after today. */
    val upcoming: List<TodoEntity>,
    /** Todos ticked off today, whenever they were due. */
    val completedToday: List<TodoEntity>,
    val moneyToday: MoneyTotals,
    val moneyThisMonth: MoneyTotals,
    val entriesToday: List<LedgerEntryEntity>,
) {
    /** Today's progress: done today out of done plus still due today (overdue included). */
    val progress: Float
        get() {
            val total = completedToday.size + overdue.size + dueToday.size
            return if (total == 0) 0f else completedToday.size.toFloat() / total
        }

    companion object {
        fun of(
            todos: List<TodoEntity>,
            ledger: List<LedgerEntryEntity>,
            today: LocalDate = LocalDate.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): TodayOverview {
            val epoch = today.toEpochDay()
            val live = todos.filter { it.deletedAt == null }
            val open = live.filter { it.completedAt == null }
            val order = compareBy<TodoEntity> { it.deadlineEpochDay ?: Long.MAX_VALUE }
                .thenBy { it.deadlineMinute ?: Int.MAX_VALUE }
                .thenByDescending { it.priority.ordinal }
                .thenBy { it.customOrder }
            val entries = ledger.filter { it.deletedAt == null }
            val monthStart = today.withDayOfMonth(1).toEpochDay()
            return TodayOverview(
                date = today,
                overdue = open.filter { (it.deadlineEpochDay ?: Long.MAX_VALUE) < epoch }.sortedWith(order),
                dueToday = open.filter { it.deadlineEpochDay == epoch }.sortedWith(order),
                upcoming = open.filter { it.deadlineEpochDay in (epoch + 1)..(epoch + 7) }.sortedWith(order),
                completedToday = live.filter { todo ->
                    todo.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } == today
                }.sortedByDescending { it.completedAt },
                moneyToday = MoneyTotals.of(entries.filter { it.epochDay == epoch }),
                moneyThisMonth = MoneyTotals.of(entries.filter { it.epochDay in monthStart..epoch }),
                entriesToday = entries.filter { it.epochDay == epoch }.sortedByDescending { it.minuteOfDay },
            )
        }
    }
}
