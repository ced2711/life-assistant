package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.cloudsync.GitHubDeviceAuthorization
import com.ced2711.lifetracker.data.backup.mergeSnapshots
import com.ced2711.lifetracker.domain.model.AttachmentOwnerType
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * `Life Assistant.exe --self-test <report file>` checks a packaged build without opening a window:
 * the parts that shrinking or a trimmed Java runtime could break (device encryption, the
 * encrypted data file with an attachment, the UI thread, merging and the GitHub connection). It
 * writes one line per check to the report and exits with 0 when everything passed.
 */
object DesktopSelfTest {
    fun run(report: File): Boolean {
        val lines = mutableListOf<String>()
        var passed = true
        fun check(name: String, block: () -> Unit) {
            try {
                block()
                lines += "OK   $name"
            } catch (failure: Throwable) {
                passed = false
                lines += "FAIL $name: $failure"
            }
        }
        val root = Files.createTempDirectory("life-assistant-self-test").toFile()
        try {
            check("device protection") {
                val data = "self-test".encodeToByteArray()
                val protection = DesktopPlatform.protection
                require(protection.unprotect(protection.protect(data)).contentEquals(data)) { "round trip differs" }
            }
            check("encrypted data file") {
                runBlocking {
                    val password = "self-test-password"
                    val attachment = File(root, "note.txt").apply { writeText("attachment bytes") }
                    DesktopDataStore(File(root, "data")).apply {
                        require(open(password.toCharArray())) { "could not create" }
                        require(addCategory("Self test")) { "could not save" }
                        val noteId = saveNote(null, null, "Self test", "body", false) ?: error("note not saved")
                        require(attachFile(AttachmentOwnerType.NOTE, noteId, attachment)) { "could not attach" }
                        close()
                    }
                    DesktopDataStore(File(root, "data")).apply {
                        require(open(password.toCharArray())) { "could not reopen" }
                        val snapshot = requireNotNull(currentSnapshot())
                        require(snapshot.categories.any { it.name == "Self test" }) { "category lost" }
                        val stored = requireNotNull(attachmentFile(snapshot.attachments.single().id)) { "attachment lost" }
                        require(stored.readText() == "attachment bytes") { "attachment changed" }
                        close()
                    }
                }
            }
            check("ui thread") { runBlocking { withContext(Dispatchers.Main) { } } }
            check("merge") {
                val snapshot = DesktopDataStore.defaultSnapshot(1_000)
                mergeSnapshots(snapshot, snapshot, snapshot, 2_000)
            }
            check("github connection") {
                val clientId = DesktopCloudDefaults.builtIn.gitHubClientId.ifBlank { error("no built-in GitHub sign-in") }
                runBlocking { GitHubDeviceAuthorization(clientId).start() }
            }
        } finally {
            root.deleteRecursively()
        }
        report.writeText(lines.joinToString("\n") + "\n")
        return passed
    }
}
