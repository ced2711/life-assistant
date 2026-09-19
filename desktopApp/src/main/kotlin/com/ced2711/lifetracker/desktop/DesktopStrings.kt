package com.ced2711.lifetracker.desktop

import androidx.compose.runtime.Composable
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.translateUiText

/**
 * Desktop-only labels which are not yet part of the Android string table.
 * Shared Android translations win whenever a label is already known there.
 */
private val desktopSimplifiedChinese = mapOf(
    "Todo" to "待办",
    "Ledger" to "流水",
    "Calendar" to "日历",
    "Notes" to "笔记",
    "Settings" to "设置",
    "Vault" to "密码库",
    "Search" to "搜索",
    "Filters" to "筛选",
    "Completed" to "已完成",
    "All" to "全部",
    "Uncategorized" to "未分类",
    "Sort" to "排序",
    "Priority" to "优先级",
    "Deadline" to "截止日期",
    "Amount" to "金额",
    "New folder" to "新建文件夹",
    "Folder" to "文件夹",
    "Folders" to "文件夹",
    "Appearance" to "外观",
    "Rename folder" to "重命名文件夹",
    "Delete folder" to "删除文件夹",
    "All notes" to "全部笔记",
    "Unfiled" to "未归档",
    "Operation failed" to "操作失败",
    "OK" to "确定",
    "Life Tracker" to "Life Tracker",
    "Unlock your encrypted local data." to "解锁已加密的本地数据。",
    "Create an encrypted local data file. Use this same password for Google Drive sync." to "创建加密的本地数据文件。Google Drive 同步时使用相同的密码。",
    "Data password" to "数据密码",
    "Remember securely with Windows" to "使用 Windows 安全记住密码",
    "Unlock" to "解锁",
    "Create local data" to "创建本地数据",
    "Your password is never uploaded. If remembered, it is protected by Windows DPAPI for this Windows account." to "你的密码不会上传。如果选择记住，密码会由此 Windows 账户的 DPAPI 保护。",
    "Back" to "返回",
    "Sync conflict" to "同步冲突",
    "Use newest cloud replaces this PC's data. Keep this PC publishes this PC's full dataset. Neither option merges individual records. Previous encrypted cloud versions are kept." to "使用最新云端版本会替换此电脑的数据。保留此电脑会发布此电脑的完整数据集。两种选择都不会合并单条记录。之前的加密云端版本会保留。",
    "Cancel" to "取消",
    "Use newest cloud" to "使用最新云端版本",
    "Keep this PC" to "保留此电脑版本",
    "active tasks" to "个未完成待办",
    "Category" to "分类",
    "Due" to "截止",
    "Attach" to "添加附件",
    "Edit" to "编辑",
    "Delete" to "删除",
    "New category" to "新建分类",
    "New todo" to "新建待办",
    "Edit todo" to "编辑待办",
    "Description" to "说明",
    "Title (optional)" to "标题（可选）",
    "Deadline M/D/YYYY, M/D, or day (optional)" to "截止日期 M/D/YYYY、M/D 或日期（可选）",
    "Tags, comma separated (optional)" to "标签，逗号分隔（可选）",
    "None" to "无",
    "Done" to "已完成",
    "Save" to "保存",
    "Income" to "收入",
    "Expense" to "支出",
    "Net" to "净额",
    "Daily net  •  green = net income  •  red = net expense" to "每日净额  •  绿色 = 净收入  •  红色 = 净支出",
    "Range" to "范围",
    "No ledger data yet" to "还没有流水数据",
    "New ledger entry" to "新建流水",
    "Edit ledger entry" to "编辑流水",
    "Positive amount, up to 2 decimal places" to "请输入正数，最多两位小数",
    "Date M/D/YYYY, M/D, or day" to "日期 M/D/YYYY、M/D 或日期",
    "Merchant / payer" to "商家 / 收款方",
    "Note" to "备注",
    "Previous" to "上一个",
    "Next" to "下一个",
    "Todos" to "待办",
    "No todos" to "没有待办",
    "No ledger entries" to "没有流水",
    "Close" to "关闭",
    "Hide folders" to "隐藏文件夹",
    "New note" to "新建笔记",
    "Edit note" to "编辑笔记",
    "Title" to "标题",
    "Pinned" to "置顶",
    "Name" to "名称",
    "Untitled" to "未命名",
    "Show password" to "显示密码",
    "New Vault entry" to "新建密码库条目",
    "Edit Vault entry" to "编辑密码库条目",
    "Label" to "标签名",
    "Account" to "账号",
    "Password" to "密码",
    "Website" to "网站",
    "Google Drive sync" to "Google Drive 同步",
    "Connected" to "已连接",
    "Not connected" to "未连接",
    "Encrypted snapshots are stored in Life Tracker's private Google Drive app folder. Other Drive files are not accessible." to "加密快照保存在 Life Tracker 的 Google Drive 私有应用文件夹中，无法访问 Drive 中的其他文件。",
    "Each upload creates a new encrypted version. Previous versions are kept; conflicts pause sync until you resolve them." to "每次上传都会创建新的加密版本。旧版本会保留；发生冲突时同步会暂停，直到你解决冲突。",
    "Automatic sync" to "自动同步",
    "Off by default. When enabled, checks every 15 minutes while Life Tracker is running" to "默认关闭。开启后，Life Tracker 运行期间每 15 分钟检查一次",
    "Syncing…" to "同步中…",
    "Sync now" to "立即同步",
    "Disconnect / switch account" to "断开连接 / 切换账号",
    "Connect Google Drive" to "连接 Google Drive",
    "Local security" to "本地安全",
    "Local data is password-encrypted. Windows can remember the password using DPAPI for this Windows account." to "本地数据使用密码加密。Windows 可使用此账户的 DPAPI 安全记住密码。",
    "Forget remembered password" to "忘记已保存的密码",
    "Theme" to "主题",
    "Accent color" to "强调色",
    "UI language" to "界面语言",
    "English" to "English",
    "Week starts on" to "每周开始于",
    "Time format" to "时间格式",
    "Date format" to "日期格式",
    "Encrypted account and password entries" to "加密保存的账号和密码条目",
    "Open" to "打开",
    "Encrypted backup" to "加密备份",
    "The .tlb file includes todos, ledger entries, notes, Vault entries, and attached files. Manual import can migrate an older Android backup password to this PC's current data password." to ".tlb 文件包含待办、流水、笔记、密码库条目和附件。手动导入可以将旧 Android 备份密码迁移为此电脑当前的数据密码。",
    "Encrypted backup exported." to "加密备份已导出。",
    "Export backup" to "导出备份",
    "Import backup" to "导入备份",
    "Life Tracker Desktop 1.6.1 • Data format compatible with Android" to "Life Tracker Desktop 1.6.1 • 数据格式兼容 Android",
    "Import encrypted backup?" to "导入加密备份？",
    "Enter the password used when this backup was created. After validation, its contents will be encrypted with this PC's current data password." to "输入创建此备份时使用的密码。验证后，内容会使用此电脑当前的数据密码重新加密。",
    "Source backup password" to "源备份密码",
    "This replaces the local records. Export the current data first if you may need it later." to "这会替换本地记录。如果之后可能需要当前数据，请先导出备份。",
    "Restore" to "恢复",
    "Backup imported and encrypted with the current data password." to "备份已导入，并使用当前数据密码重新加密。",
    "Local data changed; import was cancelled." to "本地数据已更改，导入已取消。",
    "The source password is incorrect or the backup is damaged." to "源密码不正确或备份已损坏。",
    "A Google Cloud Desktop OAuth client from the same project as the Android app is required for this open-source build. Sign-in opens in your system browser." to "此开源版本需要与 Android 应用位于同一项目的 Google Cloud Desktop OAuth 客户端。登录会在系统浏览器中打开。",
    "Desktop OAuth client ID" to "Desktop OAuth 客户端 ID",
    "Desktop OAuth client secret (optional)" to "Desktop OAuth 客户端密钥（可选）",
    "If supplied, the client secret is protected by Windows DPAPI. OAuth desktop secrets are application configuration, not a replacement for PKCE." to "如果提供客户端密钥，它会由 Windows DPAPI 保护。OAuth 桌面密钥属于应用配置，不能替代 PKCE。",
    "Open Google sign-in" to "打开 Google 登录",
    "Open attachment" to "打开附件",
    "Save a copy" to "保存副本",
    "Remove attachment" to "移除附件",
    "Notes stay safe; the folder is removed and child folders move up one level." to "笔记会保留；文件夹会被移除，子文件夹会提升一级。",
    "The file operation could not be completed. Check available storage and file access, then try again." to "文件操作无法完成。请检查存储空间和文件访问权限后重试。",
    "One of the entered values is invalid." to "输入的值无效。",
    "The operation could not be completed. Your last saved data was kept." to "操作无法完成。上次保存的数据已保留。",
    "Life Tracker is already open on this Windows account." to "此 Windows 账户中已经打开了 Life Tracker。",
    "The local data password is incorrect or the file is damaged." to "本地数据密码不正确或文件已损坏。",
    "Google Drive connected." to "Google Drive 已连接。",
    "Google Drive disconnected. Local data was kept." to "Google Drive 已断开连接。本地数据已保留。",
    "Google Drive changed while the conflict choice was open. Review the refreshed conflict." to "冲突选择打开期间 Google Drive 发生了更改。请查看刷新后的冲突。",
    "The conflicting cloud history uses a different data password. No cloud data was changed." to "冲突的云端历史使用了不同的数据密码。云端数据未更改。",
    "This PC and Google Drive both changed." to "此电脑和 Google Drive 都发生了更改。",
    "Already up to date." to "已经是最新。",
    "Encrypted backup uploaded." to "加密备份已上传。",
    "Cloud changes restored." to "云端更改已恢复。",
    "Local data changed before the cloud choice could be applied. Review the conflict." to "应用云端选择前本地数据已更改。请查看冲突。",
    "The cloud backup uses a different data password or is damaged." to "云端备份使用了不同的数据密码或已损坏。",
    "Google Drive changed before upload. Review the refreshed conflict." to "上传前 Google Drive 发生了更改。请查看刷新后的冲突。",
    "Another device uploaded at the same time. Both encrypted versions were kept; review the conflict." to "另一台设备同时上传。两个加密版本都已保留，请查看冲突。",
    "Local data changed while the cloud copy was downloading." to "下载云端副本期间本地数据已更改。",
    "Google Drive could not be reached." to "无法连接 Google Drive。",
    "Google Drive timed out." to "Google Drive 请求超时。",
    "Cloud sync settings are invalid." to "云端同步设置无效。",
    "Google Drive sync failed." to "Google Drive 同步失败。",
    "Google sign-in timed out. Try again; your existing connection was kept." to "Google 登录超时。请重试；现有连接已保留。",
    "Google Drive is not connected." to "Google Drive 未连接。",
    "Google did not grant the required private Drive permission. Reconnect and allow access." to "Google 未授予所需的私有 Drive 权限。请重新连接并允许访问。",
    "Google could not be reached during sign-in. Check your internet connection and try again." to "登录期间无法连接 Google。请检查网络连接后重试。",
    "Google authorization was denied or cancelled. Your existing connection was kept." to "Google 授权被拒绝或取消。现有连接已保留。",
    "Google policy blocked this sign-in. Use an allowed account or review the OAuth consent settings." to "Google 政策阻止了此次登录。请使用允许的账号或检查 OAuth 同意设置。",
    "Google is temporarily unavailable. Try again." to "Google 暂时不可用，请重试。",
    "Google could not authorize this app. Check the OAuth client and try again." to "Google 无法授权此应用。请检查 OAuth 客户端后重试。",
    "Google rejected the OAuth configuration. Check the client settings and try again." to "Google 拒绝了 OAuth 配置。请检查客户端设置后重试。",
    "Google token exchange failed. Check the OAuth client configuration and try again." to "Google 令牌交换失败。请检查 OAuth 客户端配置后重试。",
    "Google token request timed out. Try again." to "Google 令牌请求超时，请重试。",
    "Google rejected this OAuth client. Check the Desktop OAuth client ID/secret and that the client is enabled." to "Google 拒绝了此 OAuth 客户端。请检查 Desktop OAuth 客户端 ID/密钥并确认客户端已启用。",
    "Google rejected the authorization code or refresh token. Reconnect Google Drive." to "Google 拒绝了授权码或刷新令牌。请重新连接 Google Drive。",
    "Google denied Drive access. Choose an account that can use this app and try again." to "Google 拒绝了 Drive 访问。请选择可使用此应用的账号后重试。",
    "Before using a cloud version, Life Tracker keeps an encrypted local recovery copy. Import a copy to recover earlier local data. Recovery copies are not deleted automatically." to "使用云端版本前，Life Tracker 会保留一份加密的本地恢复副本。导入副本即可恢复之前的本地数据。恢复副本不会自动删除。",
    "Open sync recovery folder" to "打开同步恢复文件夹",
)

