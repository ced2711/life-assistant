package com.ced2711.lifetracker.data.local

import androidx.room.TypeConverter
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import com.ced2711.lifetracker.domain.model.LedgerType
import com.ced2711.lifetracker.domain.model.RecurrenceUnit
import com.ced2711.lifetracker.domain.model.TodoPriority

class Converters {
    @TypeConverter fun priorityToString(value: TodoPriority): String = value.name
    @TypeConverter fun stringToPriority(value: String): TodoPriority = TodoPriority.valueOf(value)

    @TypeConverter fun ledgerTypeToString(value: LedgerType): String = value.name
    @TypeConverter fun stringToLedgerType(value: String): LedgerType = LedgerType.valueOf(value)

    @TypeConverter fun recurrenceUnitToString(value: RecurrenceUnit): String = value.name
    @TypeConverter fun stringToRecurrenceUnit(value: String): RecurrenceUnit = RecurrenceUnit.valueOf(value)

    @TypeConverter fun ownerTypeToString(value: AttachmentOwnerType): String = value.name
    @TypeConverter fun stringToOwnerType(value: String): AttachmentOwnerType = AttachmentOwnerType.valueOf(value)
}
