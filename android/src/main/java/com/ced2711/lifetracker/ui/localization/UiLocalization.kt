package com.ced2711.lifetracker.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.ced2711.lifetracker.domain.model.UiLanguage
import java.util.Locale

val LocalUiLanguage = staticCompositionLocalOf { UiLanguage.ENGLISH }

@Composable
fun localizedText(text: String): String = translateUiText(text, LocalUiLanguage.current)

fun uiLocale(language: UiLanguage): Locale = when (language) {
    UiLanguage.ENGLISH -> Locale.ENGLISH
    UiLanguage.SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
}

fun translateUiText(text: String, language: UiLanguage): String {
    if (language == UiLanguage.ENGLISH || text.isEmpty()) return text
    zhHans[text]?.let { return it }
    // Button labels such as " Stop" carry spacing next to an icon; keep it around the translation.
    val trimmed = text.trim()
    if (trimmed.isNotEmpty() && trimmed != text) zhHans[trimmed]?.let { return text.replace(trimmed, it) }
    return translateDynamicChinese(text)
}

private fun translateDynamicChinese(text: String): String {
    fun match(pattern: String): MatchResult? = Regex(pattern).matchEntire(text)

    match("Version (.+) • Private and offline")?.let { return "版本 ${it.groupValues[1]} • 私密离线" }
    match("Last sync: (.+)")?.let { return "上次同步：${it.groupValues[1]}" }
    match("Google Drive authorization was cancelled or closed\\. If you did not cancel, check the app's Google Cloud setup(?: \\(code (-?\\d+)\\))?\\.")?.let {
        val code = it.groupValues[1].takeIf(String::isNotEmpty)?.let { value -> "（代码 $value）" }.orEmpty()
        return "Google Drive 授权已取消或关闭。如果你没有取消，请检查应用的 Google Cloud 配置$code。"
    }
    match("Google Drive setup is incomplete(?: \\(code (-?\\d+)\\))?\\. Check Drive API, package name, signing certificate SHA-1, and OAuth project\\.")?.let {
        val code = it.groupValues[1].takeIf(String::isNotEmpty)?.let { value -> "（代码 $value）" }.orEmpty()
        return "Google Drive 配置不完整$code。请检查 Drive API、包名、签名证书 SHA-1 和 OAuth 项目。"
    }
    match("Google Drive authorization failed \\(code (-?\\d+)\\)\\.")?.let {
        return "Google Drive 授权失败（代码 ${it.groupValues[1]}）。"
    }
    match("Google Drive authorization returned no result \\(result code (-?\\d+)\\)\\.")?.let {
        return "Google Drive 授权未返回结果（结果代码 ${it.groupValues[1]}）。"
    }
    match("Active \\((\\d+)\\)")?.let { return "待完成（${it.groupValues[1]}）" }
    match("Completed \\((\\d+)\\)")?.let { return "已完成（${it.groupValues[1]}）" }
    match("(\\d+) notes")?.let { return "${it.groupValues[1]} 条笔记" }
    match("The GitHub app cannot write to (.+)\\. Install it on that repository with Contents read and write permission\\.")?.let {
        return "GitHub App 无法写入 ${it.groupValues[1]}。请把它安装到这个仓库，并授予 Contents 读写权限。"
    }
    match("GitHub repository (.+) was not found\\.")?.let { return "找不到 GitHub 仓库 ${it.groupValues[1]}。" }
    match("The GitHub App can access several repositories \\((.+)\\)\\. Enter the one to use\\.")?.let {
        return "GitHub App 可以访问多个仓库（${it.groupValues[1]}），请填写要使用的那一个。"
    }
    match("(\\d+) of (\\d+) modules shown")?.let {
        return "显示 ${it.groupValues[1]}/${it.groupValues[2]} 个功能"
    }
    match("(\\d+) reminders")?.let { return "${it.groupValues[1]} 个提醒" }
    match("(\\d+) categories selected")?.let { return "已选择 ${it.groupValues[1]} 个分类" }
    match("(\\d+) of 10 files")?.let { return "${it.groupValues[1]}/10 个文件" }
    match("Add files \\((.+)\\)")?.let { return "添加文件（${it.groupValues[1]}）" }
    match("Folders · (.+)")?.let { return "文件夹 · ${translateDynamicChinese(it.groupValues[1])}" }
    match("Search · (.+)")?.let { return "搜索 · ${it.groupValues[1]}" }
    match("Inside (.+)")?.let { return "位于 ${it.groupValues[1]} 中" }
    match("Delete (.+)\\? This cannot be undone\\.")?.let {
        return "删除“${it.groupValues[1]}”？此操作无法撤销。"
    }
    match("Delete (.+)\\?")?.let { return "删除“${it.groupValues[1]}”？" }
    match("Choose whether to also complete any unfinished subtasks in “(.+)”\\.")?.let {
        return "是否同时完成“${it.groupValues[1]}”中尚未完成的子任务？"
    }
    match("Choose how much of “(.+)” to delete\\. Past occurrences are not changed\\.")?.let {
        return "选择要删除“${it.groupValues[1]}”的范围；过去的重复项不会更改。"
    }
    match("Choose (.+)")?.let {
        val label = zhHans[it.groupValues[1]] ?: translateDynamicChinese(it.groupValues[1])
        return "选择$label"
    }
    match("Deleted (.+) and future occurrences")?.let {
        return "已删除 ${it.groupValues[1]} 及之后的重复项"
    }
    match("Deleted (.+)")?.let { return "已删除 ${it.groupValues[1]}" }
    match("Removed (.+)")?.let { return "已移除 ${it.groupValues[1]}" }
    match("Remove (.+)")?.let { return "移除 ${it.groupValues[1]}" }
    match("Edit (.+)")?.let { return "编辑 ${it.groupValues[1]}" }
    match("Subtask: (.+)")?.let { return "子任务：${it.groupValues[1]}" }
    match("Add (.+) tag")?.let { return "添加标签 ${it.groupValues[1]}" }
    match("Rename #(.+)")?.let { return "重命名 #${it.groupValues[1]}" }
    match("Delete #(.+)\\?")?.let { return "删除 #${it.groupValues[1]}？" }
    match("Selected: (.+)")?.let { return "已选择：${it.groupValues[1]}" }
    match("Every N (day|week|month|year)\\(s\\)")?.let {
        val unit = when (it.groupValues[1]) {
            "day" -> "天"
            "week" -> "周"
            "month" -> "月"
            else -> "年"
        }
        return "每 N $unit"
    }
    match("Every (\\d+) (day|week|month|year)s")?.let {
        val unit = when (it.groupValues[2]) {
            "day" -> "天"
            "week" -> "周"
            "month" -> "个月"
            else -> "年"
        }
        return "每 ${it.groupValues[1]} $unit"
    }
    match("Across (\\d+) day(s?)")?.let { return "共 ${it.groupValues[1]} 天" }
    match("(\\d+) active")?.let { return "${it.groupValues[1]} 个筛选条件已启用" }
    match("Nice work — (\\d+) completed today")?.let {
        return "做得好——今天已完成 ${it.groupValues[1]} 项"
    }
    match("(\\d+) left")?.let { return "剩余 ${it.groupValues[1]} 项" }
    match("Income (.+)")?.let { return "收入 ${it.groupValues[1]}" }
    match("Expenses (.+)")?.let { return "支出 ${it.groupValues[1]}" }
    match("(\\d+) completed • All caught up today")?.let {
        return "已完成 ${it.groupValues[1]} 项 • 今天已全部完成"
    }
    match("(\\d+) completed • (\\d+) due today")?.let {
        return "已完成 ${it.groupValues[1]} 项 • 今天还剩 ${it.groupValues[2]} 项"
    }
    match("(\\d+) (todo|todos)  \\|  (\\d+) ledger (entry|entries)")?.let {
        return "${it.groupValues[1]} 项待办  |  ${it.groupValues[3]} 笔流水"
    }
    match("(.+) to (.+)")?.let { return "${it.groupValues[1]} 至 ${it.groupValues[2]}" }
    match("(\\d+) (day|days|hour|hours|week|weeks) before")?.let {
        val unit = when (it.groupValues[2]) {
            "day", "days" -> "天"
            "hour", "hours" -> "小时"
            else -> "周"
        }
        return "提前 ${it.groupValues[1]} $unit"
    }
    match("Enter a whole number from 1 to (\\d+) (hours|days|weeks)\\.")?.let {
        val unit = when (it.groupValues[2]) {
            "hours" -> "小时"
            "days" -> "天"
            else -> "周"
        }
        return "请输入 1 到 ${it.groupValues[1]} 之间的整数（$unit）。"
    }
    match("Maximum: (\\d+) (hours|days|weeks) before the deadline\\.")?.let {
        val unit = when (it.groupValues[2]) {
            "hours" -> "小时"
            "days" -> "天"
            else -> "周"
        }
        return "最多可提前 ${it.groupValues[1]} $unit。"
    }
    match("• (.+)")?.let { return "• ${it.groupValues[1]}" }
    return text
}

