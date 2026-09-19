package com.ced2711.lifetracker.ui.localization

import com.ced2711.lifetracker.domain.model.UiLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class UiLocalizationTest {
    @Test
    fun `Google authorization diagnostics preserve codes in Chinese`() {
        val chinese = UiLanguage.SIMPLIFIED_CHINESE
        assertEquals(
            "Google Drive 配置不完整（代码 10）。请检查 Drive API、包名、签名证书 SHA-1 和 OAuth 项目。",
            translateUiText("Google Drive setup is incomplete (code 10). Check Drive API, package name, signing certificate SHA-1, and OAuth project.", chinese),
        )
        assertEquals(
            "Google Drive 授权已取消或关闭。如果你没有取消，请检查应用的 Google Cloud 配置。",
            translateUiText("Google Drive authorization was cancelled or closed. If you did not cancel, check the app's Google Cloud setup.", chinese),
        )
        assertEquals(
            "Google Drive 授权失败（代码 17）。",
            translateUiText("Google Drive authorization failed (code 17).", chinese),
        )
        assertEquals(
            "Google Drive 授权未返回结果（结果代码 -2）。",
            translateUiText("Google Drive authorization returned no result (result code -2).", chinese),
        )
    }

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
