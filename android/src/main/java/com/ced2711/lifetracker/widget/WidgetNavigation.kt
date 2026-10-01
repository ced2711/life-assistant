package com.ced2711.lifetracker.widget

import android.content.Context
import android.content.Intent
import com.ced2711.lifetracker.MainActivity

internal enum class WidgetQuickAddDestination {
    TODO,
    LEDGER,
}

/** Intent contract for widget shortcuts. MainActivity owns the eventual route handling. */
object WidgetNavigation {
    const val ACTION_OPEN_TODO_LIST = "com.ced2711.lifetracker.action.OPEN_TODO_LIST"
    const val ACTION_OPEN_LEDGER = "com.ced2711.lifetracker.action.OPEN_LEDGER"

    fun todoListIntent(context: Context): Intent = mainActivityIntent(context, ACTION_OPEN_TODO_LIST)

    fun ledgerIntent(context: Context): Intent = mainActivityIntent(context, ACTION_OPEN_LEDGER)

    internal fun destinationForAction(action: String?): WidgetQuickAddDestination? = when (action) {
        ACTION_OPEN_TODO_LIST -> WidgetQuickAddDestination.TODO
        ACTION_OPEN_LEDGER -> WidgetQuickAddDestination.LEDGER
        else -> null
    }

    private fun mainActivityIntent(context: Context, destinationAction: String) =
        Intent(context, MainActivity::class.java).apply {
            action = destinationAction
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
