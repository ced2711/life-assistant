package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.domain.model.UiLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopStringsTest {
    @Test
    fun desktopLabelsTranslateAndDynamicValuesKeepTheirOrder() {
        assertEquals("流水", desktopText("Ledger", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("连接 Google Drive", desktopText("Connect Google Drive", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("3 个未完成待办", desktopActiveTasks(3, UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("12 条笔记", desktopNotesCount(12, UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("截止 9/20/2026", desktopDue("9/20/2026", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("净额 $12.00", desktopNet("$12.00", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("打开同步恢复文件夹", desktopText("Open sync recovery folder", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals(
            "使用最新云端版本会替换此电脑的数据。保留此电脑会发布此电脑的完整数据集。两种选择都不会合并单条记录。之前的加密云端版本会保留。",
            desktopText(
                "Use newest cloud replaces this PC's data. Keep this PC publishes this PC's full dataset. Neither option merges individual records. Previous encrypted cloud versions are kept.",
                UiLanguage.SIMPLIFIED_CHINESE,
            ),
        )
    }

    @Test
    fun userWrittenTextIsNotTranslatedByTheDesktopUiRoute() {
        // User values in DesktopApp are passed through the identity route; labels use the dictionary.
        assertEquals("Ledger", desktopUserText("Ledger"))
        assertEquals("Connect Google Drive", desktopUserText("Connect Google Drive"))
        assertEquals("Ledger", desktopText("Ledger", UiLanguage.ENGLISH))
    }
}
