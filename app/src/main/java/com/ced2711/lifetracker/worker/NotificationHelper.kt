package com.ced2711.lifetracker.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ced2711.lifetracker.MainActivity
import com.ced2711.lifetracker.R
import com.ced2711.lifetracker.data.local.TodoEntity

internal enum class NotificationDeliveryResult {
    DELIVERED,
    ALREADY_DELIVERED,
    BLOCKED_BY_PERMISSION,
}

internal object NotificationHelper {
    private const val CHANNEL_ID = "todo_reminders"
    private const val CHANNEL_NAME = "Todo reminders"
    private const val CHANNEL_DESCRIPTION = "Deadline reminders for todos"
    private const val DELIVERY_PREFERENCES = "taskledger_delivered_reminders"
    private val deliveryLock = Any()

    fun showTodoReminder(
        context: Context,
        todo: TodoEntity,
        deliveryKey: ReminderDeliveryKey,
    ): NotificationDeliveryResult {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return NotificationDeliveryResult.BLOCKED_BY_PERMISSION
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return NotificationDeliveryResult.BLOCKED_BY_PERMISSION
        }

        return synchronized(deliveryLock) {
            val deliveries = context.getSharedPreferences(DELIVERY_PREFERENCES, Context.MODE_PRIVATE)
            if (deliveries.getBoolean(deliveryKey.storageKey, false)) {
                return@synchronized NotificationDeliveryResult.ALREADY_DELIVERED
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = CHANNEL_DESCRIPTION
                },
            )
            if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
                return@synchronized NotificationDeliveryResult.BLOCKED_BY_PERMISSION
            }

            val launchIntent = MainActivity.todoReminderIntent(context, todo.id).apply {
                data = Uri.Builder()
                    .scheme("taskledger")
                    .authority("todo-reminder")
                    .appendPath(todo.id.toString())
                    .appendQueryParameter("offset", deliveryKey.offsetMinutes.toString())
                    .appendQueryParameter("trigger", deliveryKey.triggerAtMillis.toString())
                    .build()
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                deliveryKey.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val body = todo.description.takeIf(String::isNotBlank) ?: "Todo reminder"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(todo.title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            manager.notify(deliveryKey.notificationTag, DELIVERY_NOTIFICATION_ID, notification)
            deliveries.edit(commit = true) { putBoolean(deliveryKey.storageKey, true) }
            NotificationDeliveryResult.DELIVERED
        }
    }

    /** Keeps only deliveries that still match an active todo, offset, and wall-clock trigger. */
    fun cancelPostedRemindersExcept(
        context: Context,
        validDeliveryKeys: Set<ReminderDeliveryKey>,
    ) {
        synchronized(deliveryLock) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val validNotificationTags = validDeliveryKeys.mapTo(mutableSetOf()) { it.notificationTag }
            manager.activeNotifications
                .asSequence()
                .filter { it.notification.channelId == CHANNEL_ID }
                .filterNot { it.tag in validNotificationTags }
                .forEach { manager.cancel(it.tag, it.id) }

            val validStorageKeys = validDeliveryKeys.mapTo(mutableSetOf()) { it.storageKey }
            val deliveries = context.getSharedPreferences(DELIVERY_PREFERENCES, Context.MODE_PRIVATE)
            deliveries.edit {
                deliveries.all.keys
                    .filterNot(validStorageKeys::contains)
                    .forEach { remove(it) }
            }
        }
    }

    fun cancelAll(context: Context) {
        synchronized(deliveryLock) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications
                .asSequence()
                .filter { it.notification.channelId == CHANNEL_ID }
                .forEach { manager.cancel(it.tag, it.id) }
        }
    }

    private const val DELIVERY_NOTIFICATION_ID = 1
}
