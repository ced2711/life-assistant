package com.ced2711.lifetracker.cloudsync

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class GoogleDriveBackupStoreTest {
    private fun row(id: String, time: Long, parents: String = "") = """
        {"id":"$id","name":"backup.tlb","size":"4","modifiedTime":"2026-09-18T00:00:00Z","version":"1",
        "appProperties":{"protocol":"$CLOUD_SYNC_PROTOCOL","createdAt":"$time","deviceId":"test-device",
        "contentFingerprint":"${"a".repeat(64)}"$parents}}
    """.trimIndent()

    @Test fun `pagination retains old fork and server order despite wrong device clock`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"files":[${row("new", 1)}],"nextPageToken":"page2"}"""))
            server.enqueue(MockResponse().setBody("""{"files":[${row("old", 99999)}]}"""))
            val store = GoogleDriveBackupStore({ "test-token" }, apiBaseUrl = server.url("/"))
            assertEquals(listOf("new", "old"), store.listRevisions(1).map { it.fileId })
            assertEquals("Bearer test-token", server.takeRequest().getHeader("Authorization"))
            assertEquals("page2", server.takeRequest().requestUrl!!.queryParameter("pageToken"))
        }
    }

    @Test fun `failed oversized download preserves destination and removes partial file`() = runBlocking {
        MockWebServer().use { server ->
            val directory = Files.createTempDirectory("drive-download-test").toFile()
            try {
                val destination = java.io.File(directory, "target.tlb").apply { writeText("kept") }
                server.enqueue(MockResponse().setBody("too many bytes"))
                val store = GoogleDriveBackupStore({ "test-token" }, apiBaseUrl = server.url("/"))
                val revision = CloudRevision("file", "backup.tlb", 1, "device", null, "a".repeat(64), 1, "", 4)
                try { store.downloadRevision(revision, destination); fail("Expected bounded download failure") }
                catch (_: CloudTransportException) { }
                assertEquals("kept", destination.readText())
                assertEquals(listOf("target.tlb"), directory.listFiles()!!.map { it.name })
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun `merge parents survive upload metadata round trip`() = runBlocking {
        MockWebServer().use { server ->
            val file = Files.createTempFile("drive-upload-test", ".tlb").toFile().apply { writeText("data") }
            try {
                server.enqueue(MockResponse().setBody(row("resolved", 1, ",\"mergeParent0\":\"a\",\"mergeParent1\":\"b\"")))
                val store = GoogleDriveBackupStore({ "test-token" }, apiBaseUrl = server.url("/"))
                val uploaded = store.uploadRevision(file, NewCloudRevision(1, "device", "a", "a".repeat(64), listOf("a", "b")))
                assertEquals(listOf("a", "b"), uploaded.mergedRevisionIds)
                val body = server.takeRequest().body.readUtf8()
                assertTrue(body.contains("\"mergeParent0\":\"a\""))
                assertTrue(body.contains("appDataFolder"))
            } finally { file.delete() }
        }
    }

    @Test fun `unauthorized response surfaces consent failure without response body secrets`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("private server detail"))
            try {
                GoogleDriveBackupStore({ "test-token" }, apiBaseUrl = server.url("/")).listRevisions()
                fail("Expected authorization failure")
            } catch (error: CloudAuthorizationException) {
                assertFalse(error.message.orEmpty().contains("private server detail"))
            }
        }
    }
}
