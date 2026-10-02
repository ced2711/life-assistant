package com.ced2711.lifetracker.ui.localization

import com.ced2711.lifetracker.domain.model.UiLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class NewFeatureLocalizationTest {
    private val chinese = UiLanguage.SIMPLIFIED_CHINESE

    @Test
    fun `new modules and settings are translated`() {
        assertEquals("日记", translateUiText("Diary", chinese))
        assertEquals("告解室", translateUiText("Confessional", chinese))
        assertEquals("应用锁", translateUiText("App lock", chinese))
        assertEquals("菜单显示的功能", translateUiText("Modules in menu", chinese))
        assertEquals("焚烧", translateUiText("Burn", chinese))
    }

    @Test
    fun `icon button labels with spacing are translated and keep their spacing`() {
        assertEquals(" 停止", translateUiText(" Stop", chinese))
        assertEquals(" 删除", translateUiText(" Delete", chinese))
        assertEquals("   ", translateUiText("   ", chinese))
    }

    @Test
    fun `module count summary keeps its numbers`() {
        assertEquals("显示 4/6 个功能", translateUiText("4 of 6 modules shown", chinese))
        assertEquals("4 of 6 modules shown", translateUiText("4 of 6 modules shown", UiLanguage.ENGLISH))
    }
}