fun desktopText(text: String, language: UiLanguage): String {
    val shared = translateUiText(text, language)
    return if (shared != text || language == UiLanguage.ENGLISH) {
        shared
    } else {
        desktopSimplifiedChinese[text] ?: text
    }
}

/** Dynamic labels are translated as complete UI strings so user-authored text is never routed here. */
fun desktopActiveTasks(count: Int, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "$count active tasks"
    UiLanguage.SIMPLIFIED_CHINESE -> "$count 个未完成待办"
}

fun desktopNotesCount(count: Int, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "$count notes"
    UiLanguage.SIMPLIFIED_CHINESE -> "$count 条笔记"
}

fun desktopDue(value: String, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "Due $value"
    UiLanguage.SIMPLIFIED_CHINESE -> "截止 $value"
}

fun desktopNet(value: String, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "Net $value"
    UiLanguage.SIMPLIFIED_CHINESE -> "净额 $value"
}

fun desktopRange(value: String, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "Range ±$value"
    UiLanguage.SIMPLIFIED_CHINESE -> "范围 ±$value"
}

fun desktopLastSync(value: String, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "Last sync: $value"
    UiLanguage.SIMPLIFIED_CHINESE -> "上次同步：$value"
}

@Composable
fun desktopText(text: String): String = desktopText(text, LocalUiLanguage.current)

/** User-authored values must bypass the UI label dictionary, even if they match a built-in label. */
fun desktopUserText(text: String): String = text
