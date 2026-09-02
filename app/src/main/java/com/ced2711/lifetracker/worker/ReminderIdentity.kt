package com.ced2711.lifetracker.worker

internal data class ReminderDeliveryKey(
    val todoId: Long,
    val offsetMinutes: Long,
    val triggerAtMillis: Long,
) {
    init {
        require(todoId > 0L) { "A reminder requires a persisted todo" }
        require(offsetMinutes >= 0L) { "A reminder offset must not be negative" }
    }

    val storageKey: String
        get() = "$KEY_VERSION|$todoId|$offsetMinutes|$triggerAtMillis"

    val notificationTag: String
        get() = "$NOTIFICATION_TAG_PREFIX$storageKey"

    val workName: String
        get() = "$WORK_NAME_PREFIX$storageKey"

    val workTag: String
        get() = "$WORK_TAG_PREFIX$storageKey"

    companion object {
        private const val KEY_VERSION = "v2"
        private const val NOTIFICATION_TAG_PREFIX = "taskledger_reminder|"
        private const val WORK_NAME_PREFIX = "taskledger_reminder_work|"
        private const val WORK_TAG_PREFIX = "taskledger_reminder_key|"

        fun isWorkTag(tag: String): Boolean = tag.startsWith(WORK_TAG_PREFIX)
    }
}

internal enum class ReminderWorkTagStatus {
    LEGACY,
    VALID,
    INVALID,
}

internal fun classifyReminderWorkTags(
    tags: Set<String>,
    validDeliveryTags: Set<String>,
): ReminderWorkTagStatus {
    val deliveryTags = tags.filter(ReminderDeliveryKey::isWorkTag)
    return when {
        deliveryTags.isEmpty() -> ReminderWorkTagStatus.LEGACY
        deliveryTags.size == 1 && deliveryTags.single() in validDeliveryTags ->
            ReminderWorkTagStatus.VALID
        else -> ReminderWorkTagStatus.INVALID
    }
}
