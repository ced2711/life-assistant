package com.ced2711.lifetracker.ui.localization

/** Simplified Chinese for the Ledger screens: English text as written in the code to its translation. */
internal val zhLedger: Map<String, String> = mapOf(
    // Period and totals
    "Year" to "年",
    "All time" to "全部时间",
    "Yesterday" to "昨天",

    // Entries
    "Where or what" to "在哪儿 / 买了什么",
    "Type an amount above and save it, or tap + for an entry with all details." to
        "在上面输入金额并保存，或点 + 新建一条带完整信息的流水。",

    // Editor
    "Never" to "不重复",
    "Until (optional)" to "截止日期（可选）",
    "Attach" to "添加附件",
    "Added when you save" to "保存时添加",
    "Attachments require a generated ledger entry." to "要添加附件，需要先生成这笔流水。",
    "Enter a whole number from 1 to 2,147,483,647" to "请输入 1 到 2,147,483,647 之间的整数",

    // Statistics
    "Tap a bar for its numbers" to "点按柱形查看具体数字",
    "Spending by tag" to "按标签统计支出",
    "Untagged" to "无标签",

    // Recurring
    "Stop this schedule?" to "停止这个定期流水？",
    "No new entries will be added. Entries it already created stay." to "之后不会再自动添加流水，已经生成的流水会保留。",
)
