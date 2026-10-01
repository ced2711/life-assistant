package com.ced2711.lifetracker.desktop

import androidx.compose.runtime.Composable
import com.ced2711.lifetracker.domain.model.AppIdentity
import com.ced2711.lifetracker.domain.model.UiLanguage
import com.ced2711.lifetracker.ui.localization.LocalUiLanguage
import com.ced2711.lifetracker.ui.localization.translateUiText

/**
 * Desktop-only labels which are not yet part of the Android string table.
 * Shared Android translations win whenever a label is already known there.
 */
private val desktopSimplifiedChinese = mapOf(
    "Fold menu (Ctrl+B)" to "收起菜单（Ctrl+B）",
    "Unfold menu (Ctrl+B)" to "展开菜单（Ctrl+B）",
    "Export failed." to "导出失败。",
    "Lock with my data password" to "用我的数据密码加密",
    "Lock with a separate backup password" to "用单独的备份密码加密",
    "Backup password" to "备份密码",
    "Confirm password" to "确认密码",
    "At least 8 characters. Nobody can recover it for you." to "至少 8 个字符。任何人都无法帮你找回它。",
    "Choose where to save" to "选择保存位置",
    "Checking…" to "正在检查…",
    "Review backup" to "查看备份内容",
    "Replace this PC's data with the backup?" to "用备份替换这台电脑的数据？",
    "Backup" to "备份",
    "This PC" to "这台电脑",
    "Ledger entries" to "流水",
    "Diary" to "日记",
    "Attachments" to "附件",
    "Everything on this PC is replaced by the backup. Export the current data first if you may need it later." to "这台电脑上的所有数据都会被备份替换。如果以后可能还需要，请先导出当前数据。",
    "Replace" to "替换",
    "Quit" to "退出",
    "Show reminders on this PC" to "在这台电脑上显示提醒",
    "As system notifications while Life Assistant is open." to "生活助手打开时以系统通知显示。",
    "Keep running in the tray when closed" to "关闭窗口后在托盘中继续运行",
    "Reminders keep arriving after the window is closed. Quit from the tray icon." to "关闭窗口后仍会收到提醒。可从托盘图标退出。",
    "Rename" to "重命名",
    "Rename category" to "重命名分类",
    "Delete category" to "删除分类",
    "Its todos become uncategorized and its subcategories move up one level." to "其中的待办会变成未分类，子分类会上移一级。",
    "Rename tag" to "重命名标签",
    "Delete tag" to "删除标签",
    "Daily net" to "每日净额",
    "Monthly net" to "每月净额",
    "Delete repeating entry" to "删除重复流水",
    "Delete repeating todo" to "删除重复待办",
    "Edit repeating entry" to "编辑重复流水",
    "Edit repeating todo" to "编辑重复待办",
    "No notes match the search." to "没有符合搜索的笔记。",
    "Open Vault" to "打开密码库",
    "Pin" to "置顶",
    "Unpin" to "取消置顶",
    "Saved" to "已保存",
    "Saving…" to "正在保存…",
    "Starts" to "开始日期",
    "Todo deleted" to "已删除待办",
    "Todos deleted" to "已删除待办",
    "Apply this to only this one, or to this one and all later ones?" to "只改这一次，还是这一次及以后的都改？",
    "Nothing to do here. Add a todo above or press Ctrl+N." to "这里没有待办。在上面添加，或按 Ctrl+N。",
    "No notes here yet. Press Ctrl+N to write one." to "这里还没有笔记。按 Ctrl+N 写一条。",
    "Choose an entry, or press Ctrl+N for a new one." to "选择一个条目，或按 Ctrl+N 新建。",
    "Enter your data password to open the Vault." to "输入数据密码以打开密码库。",
    "Delete this Vault entry?" to "删除这个密码库条目？",
    "Delete this note?" to "删除这条笔记？",
    "New entry" to "新建流水",
    "Edit entry" to "编辑流水",
    "Recurring" to "定期流水",
    "Average daily spending" to "日均支出",
    "Largest expense" to "最大支出",
    "Remove this schedule?" to "移除这个周期？",
    "Stopped" to "已停止",
    "Remove" to "移除",
    "Stop" to "停止",
    "Merged changes from your other devices." to "已合并你其他设备上的更改。",
    "Merged changes from your other devices. Some texts were edited on both; both versions were kept." to "已合并你其他设备上的更改。有些文字两边都改过，两个版本都保留了。",
    "Other devices kept changing the cloud backup. Sync again in a moment." to "其他设备一直在修改云端备份，请稍后再同步。",
    "The cloud backup uses a different data password. Choose which version to keep." to "云端备份使用了不同的数据密码，请选择保留哪个版本。",
    "Add a subtask and press Enter" to "添加子任务，按回车确认",
    "Add a todo and press Enter" to "添加待办，按回车确认",
    "Add a todo for this day" to "为这一天添加待办",
    "All time" to "全部时间",
    "All todos" to "全部待办",
    "Any" to "不限",
    "Any priority" to "任意优先级",
    "Change" to "修改",
    "Change schedule" to "修改周期",
    "Changes made on this PC and your other devices are merged automatically. The 10 most recent encrypted versions are kept." to "这台电脑和你其他设备上的修改会自动合并。云端保留最近 10 个加密版本。",
    "Close (Esc)" to "关闭（Esc）",
    "Complete this todo?" to "完成这个待办？",
    "Default reminders for new todos with a date" to "有日期的新待办默认提醒",
    "Encrypted inside the local .tlb file" to "加密保存在本地 .tlb 文件中",
    "Entries already created stay as they are. The changed schedule starts on the date below." to "已经生成的记录保持不变，修改后的周期从下面的日期开始。",
    "Entry deleted" to "已删除记录",
    "Later" to "以后",
    "Lock" to "锁定",
    "Never" to "不重复",
    "Next 30 days" to "未来 30 天",
    "Next 7 days" to "未来 7 天",
    "No Vault entries yet." to "密码库里还没有条目。",
    "No date" to "无日期",
    "No entries in this period." to "这段时间没有记录。",
    "No folders yet." to "还没有文件夹。",
    "No new entries will be added. Entries it already created stay." to "之后不会再自动添加记录，已经生成的记录会保留。",
    "No recurring entries. Choose Repeat when adding an entry." to "没有周期记录。添加记录时选择“重复”即可创建。",
    "Nothing planned for the next 30 days." to "未来 30 天没有安排。",
    "Only the todo" to "只完成待办",
    "Only this one" to "仅这一次",
    "Overdue" to "已逾期",
    "Reminder notifications" to "提醒通知",
    "Reminder time for todos without a time" to "没有具体时间的待办在几点提醒",
    "Save (Ctrl+S)" to "保存（Ctrl+S）",
    "Search (Ctrl+F)" to "搜索（Ctrl+F）",
    "Shown by the phone app. Shared with your other devices through sync." to "由手机应用发出通知，并通过同步共享到你的其他设备。",
    "Spending by tag" to "按标签统计支出",
    "Start writing…" to "开始写吧…",
    "Stop this schedule?" to "停止这个周期？",
    "System" to "跟随系统",
    "The entries it created stay as ordinary entries." to "它生成的记录会作为普通记录保留。",
    "This PC and the cloud could not be merged automatically, for example because the cloud uses a different data password. Use newest cloud replaces this PC's data; Keep this PC uploads this PC's data. Previous encrypted cloud versions are kept." to "这台电脑和云端无法自动合并，例如云端使用了不同的数据密码。“使用最新云端版本”会替换这台电脑的数据；“保留此电脑”会上传这台电脑的数据。之前的加密云端版本会保留。",
    "This and later ones" to "这次及以后",
    "Todo and subtasks" to "待办和子任务",
    "Untagged" to "无标签",
    "Until (optional)" to "截止日期（可选）",
    "Where or what, then Enter" to "在哪儿 / 买了什么，按回车",
    "Year" to "年",
    "Yesterday" to "昨天",
    "e.g. tomorrow, fri, 10/3" to "例如 明天、周五、10/3",
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
    AppIdentity.NAME to "生活助手",
    "Unlock your encrypted local data." to "解锁已加密的本地数据。",
    "Create an encrypted local data file. Use this same password for Google Drive sync." to "创建加密的本地数据文件。Google Drive 同步时使用相同的密码。",
    "Data password" to "数据密码",
    "Remember securely with Windows" to "使用 Windows 安全记住密码",
    "Remember securely on this computer" to "在这台电脑上安全记住密码",
    "Your password is never uploaded. If remembered, it is encrypted with a key kept in your desktop keyring or a file only you can read." to
        "你的密码不会上传。如果选择记住，密码会用一把只存在本机的密钥加密，这把密钥放在桌面密钥环或只有你能读的文件里。",
    "Local data is password-encrypted. Life Assistant can remember the password for this Linux user." to
        "本地数据使用密码加密。生活助手可以为这个 Linux 用户记住密码。",
    "Applies even when the password is remembered." to "即使已记住密码也会生效。",
    "Life Assistant is already open for this user." to "当前用户已经打开了生活助手。",
    "Sealed words are protected on this computer and never exported or synced." to "封存的内容只在这台电脑上加密保存，不会导出或同步。",
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
    "Encrypted snapshots are stored in Life Assistant's private Google Drive app folder. Other Drive files are not accessible." to "加密快照保存在生活助手的 Google Drive 私有应用文件夹中，无法访问 Drive 中的其他文件。",
    "Each upload creates a new encrypted version. Previous versions are kept; conflicts pause sync until you resolve them." to "每次上传都会创建新的加密版本。旧版本会保留；发生冲突时同步会暂停，直到你解决冲突。",
    "Automatic sync" to "自动同步",
    "Off by default. When enabled, checks every 15 minutes while Life Assistant is running" to "默认关闭。开启后，生活助手运行期间每 15 分钟检查一次",
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
    "Life Assistant Desktop 1.7.0 • Data format compatible with Android" to "生活助手桌面版 1.7.0 • 数据格式兼容 Android",
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
    "Life Assistant is already open on this Windows account." to "此 Windows 账户中已经打开了生活助手。",
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
    "Before using a cloud version, Life Assistant keeps an encrypted local recovery copy. Import a copy to recover earlier local data. The 5 most recent recovery copies are kept." to "使用云端版本前，生活助手会保留一份加密的本地恢复副本。导入副本即可恢复之前的本地数据。只保留最近 5 份恢复副本。",
    "About" to "关于",
    "View license" to "查看许可证",
    "Source code" to "源代码",
    "License" to "许可证",
    "Version" to "版本",
    "This software is provided without warranty." to "本软件不提供任何保证。",
    "The full license and additional permissions are available offline." to "完整许可证和附加权限可离线查看。",
    "Close" to "关闭",
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
    UiLanguage.ENGLISH -> if (count == 1) "1 note" else "$count notes"
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

fun desktopAppVersion(language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "${AppIdentity.NAME} Desktop ${AppIdentity.VERSION} • Data format compatible with Android"
    UiLanguage.SIMPLIFIED_CHINESE -> "生活助手桌面版 ${AppIdentity.VERSION} • 数据格式兼容 Android"
}

@Composable
fun desktopText(text: String): String = desktopText(text, LocalUiLanguage.current)

/** User-authored values must bypass the UI label dictionary, even if they match a built-in label. */
fun desktopUserText(text: String): String = text

fun desktopMoreCount(count: Int, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "+$count more"
    UiLanguage.SIMPLIFIED_CHINESE -> "还有 $count 项"
}

fun desktopReminderBody(dueAtMillis: Long, snapshot: com.ced2711.lifetracker.data.backup.BackupSnapshot, language: UiLanguage): String {
    val due = java.time.Instant.ofEpochMilli(dueAtMillis).atZone(java.time.ZoneId.systemDefault())
    val text = formatDeadline(due.toLocalDate().toEpochDay(), due.hour * 60 + due.minute, snapshot, language)
    return when (language) {
        UiLanguage.ENGLISH -> "Due $text"
        UiLanguage.SIMPLIFIED_CHINESE -> "截止 $text"
    }
}

fun desktopBackupCreated(time: String, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "Backup created $time"
    UiLanguage.SIMPLIFIED_CHINESE -> "备份创建于 $time"
}

fun desktopCompletedCount(count: Int, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "Completed ($count)"
    UiLanguage.SIMPLIFIED_CHINESE -> "已完成（$count）"
}

fun desktopOpenSubtasks(count: Int, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> if (count == 1) "1 subtask is not done yet. Complete it too?" else "$count subtasks are not done yet. Complete them too?"
    UiLanguage.SIMPLIFIED_CHINESE -> "还有 $count 个子任务没完成。要一起完成吗？"
}

fun desktopReminderMinutes(minutes: Long, language: UiLanguage): String {
    val (amount, unitEn, unitZh) = when {
        minutes % 1_440L == 0L -> Triple(minutes / 1_440L, "day", "天")
        minutes % 60L == 0L -> Triple(minutes / 60L, "hour", "小时")
        else -> Triple(minutes, "min", "分钟")
    }
    return when (language) {
        UiLanguage.ENGLISH -> "$amount $unitEn${if (amount != 1L && unitEn != "min") "s" else ""} before"
        UiLanguage.SIMPLIFIED_CHINESE -> "提前 $amount $unitZh"
    }
}

/** "Every 2 weeks from 09/01/2026 until 12/31/2026" and its Chinese form. */
fun desktopRepeatSummary(
    unit: com.ced2711.lifetracker.domain.model.RecurrenceUnit,
    interval: Int,
    start: String,
    end: String?,
    language: UiLanguage,
): String {
    val (en, zh) = when (unit) {
        com.ced2711.lifetracker.domain.model.RecurrenceUnit.DAY -> "day" to "天"
        com.ced2711.lifetracker.domain.model.RecurrenceUnit.WEEK -> "week" to "周"
        com.ced2711.lifetracker.domain.model.RecurrenceUnit.MONTH -> "month" to "个月"
        com.ced2711.lifetracker.domain.model.RecurrenceUnit.YEAR -> "year" to "年"
    }
    return when (language) {
        UiLanguage.ENGLISH -> (if (interval == 1) "Every $en" else "Every $interval ${en}s") + " from $start" + (end?.let { " until $it" } ?: "")
        UiLanguage.SIMPLIFIED_CHINESE -> "从 $start 起每${if (interval == 1) "" else " $interval "}$zh" + (end?.let { "，到 $it 为止" } ?: "")
    }
}

fun desktopCopiedMessage(what: String, language: UiLanguage): String = when (language) {
    UiLanguage.ENGLISH -> "$what copied. The clipboard is cleared in 30 seconds."
    UiLanguage.SIMPLIFIED_CHINESE -> "已复制$what，30 秒后自动清除剪贴板。"
}
