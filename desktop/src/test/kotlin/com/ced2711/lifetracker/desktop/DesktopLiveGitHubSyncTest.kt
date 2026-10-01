package com.ced2711.lifetracker.desktop

import com.ced2711.lifetracker.cloudsync.ConflictResolution
import com.ced2711.lifetracker.cloudsync.GitHubBackupStore
import com.ced2711.lifetracker.cloudsync.GitHubRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Two independent devices syncing through real GitHub with the production sync controller.
 * Runs only when LA_GITHUB_TEST_REPO and LA_GITHUB_TEST_TOKEN are set.
 */
class DesktopLiveGitHubSyncTest {
    private val repository = System.getenv("LA_GITHUB_TEST_REPO")
    private val token = System.getenv("LA_GITHUB_TEST_TOKEN")

    private class Device(root: File, name: String, cloud: GitHubBackupStore) {
        val data = DesktopDataStore(root.resolve("$name/app"))
        val config = DesktopConfigStore(root.resolve("$name/desktop.properties"))
        val controller = DesktopCloudSyncController(
            dataStore = data,
            configStore = config,
            oauth = DesktopGoogleOAuth(config, DesktopCredentialStore(root.resolve("$name/credentials"))),
            cloudStore = cloud,
            connectionStatus = { true },
        )
        fun categories() = data.currentSnapshot()!!.categories.map { it.name }.toSet()
        fun todos() = data.currentSnapshot()!!.todos.map { it.title }.toSet()
    }

    @Test
    fun twoDevicesStayInStepThroughRealGitHub() = runBlocking {
        assumeTrue(repository != null && token != null)
        val root = Files.createTempDirectory("live-two-devices").toFile()
        val cloud = GitHubBackupStore({ token!! }, GitHubRepository.parse(repository!!), branch = BRANCH)
        // Start from an empty history on the dedicated test branch.
        cloud.listRevisions().forEach { cloud.deleteRevision(it.fileId) }
        val a = Device(root, "a", cloud)
        val b = Device(root, "b", cloud)
        try {
            assertTrue(a.data.open(password()))
            assertTrue(b.data.open(password()))

            // 1. A's edit reaches B, which starts empty.
            assertTrue(a.data.upsertTodo(null, "", "Written on A", null, com.ced2711.lifetracker.domain.model.TodoPriority.NONE, null, "", false))
            a.controller.synchronize()
            assertEquals("Encrypted backup uploaded.", a.controller.state.value.message)
            b.controller.synchronize()
            assertEquals("Cloud changes restored.", b.controller.state.value.message)
            assertEquals(setOf("Written on A"), b.todos())

            // 2. B's edit reaches A.
            assertTrue(b.data.addCategory("From B"))
            b.controller.synchronize()
            a.controller.synchronize()
            assertEquals(setOf("From B"), a.categories())

            // 3. Nothing changed: both report up to date.
            a.controller.synchronize()
            assertEquals("Already up to date.", a.controller.state.value.message)

            // 4. Both edit before syncing: the second device sees a conflict and resolves it.
            assertTrue(a.data.addCategory("Only on A"))
            assertTrue(b.data.addCategory("Only on B"))
            a.controller.synchronize()
            b.controller.synchronize()
            assertNotNull("B must be asked which version to keep", b.controller.state.value.conflict)
            b.controller.synchronize(ConflictResolution.KEEP_LOCAL)
            assertNull(b.controller.state.value.conflict)
            a.controller.synchronize()
            assertEquals(b.categories(), a.categories())
            assertTrue("Only on B" in a.categories())

            // 5. Many uploads keep only the newest versions in the cloud.
            repeat(12) { index ->
                assertTrue(a.data.addCategory("Batch $index"))
                a.controller.synchronize()
            }
            val remaining = cloud.listRevisions()
            assertTrue("cloud keeps ${remaining.size} versions", remaining.size <= DesktopCloudSyncController.KEPT_CLOUD_REVISIONS + 1)
            b.controller.synchronize()
            assertEquals(a.categories(), b.categories())
        } finally {
            a.data.close()
            b.data.close()
            root.deleteRecursively()
        }
    }

    private fun password(): CharArray = "live-sync-test-26!".toCharArray()

    private companion object {
        const val BRANCH = "life-assistant-sync-two-devices-test"
    }
}
