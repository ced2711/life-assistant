package com.ced2711.lifetracker.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetNavigationTest {
    @Test
    fun todoActionParsesAsTodoQuickAdd() {
        assertEquals(
            WidgetQuickAddDestination.TODO,
            WidgetNavigation.destinationForAction(WidgetNavigation.ACTION_OPEN_TODO_LIST),
        )
    }

    @Test
    fun ledgerActionParsesAsLedgerQuickAdd() {
        assertEquals(
            WidgetQuickAddDestination.LEDGER,
            WidgetNavigation.destinationForAction(WidgetNavigation.ACTION_OPEN_LEDGER),
        )
    }

    @Test
    fun unknownAndMissingActionsAreRejected() {
        assertNull(WidgetNavigation.destinationForAction(null))
        assertNull(WidgetNavigation.destinationForAction("com.ced2711.lifetracker.action.UNKNOWN"))
    }
}
