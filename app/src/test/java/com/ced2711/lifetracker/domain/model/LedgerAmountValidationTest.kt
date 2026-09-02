package com.ced2711.lifetracker.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerAmountValidationTest {
    @Test
    fun `minimum and maximum amounts are valid`() {
        requireValidLedgerAmount(1L)
        requireValidLedgerAmount(MAX_LEDGER_AMOUNT_CENTS)
    }

    @Test
    fun `zero negative and over-limit amounts are rejected`() {
        listOf(0L, -1L, MAX_LEDGER_AMOUNT_CENTS + 1L).forEach { amountCents ->
            val failure = runCatching { requireValidLedgerAmount(amountCents) }.exceptionOrNull()

            assertTrue("Expected $amountCents to be rejected", failure is IllegalArgumentException)
        }
    }
}
