package com.ced2711.lifetracker.cloudsync

import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Talks to real GitHub. Runs only when LA_GITHUB_TEST_REPO (owner/name of a disposable private
 * repository) and LA_GITHUB_TEST_TOKEN are set; ordinary builds skip it.
 */
class GitHubLiveIntegrationTest {
    private val repository = System.getenv("LA_GITHUB_TEST_REPO")
    private val token = System.getenv("LA_GITHUB_TEST_TOKEN")
    private val directory: File = Files.createTempDirectory("github-live").toFile()

    private fun store() = GitHubBackupStore({ token }, GitHubRepository.parse(repository), branch = BRANCH)

    private fun revision(createdAt: Long, device: String, base: String? = null, merged: List<String> = emptyList()) =
        NewCloudRevision(
            createdAt = createdAt,
            deviceId = device,
            baseRevisionId = base,
            contentFingerprint = "%064x".format(createdAt),
            mergedRevisionIds = merged,
        )

    private fun payload(size: Int) = ByteArray(size).also(SecureRandom()::nextBytes)

    @Test
    fun realGitHubStoresListsDownloadsRacesAndDeletes() = runBlocking {
        assumeTrue(repository != null && token != null)
        val store = store()
        store.listRevisions().forEach { store.deleteRevision(it.fileId) }
        assertEquals(emptyList<CloudRevision>(), store.listRevisions())

        val firstBytes = payload(300_000)
        val first = store.uploadRevision(File(directory, "a.tlb").apply { writeBytes(firstBytes) }, revision(1, "device-a"))
        val second = store.uploadRevision(
            File(directory, "b.tlb").apply { writeBytes(payload(1_000)) },
            revision(2, "device-a", base = first.fileId),
        )
        val listed = store.listRevisions()
        assertEquals(listOf(second.fileId, first.fileId), listed.map(CloudRevision::fileId))
        assertEquals(listOf(second.fileId), cloudRevisionHeads(listed).map(CloudRevision::fileId))

        val downloaded = File(directory, "downloaded.tlb")
        store().downloadRevision(listed.last(), downloaded)
        assertArrayEquals("bytes survive the round trip through GitHub", firstBytes, downloaded.readBytes())

        // Two devices publish on the same base at the same moment: both must survive.
        val raced = listOf("device-b", "device-c").mapIndexed { index, device ->
            async(kotlinx.coroutines.Dispatchers.IO) {
                store().uploadRevision(
                    File(directory, "$device.tlb").apply { writeBytes(payload(2_000)) },
                    revision(10L + index, device, base = second.fileId),
                )
            }
        }.awaitAll()
        val afterRace = store.listRevisions()
        assertEquals(raced.map(CloudRevision::fileId).toSet(), cloudRevisionHeads(afterRace).map(CloudRevision::fileId).toSet())

        // Resolving the conflict merges both tips into one head again.
        val merged = store.uploadRevision(
            File(directory, "merge.tlb").apply { writeBytes(payload(2_000)) },
            revision(20, "device-b", base = raced[0].fileId, merged = raced.map(CloudRevision::fileId)),
        )
        assertEquals(listOf(merged.fileId), cloudRevisionHeads(store.listRevisions()).map(CloudRevision::fileId))

        store.deleteRevision(first.fileId)
        assertTrue(store.listRevisions().none { it.fileId == first.fileId })
    }

    private companion object {
        const val BRANCH = "life-assistant-sync-store-test"
    }
}