private val zhHans = mapOf(
    // Navigation and common actions
    "Todo" to "待办",
    "Ledger" to "流水",
    "Calendar" to "日历",
    "Notes" to "笔记",
    "Settings" to "设置",
    "Vault" to "密码库",
    "Backup & restore" to "备份与恢复",
    "Backup & sync" to "备份与同步",
    "Back" to "返回",
    "Add" to "添加",
    "New" to "新建",
    "Save" to "保存",
    "Saving…" to "正在保存…",
    "Cancel" to "取消",
    "Close" to "关闭",
    "Done" to "完成",
    "Delete" to "删除",
    "Rename" to "重命名",
    "Remove" to "移除",
    "Open" to "打开",
    "Choose" to "选择",
    "Copy" to "复制",
    "Clear" to "清除",
    "Continue" to "继续",
    "Export" to "导出",
    "Retry" to "重试",
    "Undo" to "撤销",
    "Finish" to "完成",
    "Copying…" to "正在复制…",
    "Retry files" to "重试文件",
    "Today" to "今天",
    "Complete" to "完成",
    "Add todo" to "添加待办",
    "Unavailable" to "不可用",
    "Offline" to "离线",
    "Open Life Assistant to recover data" to "打开 生活助手 以恢复数据",
    "Open Life Assistant to finish data recovery" to "打开 生活助手 完成数据恢复",
    "All clear for today ✓" to "今天已全部完成 ✓",
    "+ Task" to "+ 待办",
    "Todo reminder" to "待办提醒",
    "Todo reminders" to "待办提醒",
    "Deadline reminders for todos" to "待办截止日期提醒",
    "Previous" to "上一个",
    "Next" to "下一个",
    "Tomorrow" to "明天",
    "Next week" to "下周",
    "Search" to "搜索",
    "All" to "全部",
    "None" to "无",
    "System default" to "跟随系统",
    "Light" to "浅色",
    "Dark" to "深色",

    // Startup, backup and security
    "Finishing data recovery…" to "正在完成数据恢复…",
    "Your tasks, ledger, and vault will open when it is safe." to "安全检查完成后即可打开待办、流水和密码库。",
    "Data recovery couldn't finish" to "数据恢复未能完成",
    "Life Assistant has kept your data closed to avoid conflicting changes. Try again before using the app." to
        "生活助手 已暂时锁定数据以避免冲突，请重试后再使用。",
    "Private, offline backup" to "私密离线备份",
    "Restore your previous data" to "恢复以前的数据",
    "This personal migration build contains your original encrypted backup. Enter its password to validate it, review the contents, and restore. The password and decrypted data are not built into the app." to
        "此个人迁移版本包含你原来的加密备份。请输入密码进行验证、查看内容并恢复；密码和解密后的数据并未内置在应用中。",
    "Export encrypted backup" to "导出加密备份",
    "Choose where to create a .tlb file, then protect it with a password of at least 8 characters. Exporting the Vault may require device authentication." to
        "选择 .tlb 文件的保存位置，并使用至少 8 个字符的密码保护。导出密码库时可能需要设备身份验证。",
    "Restore from backup" to "从备份恢复",
    "Open a .tlb file and enter its password. The backup is decrypted and fully validated before you see a required preview or any data is changed." to
        "打开 .tlb 文件并输入密码。应用会先解密并完整验证备份，然后显示必须确认的预览，确认前不会更改任何数据。",
    "Your todos, ledger, notes, settings, attachments, and Vault are encrypted into one local .tlb file. Nothing is uploaded." to
        "待办、流水、笔记、设置、附件和密码库会加密到一个本地 .tlb 文件中，不会上传。",
    "The backup password is never saved and cannot be recovered." to "备份密码不会被保存，也无法找回。",
    "Backup password" to "备份密码",
    "Confirm password" to "确认密码",
    "Hide password" to "隐藏密码",
    "Show password" to "显示密码",
    "Use at least 8 characters." to "请至少输入 8 个字符。",
    "Passwords do not match." to "两次输入的密码不一致。",
    "Restore included backup" to "恢复内置备份",
    "Choose export location" to "选择导出位置",
    "Choose backup file" to "选择备份文件",
    "Encrypt backup" to "加密备份",
    "Use at least 8 characters. This password is not saved and cannot be recovered." to
        "请使用至少 8 个字符。密码不会保存，也无法找回。",
    "Unlock backup" to "解锁备份",
    "Enter the password used when this backup was created. It is not saved." to
        "输入创建此备份时使用的密码，该密码不会被保存。",
    "Decrypt & review" to "解密并检查",
    "Review backup" to "检查备份",
    "The backup was decrypted and validated. Review it before continuing." to "备份已解密并验证，请检查后继续。",
    "Continuing does not restore yet. You will see a final replacement warning." to
        "继续操作暂时不会恢复数据，下一步还会显示最终替换警告。",
    "Cancel restore" to "取消恢复",
    "Created" to "创建时间",
    "Vault entries" to "密码库条目",
    "Total backup size" to "备份总大小",
    "Replace all local data?" to "替换全部本地数据？",
    "This is a complete replacement, not a merge. It permanently replaces your current todos, ledger, notes, settings, attachments, and Vault with the backup." to
        "这是完整替换而不是合并。当前待办、流水、笔记、设置、附件和密码库将永久替换为备份内容。",
    "Once restore starts, it cannot be cancelled. The existing data is left unchanged if validation or preparation fails." to
        "恢复开始后无法取消；如果验证或准备失败，现有数据不会改变。",
    "Back to preview" to "返回预览",
    "Replace & restore" to "替换并恢复",
    "Creating encrypted backup" to "正在创建加密备份",
    "Keep Life Assistant open while the file is written." to "写入文件时请保持 生活助手 运行。",
    "Checking backup" to "正在检查备份",
    "Decrypting and validating everything before making changes." to "正在解密并验证全部内容，完成前不会更改数据。",
    "Restoring backup" to "正在恢复备份",
    "Replacing local data. This cannot be cancelled." to "正在替换本机数据，此操作无法取消。",
    "Password" to "密码",
    "Password vault" to "密码库",
    "Encrypted on this device" to "已在本机加密",
    "Encrypted backup" to "加密备份",
    "Local .tlb file" to "本地 .tlb 文件",
    "Google Drive sync" to "Google Drive 同步",
    "Connected" to "已连接",
    "Not connected" to "未连接",
    "Sync password-encrypted snapshots through Life Assistant's private app folder. The app cannot see other files in your Google Drive." to
        "通过 生活助手 的专用应用文件夹同步密码加密的快照。应用无法查看 Google Drive 中的其他文件。",
    "Each upload creates a new encrypted version. Previous versions are kept; conflicts pause sync." to
        "每次上传都会创建新的加密版本，之前的版本会保留；出现冲突时暂停同步。",
    "Google Drive is optional. Life Assistant works offline by default; enable Drive only when you want encrypted backups shared between Android and Windows." to
        "Google Drive 为可选功能，生活助手 默认离线使用。需要在 Android 和 Windows 之间共享加密备份时，再启用 Drive。",
    "Setup requires Drive API access, package com.ced2711.lifetracker, and the release signing SHA-1 listed in the setup guide. Android and Windows must use the same Google Cloud project and account." to
        "配置需要启用 Drive API、注册包名 com.ced2711.lifetracker，以及配置指南中的发布签名 SHA-1。Android 和 Windows 必须使用同一 Google Cloud 项目和账号。",
    "When Vault contains entries, background sync pauses until you unlock it in Life Assistant. This keeps Vault keys protected by Android." to
        "密码库中有条目时，后台同步会暂停，直到你在 生活助手 中解锁密码库，以便继续由 Android 保护密码库密钥。",
    "Automatic sync" to "自动同步",
    "Runs periodically when a network is available" to "有网络时定期运行",
    "Syncing…" to "正在同步…",
    "Sync now" to "立即同步",
    "Up to date" to "已是最新",
    "Changes not synced yet" to "有更改尚未同步",
    "Sync needs attention" to "同步需要处理",
    "Tap to sync both ways now" to "点击立即双向同步",
    "Tap to open sync settings" to "点击打开同步设置",
    "Last synced" to "上次同步",
    "Never synced" to "尚未同步",
    "Disconnect" to "断开连接",
    "Connecting…" to "正在连接…",
    "Connect Google Drive" to "连接 Google Drive",
    "Choose a sync password. You will enter the same password on Windows or a new device. Google cannot recover it." to
        "设置同步密码。在 Windows 或新设备上需要输入同一密码，Google 无法帮你找回。",
    "Sync password" to "同步密码",
    "Connect" to "连接",
    "Reconnect" to "重新连接",
    "Sync conflict" to "同步冲突",
    "This device and Google Drive both changed since the last sync. Use cloud replaces all local data. Keep this device uploads the current local data as the next encrypted snapshot." to
        "自上次同步后，本机和 Google Drive 都发生了更改。“使用云端”会替换全部本机数据；“保留本机”会将当前本机数据上传为下一个加密快照。",
    "Use cloud" to "使用云端",
    "Keep this device" to "保留本机",
    "Unknown" to "未知",
    "Google Drive connection failed." to "Google Drive 连接失败。",
    "Local sync recovery" to "本机同步恢复副本",
    "Export" to "导出",
    "Could not create the cloud recovery directory." to "无法创建同步恢复副本目录，本机数据未被替换。",
    "Before using a cloud version, Life Assistant keeps an encrypted local recovery copy. Export a copy and use Restore backup to recover it with its original sync password. The 5 most recent copies are kept." to
        "使用云端版本前，生活助手 会保留一份加密的本机恢复副本。导出副本后，可通过“恢复备份”输入创建时的同步密码找回数据。只保留最近 5 份副本。",
    "Hide recovery copies" to "收起恢复副本",
    "Show recovery copies" to "显示恢复副本",
    "Encrypted recovery copy exported. Restore it with the sync password used when it was created." to "加密恢复副本已导出，请使用创建时的同步密码恢复。",
    "Could not export the recovery copy. The private original was kept." to "无法导出恢复副本，应用内的原始副本仍已保留。",
    "Google Drive authorization failed." to "Google Drive 授权失败。",
    "Google Drive authorization returned no result." to "Google Drive 授权未返回结果。",
    "Google Drive connection failed while saving local credentials." to "保存本机凭据时失败，未能完成 Google Drive 连接。",
    "Google Drive could not reach the network." to "Google Drive 无法连接网络。",
    "Google account sign-in failed." to "Google 账号登录失败。",
    "Google Drive permission was not granted." to "未授予 Google Drive 权限。",
    "Google Drive app-data access was not granted." to "未授予 Google Drive 应用数据访问权限。",
    "Open Life Assistant to finish connecting Google Drive." to "请打开 生活助手 完成 Google Drive 连接。",
    "Google Drive changed before upload. Sync again to review it." to "上传前 Google Drive 已发生变化，请重新同步并检查冲突。",
    "Google Drive connection was cancelled." to "已取消 Google Drive 连接。",
    "Vault authentication was cancelled." to "已取消密码库身份验证。",
    "Google Drive disconnected. Local data was kept." to "已断开 Google Drive，本机数据已保留。",
    "Google Drive connected." to "已连接 Google Drive。",
    "Google Drive reconnected." to "已重新连接 Google Drive。",
    "Google Drive sync failed." to "Google Drive 同步失败。",
    "Google Drive sync is disabled." to "Google Drive 同步已关闭。",
    "Already up to date." to "已经是最新状态。",
    "Encrypted backup uploaded." to "加密备份已上传。",
    "Cloud changes restored." to "已恢复云端更改。",
    "Merged changes from your other devices." to "已合并你其他设备上的更改。",
    "Merged changes from your other devices. Some texts were edited on both; both versions were kept." to
        "已合并你其他设备上的更改。有些文字两边都改过，两个版本都保留了。",
    "Other devices kept changing the cloud backup. Sync again in a moment." to "其他设备一直在修改云端备份，请稍后再同步。",
    "The cloud backup uses a different data password. Choose which version to keep." to "云端备份使用了不同的数据密码，请选择保留哪个版本。",
    "Both this device and Google Drive changed." to "本机和 Google Drive 都发生了更改。",
    "Reconnect Google Drive to continue." to "请重新连接 Google Drive 后继续。",
    "The saved sync password is unavailable." to "已保存的同步密码不可用。",
    "Data kept changing while cloud backup was created." to "创建云端备份时数据持续变化，请重试。",
    "Google Drive could not be reached." to "无法连接 Google Drive。",
    "Google Drive timed out." to "Google Drive 连接超时。",
    "The backup password is incorrect or the backup was modified." to "备份密码错误或备份已被修改。",
    "The linked cloud backup is missing. Reconnect to initialize a new backup." to
        "已连接的云备份不存在。请断开后重新连接以创建新备份。",
    "Cloud revision history is invalid." to "云端版本历史无效。",
    "Cloud history is too large to sync safely." to "云端版本历史过大，无法安全同步。",
    "The cloud backup exceeds available local storage." to "本机可用空间不足，无法下载云备份。",
    "Google Drive returned an empty backup." to "Google Drive 返回了空备份。",
    "The downloaded backup size did not match Google Drive metadata." to
        "下载的备份大小与 Google Drive 元数据不一致。",
    "Could not reach Google Drive." to "无法连接 Google Drive。",
    "Google Drive authorization expired or was revoked." to "Google Drive 授权已过期或被撤销。",
    "Unlock Vault in Life Assistant to finish the encrypted cloud sync." to
        "请在 生活助手 中解锁密码库，以完成加密云同步。",
    "Open Life Assistant to reconnect Google Drive." to "请打开 生活助手 重新连接 Google Drive。",
    "Google Drive has conflicting changes. Open Backup & sync to choose a version." to
        "Google Drive 中存在冲突更改。请打开“备份与同步”选择版本。",
    "Life Assistant cloud sync" to "生活助手 云同步",
    "Cloud sync" to "云同步",
    "Google Drive sync needs attention" to "Google Drive 同步需要处理",
    "Cloud changes need review. Sync now to choose which version to keep." to
        "云端更改需要检查。请立即同步并选择要保留的版本。",
    "Automatic sync is waiting for Vault authentication." to "自动同步正在等待密码库身份验证。",
    "Google Drive permission needs to be renewed." to "需要重新授予 Google Drive 权限。",
    "The last automatic sync failed. Try syncing again." to "上次自动同步失败，请重试。",

    // Notes
    "Long-term writing and private files" to "长期记录与私密文件",
    "Open password vault" to "打开密码库",
    "New folder" to "新建文件夹",
    "Rename folder" to "重命名文件夹",
    "Clear search" to "清除搜索",
    "Collapse" to "收起",
    "Expand" to "展开",
    "No notes here yet." to "这里还没有笔记。",
    "No matching notes." to "没有匹配的笔记。",
    "Back to notes" to "返回笔记",
    "Delete note" to "删除笔记",
    "Note saved" to "笔记已保存",
    "Search notes" to "搜索笔记",
    "Folders" to "文件夹",
    "All notes" to "全部笔记",
    "Unfiled" to "未分类",
    "Delete folder" to "删除文件夹",
    "Notes will move to Unfiled. Child folders will move up one level." to
        "其中的笔记将移至“未分类”，子文件夹会上移一级。",
    "Folder name" to "文件夹名称",
    "Select a note or create a new one." to "请选择一条笔记或新建笔记。",
    "New note" to "新建笔记",
    "Edit note" to "编辑笔记",
    "Title (optional)" to "标题（可选）",
    "Derived from the first line if empty" to "留空时取第一行作为标题",
    "Pin this note" to "置顶此笔记",
    "Note" to "备注",
    "Write anything…" to "记录任何内容…",
    "Add files" to "添加文件",
    "Files and images are copied into private app storage. Passwords should use the secure Vault." to
        "文件和图片会复制到应用私有存储；密码建议保存在安全的密码库中。",
    "Delete this note?" to "删除这条笔记？",
    "The note will be removed. Its private files will be cleaned up safely." to
        "笔记将被删除，其私有文件也会被安全清理。",

    // Todo
    "Daily momentum" to "今日进度",
    "No deadlines today — anything you finish is a win." to "今天没有截止事项，完成任何事情都是进步。",
    "Quick add description" to "快速添加待办",
    "What needs to be done?" to "需要做什么？",
    "New task" to "新建待办",
    "Edit task" to "编辑待办",
    "Mark as done" to "标记为已完成",
    "Mark as not done" to "标记为未完成",
    "Categories" to "分类",
    "Filters" to "筛选",
    "Clear filters" to "清除筛选",
    "No active filters" to "没有启用筛选",
    "All categories" to "全部分类",
    "Uncategorized" to "未分类",
    "All tags" to "全部标签",
    "Tag" to "标签",
    "Sort" to "排序",
    "1 category selected" to "已选择 1 个分类",
    "Manage tags..." to "管理标签…",
    "Deadline" to "截止日期",
    "Priority" to "优先级",
    "Manual" to "手动排序",
    "Search, tags, priority, category, and sorting" to "搜索、标签、优先级、分类与排序",
    "Description *" to "说明 *",
    "Description is required" to "必须填写说明",
    "Description is required." to "必须填写说明。",
    "If blank, it is derived from the description." to "留空时根据说明自动生成。",
    "Deadline (US date)" to "截止日期（美式格式）",
    "Day, MM/DD, or MM/DD/YYYY" to "日期、月/日或月/日/年",
    "15, 8/15, or 8/15/2026" to "15、8/15 或 8/15/2026",
    "A passed day or month/day rolls forward automatically." to "已过去的日期会自动顺延。",
    "Enter a valid deadline" to "请输入有效的截止日期",
    "Time" to "时间",
    "9:30 AM or 21:30" to "上午 9:30 或 21:30",
    "Enter a valid US date: day, M/d, or M/d/yyyy." to "请输入有效的美式日期：日、月/日或月/日/年。",
    "Enter a valid time such as 9:30 AM or 21:30." to "请输入有效时间，例如上午 9:30 或 21:30。",
    "A time requires a deadline date." to "设置时间前必须先设置截止日期。",
    "Repeating tasks require a deadline." to "重复待办必须设置截止日期。",
    "Repeat interval must be at least 1." to "重复间隔至少为 1。",
    "Enter a valid repeat end date." to "请输入有效的重复结束日期。",
    "Repeat end date cannot be before the deadline." to "重复结束日期不能早于截止日期。",
    "Low" to "低",
    "Medium" to "中",
    "High" to "高",
    "Urgent" to "紧急",
    "Tags" to "标签",
    "Comma-separated" to "用逗号分隔",
    "work, errands" to "工作、跑腿",
    "Separate tags with commas. Type to find existing tags." to "使用逗号分隔标签，输入文字可查找现有标签。",
    "Reminders" to "提醒",
    "Subtasks" to "子任务",
    "One subtask per line" to "每行一个子任务",
    "Apply changes to" to "将更改应用到",
    "Only this occurrence" to "仅此一次",
    "This and future occurrences" to "此次及之后",
    "Choose This and future occurrences to change the repeat rule. Other task fields still apply only to this occurrence." to
        "选择“此次及之后”可修改重复规则；其他待办字段仍只应用于本次。",
    "Every" to "每隔",
    "Days" to "天",
    "Weeks" to "周",
    "Months" to "月",
    "Years" to "年",
    "Repeating" to "重复",
    "Repeats" to "重复中",
    "Add recurrence" to "添加重复",
    "Deadline has a time" to "截止日期包含时间",
    "Add deadline time" to "添加截止时间",
    "Choose deadline date" to "选择截止日期",
    "Choose repeat end date" to "选择重复结束日期",
    "Repeat end date (optional)" to "重复结束日期（可选）",
    "Attachments" to "附件",
    "Up to 10 files per task, 25 MB each, and 128 MB total. New files are copied after the task is saved." to
        "每项待办最多 10 个文件，每个 25 MB，总计 128 MB；保存待办后会复制新文件。",
    "Task saved" to "待办已保存",
    "The task details are saved. Files are being copied now." to "待办内容已保存，正在复制文件。",
    "The task details are already saved, but one or more files were not copied. Details are locked so later edits cannot be silently skipped. Remove any unavailable file, then retry." to
        "待办内容已保存，但一个或多个文件未复制。内容已暂时锁定，以免后续修改被跳过；请移除无法使用的文件后重试。",
    "No files remain to copy." to "没有需要复制的文件。",
    "Selected file" to "已选文件",
    "Manage categories" to "管理分类",
    "Deleting a category keeps its tasks in Uncategorized and moves child categories up one level." to
        "删除分类后，其中的待办会保留在“未分类”，子分类会上移一级。",
    "Category name" to "分类名称",
    "No parent" to "无上级分类",
    "Add category" to "添加分类",
    "No categories yet." to "还没有分类。",
    "No active tasks match these filters." to "没有符合当前筛选条件的待办。",
    "No completed tasks match these filters." to "没有符合当前筛选条件的已完成待办。",
    "Tasks will become Uncategorized. Child categories will move to this category's parent." to
        "待办会变为“未分类”，子分类会移到此分类的上一级。",
    "Manage tags" to "管理标签",
    "Changes apply to existing tasks and repeating rules, including future occurrences." to
        "更改会应用到现有待办和重复规则，包括未来实例。",
    "No tags yet." to "还没有标签。",
    "Tag name" to "标签名称",
    "A tag name cannot contain commas." to "标签名称不能包含逗号。",
    "This removes the tag from all tasks and repeating rules." to "这会从所有待办和重复规则中移除该标签。",
    "Complete task?" to "完成待办？",
    "Task + subtasks" to "待办和子任务",
    "Task only" to "仅待办",
    "Delete repeating task?" to "删除重复待办？",
    "Move up" to "上移",
    "Move down" to "下移",
    "Moves follow the visible filtered list; hidden tasks keep their relative order." to
        "移动顺序以当前可见筛选列表为准，隐藏待办保持相对顺序。",

    // Ledger
    "Entries" to "流水",
    "Recurring" to "定期流水",
    "Active" to "进行中",
    "Stopped" to "已停止",
    "starts" to "开始",
    "ends" to "结束",
    "Statistics" to "统计",
    "Quick entry" to "快速记账",
    "Recent entries" to "最近流水",
    "No entries yet" to "还没有流水",
    "New entry" to "新建流水",
    "Edit entry" to "编辑流水",
    "Choose date" to "选择日期",
    "Choose time" to "选择时间",
    "Choose start date" to "选择开始日期",
    "Choose end date" to "选择结束日期",
    "Enter a valid time such as 9:30 AM or 21:30" to "请输入有效时间，例如上午 9:30 或 21:30",
    "Replacement schedules must start tomorrow or later" to "替换计划必须从明天或之后开始",
    "End date cannot be before the effective date" to "结束日期不能早于生效日期",
    "An existing entry can only start repeating today or earlier. Create a new recurring entry for a future start." to
        "现有流水只能从今天或更早日期开始重复；如需未来开始，请新建定期流水。",
    "Receipts can be added after the first scheduled entry is generated." to "第一笔计划流水生成后才能添加收据。",
    "Details" to "详情",
    "Amount" to "金额",
    "Income" to "收入",
    "Expense" to "支出",
    "Date" to "日期",
    "Merchant" to "商家/收款方",
    "travel, work" to "旅行、工作",
    "Repeat" to "重复",
    "Create independent entries on schedule" to "按计划生成独立流水",
    "This occurrence is independent. Stop its schedule from the Recurring page." to
        "本次流水相互独立，可在“定期流水”页面停止计划。",
    "End date" to "结束日期",
    "End date cannot be before the entry date" to "结束日期不能早于流水日期",
    "Required · up to $999,999,999.99 · max 2 decimal places" to
        "必填 · 最高 $999,999,999.99 · 最多两位小数",
    "Up to $999,999,999.99 · max 2 decimal places" to
        "最高 $999,999,999.99 · 最多两位小数",
    "25 MB each, 128 MB total" to "每个 25 MB，总计 128 MB",
    "Recurring entries" to "定期流水",
    "No recurring entries" to "没有定期流水",
    "Scheduled entries are generated independently. Stopping a schedule keeps existing entries." to
        "计划流水会独立生成；停止计划不会删除已有流水。",
    "Edit rule" to "编辑规则",
    "Stop" to "停止",
    "Daily" to "每天",
    "Weekly" to "每周",
    "Monthly" to "每月",
    "Yearly" to "每年",
    "Edit recurring rule" to "编辑定期流水规则",
    "The old schedule and every existing entry are preserved. The replacement starts tomorrow or later." to
        "旧计划和所有已有流水都会保留，新计划从明天或之后开始。",
    "Apply changes from" to "从此日期开始应用",
    "Repeat interval" to "重复间隔",
    "Generated entries use 12:00 AM (00:00)." to "生成的流水时间为 00:00。",
    "Trend" to "趋势",
    "Ledger income and expense trend" to "流水收支趋势图",
    "No period selected. Use the previous or next period action to inspect values." to
        "未选择时间段。使用上一个或下一个时间段操作查看数值。",
    "Previous period" to "上一个时间段",
    "Next period" to "下一个时间段",
    "No activity in this period" to "此期间没有流水",
    "Income / expense" to "收入 / 支出",
    "Expenses" to "支出",
    "Net" to "净额",
    "Largest expense" to "最大支出",
    "Average daily spending" to "日均支出",
    "No description" to "无说明",
    "No expenses" to "无支出",
    "Start date" to "开始日期",
    "Enter a valid day, month/day, or month/day/year" to "请输入有效的日、月/日或月/日/年",
    "7 days" to "7 天",
    "30 days" to "30 天",
    "This month" to "本月",
    "This year" to "今年",
    "Custom" to "自定义",

    // Calendar
    "Month" to "月",
    "Week" to "周",
    "Day" to "日",
    "Agenda" to "日程",
    "Completed" to "已完成",
    "No todos or ledger entries for this day." to "当天没有待办或流水。",
    "Nothing scheduled this month" to "本月没有安排",
    "Todos" to "待办",
    "No todos" to "没有待办",
    "Ledger entries" to "流水",
    "No ledger entries" to "没有流水",

    // Settings
    "Security" to "安全",
    "Data" to "数据",
    "Appearance" to "外观",
    "Theme" to "主题",
    "Accent color" to "强调色",
    "Teal" to "青绿",
    "Blue" to "蓝色",
    "Violet" to "紫色",
    "Rose" to "玫红",
    "Orange" to "橙色",
    "Green" to "绿色",
    "Regional preferences" to "区域设置",
    "UI language" to "界面语言",
    "English" to "English",
    "Simplified Chinese" to "简体中文",
    "Week starts on" to "每周起始日",
    "Sunday" to "星期日",
    "Monday" to "星期一",
    "Time format" to "时间格式",
    "12-hour" to "12 小时制",
    "24-hour" to "24 小时制",
    "Date format" to "日期格式",
    "Quick add fields" to "快速添加字段",
    "Description only" to "仅说明",
    "All optional fields" to "全部可选字段",
    "Category" to "分类",
    "Notifications" to "通知",
    "Allow reminders and due-date notifications" to "允许提醒和截止日期通知",
    "Notification permission was denied. Notifications remain off." to "通知权限被拒绝，通知将保持关闭。",
    "Default reminders" to "默认提醒",
    "All-day reminder time" to "全天待办提醒时间",
    "About" to "关于",
    "Life Assistant" to "生活助手",
    "Life Assistant by ced2711" to "生活助手 · ced2711",
    "View license, notices, and source" to "查看许可证、声明与源码",
    "No browser is available to open the source link." to "没有可用于打开源码链接的浏览器。",
    "The source link could not be opened." to "无法打开源码链接。",
    "About Life Assistant" to "关于生活助手",
    "This software is provided as-is, without warranty of any kind. Use it at your own risk." to "本软件按现状提供，不作任何担保。使用风险由使用者自行承担。",
    "License" to "许可证",
    "Additional permissions" to "附加许可",
    "Notices" to "版权及第三方声明",
    "View source" to "查看源码",
    "Automatically add these to new todos that have a deadline." to "自动为有截止日期的新待办添加这些提醒。",
    "Choose optional fields shown below the quick add description." to "选择快速添加说明下方显示的可选字段。",
    "At due time" to "到期时",
    "1 hour before" to "提前 1 小时",
    "1 day before" to "提前 1 天",
    "3 days before" to "提前 3 天",
    "1 week before" to "提前 1 周",
    "+ Custom reminder" to "+ 自定义提醒",
    "Reminder value" to "提醒数值",
    "Hours" to "小时",
    "This reminder is already selected." to "已选择此提醒。",
    "System picker" to "系统选择器",

    // Vault
    "Confirm your identity" to "确认身份",
    "Waiting for secure device authentication." to "正在等待设备安全验证。",
    "Vault unavailable" to "密码库不可用",
    "Try again" to "重试",
    "Lock vault" to "锁定密码库",
    "Add vault entry" to "添加密码库条目",
    "Vault locked" to "密码库已锁定",
    "Create your vault" to "创建密码库",
    "Your accounts and passwords are encrypted on this device. Unlocking requires your fingerprint, face, PIN, pattern, or password." to
        "账号和密码已在本机加密。解锁时需要使用指纹、面容、PIN、图案或密码。",
    "Set a secure screen lock in Android Settings before using the vault." to
        "使用密码库前，请先在 Android 设置中启用安全屏幕锁定。",
    "Unlock" to "解锁",
    "Create vault" to "创建密码库",
    "No vault entries yet. Use Add to store an account." to "密码库还没有条目，请使用“添加”保存账号。",
    "No entries match your search." to "没有匹配搜索条件的条目。",
    "Enable fingerprint unlock" to "启用指纹解锁",
    "Use your enrolled fingerprint next time, with screen lock as a fallback." to
        "下次可使用已录入的指纹，并以屏幕锁定作为备用方式。",
    "Enable" to "启用",
    "Upgrade vault security" to "升级密码库安全性",
    "Add support for the current Android authentication system on this device." to
        "为本设备当前的 Android 身份验证系统添加支持。",
    "Upgrade" to "升级",
    "Back to entries" to "返回条目",
    "Delete entry" to "删除条目",
    "Copy account" to "复制账号",
    "Working…" to "处理中…",
    "Hide" to "隐藏",
    "Show" to "显示",
    "Use screen lock" to "使用屏幕锁定",
    "Confirm your fingerprint" to "确认指纹",
    "Confirm your screen lock to continue." to "确认屏幕锁定以继续。",
    "Confirm your screen lock to permanently reset the vault." to "确认屏幕锁定以永久重置密码库。",
    "Confirm to permanently reset the vault" to "确认永久重置密码库",
    "Unlock Vault" to "解锁密码库",
    "Create Vault" to "创建密码库",
    "Authenticate backup" to "验证备份",
    "Reset Vault" to "重置密码库",
    "Delete entry?" to "删除条目？",
    "Reset vault?" to "重置密码库？",
    "Reset vault" to "重置密码库",
    "Android will ask you to confirm your identity, then permanently delete every vault entry and encryption key. This cannot be undone." to
        "Android 会要求验证身份，随后永久删除所有密码库条目和加密密钥，此操作无法撤销。",
    "Open Android security settings" to "打开 Android 安全设置",
    "Account or username" to "账号或用户名",
    "Label" to "名称",
    "Website" to "网站",
    "Enter at least one field. Passwords stay encrypted on this device." to "请至少填写一项，密码会加密保存在本机。",
    "Select an entry or use Add." to "请选择一个条目或点击添加。",
    // Diary
    "Diary" to "日记",
    "Diary entries" to "日记",
    "Previous day" to "前一天",
    "Next day" to "后一天",
    "Delete diary entry" to "删除日记",
    "How was your day?" to "今天过得怎么样？",
    "Saved automatically. Clearing the text removes the entry." to "自动保存。清空内容即删除这篇日记。",
    "No diary entries yet" to "还没有日记",
    "Delete this diary entry?" to "删除这篇日记？",
    "No diary entry" to "没有日记",
    "Open diary" to "查看日记",
    "Write diary" to "写日记",
    // Confessional
    "Confessional" to "告解室",
    "Say what you need to say. Burn it to let it go for good, or seal it on this device. " +
        "Nothing here is backed up or synced, and screenshots are blocked." to
        "把想说的话写下来。焚烧它，让它彻底消失；或者封存在这台设备上。这里的内容不会备份或同步，也无法截屏。",
    "Write it down…" to "写下来……",
    "Burn" to "焚烧",
    "Seal" to "封存",
    "Burned. It's gone." to "已焚烧，它消失了。",
    "Sealed on this device." to "已封存在这台设备上。",
    "Open sealed confessions" to "打开封存的告解",
    "Sealed confessions" to "封存的告解",
    "Burn all" to "全部焚烧",
    "Burn every sealed confession?" to "焚烧所有封存的告解？",
    "They will be permanently deleted from this device." to "它们将从这台设备上永久删除。",
    "Something went wrong" to "出错了",
    "The confessional is full. Burn some sealed entries first." to "告解室已满，请先焚烧一些封存的内容。",
    // App lock and menu
    "Unlock Life Assistant" to "解锁生活助手",
    "Life Assistant is locked" to "生活助手已锁定",
    "Use your fingerprint, face or screen lock to continue." to "请使用指纹、面部或锁屏密码继续。",
    "This device has no screen lock, so the app lock cannot verify you. Set a screen lock to protect the app." to
        "此设备没有设置锁屏，软件锁无法验证你的身份。请设置锁屏来保护应用。",
    "App lock" to "软件锁",
    "Ask for fingerprint, face or screen lock when opening the app" to "打开应用时需要指纹、面部或锁屏密码验证",
    "Lock after leaving the app" to "离开应用后锁定",
    "Immediately" to "立即",
    "After 1 minute" to "1 分钟后",
    "After 5 minutes" to "5 分钟后",
    "Set a screen lock on this device first." to "请先为此设备设置锁屏。",
    "Turn on app lock" to "开启软件锁",
    "Turn off app lock" to "关闭软件锁",
    "Menu" to "菜单",
    "Modules in menu" to "菜单显示的功能",
    "All modules" to "全部功能",
    "Hidden modules keep their data. At least one module stays visible." to "隐藏的功能会保留数据，至少保留一个功能可见。",
    // Windows-only texts live here too: the "(.+) to (.+)" range rule would otherwise rewrite
    // any sentence containing " to " before the desktop table is consulted.
    "Confirm" to "确认",
    "The password is incorrect." to "密码不正确。",
    "Enter your data password to continue." to "请输入数据密码继续。",
    "Ask for the data password when returning to the app" to "回到应用时要求输入数据密码",
    "Applies even when Windows remembers the password." to "即使 Windows 已记住密码也会生效。",
    "Enter your data password to turn off the app lock." to "请输入数据密码以关闭软件锁。",
    "Delete this todo?" to "删除这个待办？",
    "Say what you need to say. Burn it to let it go for good, or seal it on this PC. " +
        "Sealed words are protected by Windows and never exported or synced." to
        "把想说的话写下来。焚烧它，让它彻底消失；或者封存在这台电脑上。封存的内容受 Windows 保护，不会导出或同步。",
    "Sealed on this PC." to "已封存在这台电脑上。",
    "Enter your data password to read sealed confessions." to "请输入数据密码查看封存的告解。",
    "They will be permanently deleted from this PC." to "它们将从这台电脑上永久删除。",
    // Cloud sync providers
    "GitHub sync" to "GitHub 同步",
    "Syncs a few seconds after each change, when you open the app, and about every 15 minutes in the background." to
        "每次修改后几秒内同步，打开应用时同步，后台约每 15 分钟同步一次。",
    "Syncs a few seconds after each change, when the window is focused, and every 2 minutes while Life Assistant is open." to
        "每次修改后几秒内同步，切回窗口时同步，生活助手打开期间每 2 分钟同步一次。",
    "The cloud backup has conflicting changes. Open Backup & sync to choose a version." to
        "云端备份有冲突的修改，请打开“备份与同步”选择要保留的版本。",
    "Delete this stopped schedule?" to "删除这个已停止的定期流水？",
    "It disappears from Recurring. Entries it already created stay in your ledger." to
        "它会从“定期流水”中移除，已经生成的流水记录会保留。",
    "View or edit ledger entry" to "查看或编辑流水",
    "Local .tlb, Google Drive or GitHub" to "本地 .tlb、Google Drive 或 GitHub",
    "Connect GitHub" to "连接 GitHub",
    "Sync password-encrypted snapshots between Android and Windows through Google Drive's " +
        "private app folder or a private GitHub repository. Cloud sync is optional; " +
        "Life Assistant works offline by default." to
        "通过 Google Drive 的私有应用文件夹或 GitHub 私有仓库，在 Android 和 Windows 之间同步用密码加密的快照。云同步是可选的，生活助手默认离线使用。",
    "Each upload creates a new encrypted version. The 10 most recent versions are kept; " +
        "conflicts pause sync." to "每次上传都会生成一个新的加密版本，只保留最近 10 个版本；出现冲突时会暂停同步。",
    "Each upload creates a new encrypted version. The 10 most recent versions are kept; conflicts pause sync until you resolve them." to
        "每次上传都会生成一个新的加密版本，只保留最近 10 个版本；出现冲突时会暂停同步，直到你处理冲突。",
    "Sign in with GitHub; that is all. Use the same GitHub account and the same " +
        "sync password on your phone and your PC." to
        "用 GitHub 登录就好。手机和电脑上要用同一个 GitHub 账号和同一个同步密码。",
    "Google Drive (coming soon)" to "Google Drive（即将推出）",
    "Choose a sync password. If another device already syncs, enter its password " +
        "(on Windows this is your data password). Nobody can recover it for you." to
        "设置一个同步密码。如果另一台设备已经在同步，请输入它用的密码（Windows 上就是你的数据密码）。任何人都无法帮你找回它。",
    "GitHub Client ID" to "GitHub 客户端 ID",
    "Private repository (owner/name, optional)" to "私有仓库（所有者/名称，可选）",
    "Approve on GitHub" to "在 GitHub 上授权",
    "The code is copied. Open GitHub, paste it, and choose Authorize. Then come back; this screen continues by itself." to
        "代码已复制。打开 GitHub，粘贴代码，然后点 Authorize。完成后回到这里，此页面会自动继续。",
    "GitHub opened in your browser and this code is already copied. Paste it there, then choose Authorize. This window continues by itself." to
        "已在浏览器中打开 GitHub，代码也已复制。在那里粘贴代码，然后点 Authorize。此窗口会自动继续。",
    "Copy code" to "复制代码",
    "Open GitHub" to "打开 GitHub",
    "Get sign-in code" to "获取登录代码",
    "No browser is available to open GitHub." to "没有可以打开 GitHub 的浏览器。",
    "Encrypted snapshots are stored in Life Assistant's private Google Drive app folder or a private GitHub repository. Nothing else in those accounts is read." to
        "加密快照保存在生活助手在 Google Drive 中的私有应用文件夹或 GitHub 私有仓库中，不会读取这些账号里的其他内容。",
    "This build has no built-in GitHub sign-in. Enter the Client ID of a GitHub OAuth App with " +
        "Device Flow on. Leave the repository empty to create a private life-assistant-data repository." to
        "此版本没有内置 GitHub 登录。请输入一个开启了 Device Flow 的 GitHub OAuth App 的客户端 ID。仓库留空会自动创建私有仓库 life-assistant-data。",
    "Your data password encrypts every backup before upload. Use the same password on every device." to
        "每个备份在上传前都会用你的数据密码加密。所有设备要使用同一个密码。",
    "Desktop OAuth client secret" to "Desktop OAuth 客户端密钥",
    "Google requires the client secret for Desktop clients. It is protected by Windows DPAPI and is application configuration, not a replacement for PKCE." to
        "Google 要求桌面客户端提供客户端密钥。它由 Windows DPAPI 保护，属于应用配置，不能代替 PKCE。",
    "GitHub connected." to "已连接 GitHub。",
    "GitHub reconnected." to "已重新连接 GitHub。",
    "GitHub connection cancelled." to "已取消连接 GitHub。",
    "GitHub disconnected. Local data was kept." to "已断开 GitHub，本机数据已保留。",
    "Cloud sync failed." to "云同步失败。",
    "Cloud sync is disabled." to "云同步已关闭。",
    "Both this device and the cloud backup changed." to "本设备和云端备份都有改动。",
    "This PC and the cloud backup both changed." to "这台电脑和云端备份都有改动。",
    "The cloud backup changed before upload. Sync again to review it." to "上传前云端备份发生了变化，请再次同步以查看。",
    "The cloud backup changed before upload. Review the refreshed conflict." to "上传前云端备份发生了变化，请查看更新后的冲突。",
    "The cloud backup changed while the conflict choice was open. Review the refreshed conflict." to
        "处理冲突期间云端备份发生了变化，请查看更新后的冲突。",
    "The cloud service could not be reached." to "无法连接云服务。",
    "The cloud service timed out." to "云服务响应超时。",
    "GitHub is not connected." to "尚未连接 GitHub。",
    "GitHub is not connected. Reconnect GitHub." to "尚未连接 GitHub，请重新连接。",
    "GitHub sign-in expired or was revoked. Reconnect GitHub." to "GitHub 登录已过期或已被撤销，请重新连接 GitHub。",
    "GitHub's rate limit was reached. Try again in a few minutes." to "已达到 GitHub 的请求频率上限，请几分钟后再试。",
    "Could not reach GitHub." to "无法连接 GitHub。",
    "GitHub kept changing while saving. Sync again." to "保存期间 GitHub 上的数据一直在变化，请再次同步。",
    "The GitHub code expired. Start again." to "GitHub 代码已过期，请重新开始。",
    "GitHub authorization was cancelled." to "GitHub 授权已取消。",
    "Device flow is off for this GitHub app. Turn on \"Enable Device Flow\" in its settings." to
        "这个 GitHub 应用没有开启 Device Flow，请在它的设置中打开“Enable Device Flow”。",
    "GitHub did not allow creating the private repository life-assistant-data. Create it yourself on GitHub, then connect again." to
        "GitHub 不允许创建私有仓库 life-assistant-data。请在 GitHub 上手动创建后再连接。",
    "GitHub did not recognize this Client ID." to "GitHub 无法识别这个客户端 ID。",
    "Install the GitHub App on a private repository first." to "请先把 GitHub App 安装到一个私有仓库上。",
    "Use a private repository. Backups are encrypted, but their history would be public." to
        "请使用私有仓库。备份虽然是加密的，但它们的历史记录会公开。",
    "Enter the repository as owner/name." to "请按“所有者/名称”的格式输入仓库。",
    "Enter a valid GitHub Client ID." to "请输入有效的 GitHub 客户端 ID。",
    "The backup is larger than GitHub's 100 MB file limit." to "备份超过了 GitHub 单个文件 100 MB 的上限。",
    "The Google Drive API is not enabled in this Google Cloud project. Enable it, then sync again." to
        "这个 Google Cloud 项目没有启用 Google Drive API，请启用后再同步。",
    "Google Drive is limiting requests. Try again in a few minutes." to "Google Drive 正在限制请求，请几分钟后再试。",
    "Your Google Drive storage is full." to "你的 Google Drive 存储空间已满。",
    "Google requires the Desktop OAuth client secret. Enter it from the Google Cloud Console and try again." to
        "Google 要求提供 Desktop OAuth 客户端密钥，请从 Google Cloud Console 复制后重试。",
    "The GitHub backup no longer exists." to "这个 GitHub 备份已不存在。",
)
