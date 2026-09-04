package com.ced2711.lifetracker.ui.localization

import com.ced2711.lifetracker.domain.model.UiLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class UiLocalizationTest {
    @Test
    fun `English leaves UI text unchanged`() {
        assertEquals("Settings", translateUiText("Settings", UiLanguage.ENGLISH))
        assertEquals("3 reminders", translateUiText("3 reminders", UiLanguage.ENGLISH))
    }

    @Test
    fun `Simplified Chinese translates important static and dynamic UI text`() {
        assertEquals("设置", translateUiText("Settings", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("3 个提醒", translateUiText("3 reminders", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals(
            "已完成 2 项 • 今天还剩 4 项",
            translateUiText("2 completed • 4 due today", UiLanguage.SIMPLIFIED_CHINESE),
        )
        assertEquals(
            "子任务：Call 学校",
            translateUiText("Subtask: Call 学校", UiLanguage.SIMPLIFIED_CHINESE),
        )
        assertEquals(
            "是否同时完成“Buy milk”中尚未完成的子任务？",
            translateUiText(
                "Choose whether to also complete any unfinished subtasks in “Buy milk”.",
                UiLanguage.SIMPLIFIED_CHINESE,
            ),
        )
    }

    @Test
    fun `arbitrary user entered text is never translated`() {
        val userText = "Settings for 学校 account"
        assertEquals(userText, translateUiText(userText, UiLanguage.SIMPLIFIED_CHINESE))
    }

    @Test
    fun `UI locale follows the selected language`() {
        assertEquals(Locale.ENGLISH, uiLocale(UiLanguage.ENGLISH))
        assertEquals(Locale.SIMPLIFIED_CHINESE, uiLocale(UiLanguage.SIMPLIFIED_CHINESE))
    }
}
