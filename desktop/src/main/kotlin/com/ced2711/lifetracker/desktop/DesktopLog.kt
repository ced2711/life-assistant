package com.ced2711.lifetracker.desktop

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A short record of things that went wrong out of sight, such as the AI assistants' door not
 * opening, in `life-assistant.log` in the app's folder. The installed app has no console, so
 * without it such failures leave no trace. Only the newest part of the file is kept.
 */
object DesktopLog {
    fun file(appDirectory: File) = File(appDirectory, "life-assistant.log")

    @Synchronized
    fun write(appDirectory: File, message: String, error: Throwable? = null) {
        val line = buildString {
            append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            append("  ").append(message)
            if (error != null) append(": ").append(error.javaClass.name).append(": ").append(error.message.orEmpty())
        }.replace('\n', ' ')
        System.err.println(line)
        runCatching {
            val log = file(appDirectory)
            log.parentFile?.mkdirs()
            if (log.length() > MAX_BYTES) log.writeText(log.readText().takeLast(MAX_BYTES / 2).substringAfter('\n'))
            log.appendText(line + "\n")
        }
    }

    private const val MAX_BYTES = 256 * 1024
}
