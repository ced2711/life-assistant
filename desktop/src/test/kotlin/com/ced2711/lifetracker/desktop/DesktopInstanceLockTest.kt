package com.ced2711.lifetracker.desktop

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only one copy of the app runs: a second one, in this process or another, does not get the lock. */
class DesktopInstanceLockTest {
    private val dir: File = Files.createTempDirectory("instance-lock").toFile()
    private val file = File(dir, "test.instance.lock")

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun theFirstCopyHoldsItAndTheNextOneCanTakeItOnlyAfterwards() {
        val first = DesktopInstanceLock(file)
        val second = DesktopInstanceLock(file)
        assertTrue(first.acquire())
        assertTrue("asking again in the same copy is fine", first.acquire())
        assertFalse(second.acquire())
        first.release()
        assertTrue(second.acquire())
        second.release()
    }

    @Test
    fun anotherProcessHoldingTheLockKeepsThisOneOut() {
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        // The class path as an argument file: on Windows it is too long for a command line.
        val arguments = File(dir, "java.args").apply {
            writeText("-cp \"${System.getProperty("java.class.path").replace("\\", "\\\\")}\"\n")
        }
        val ready = File(dir, "ready")
        val holder = ProcessBuilder(java, "@${arguments.absolutePath}", "com.ced2711.lifetracker.desktop.DesktopInstanceLockTestKt", file.absolutePath, ready.absolutePath)
            .redirectErrorStream(true)
            .start()
        try {
            val deadline = System.currentTimeMillis() + 30_000
            while (!ready.exists() && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertTrue("the other process took the lock", ready.exists())

            val here = DesktopInstanceLock(file)
            assertFalse(here.acquire())

            // However the other copy ends, even killed, the lock comes free again (on Windows a
            // moment after the process has gone; the app keeps trying while it waits).
            holder.destroyForcibly()
            holder.waitFor(10, TimeUnit.SECONDS)
            val freeBy = System.currentTimeMillis() + 10_000
            var taken = here.acquire()
            while (!taken && System.currentTimeMillis() < freeBy) {
                Thread.sleep(100)
                taken = here.acquire()
            }
            assertTrue(taken)
            here.release()
        } finally {
            holder.destroyForcibly()
        }
    }
}

/** Run by the test above in its own process: takes the lock, says so, and waits to be ended. */
fun main(args: Array<String>) {
    check(DesktopInstanceLock(File(args[0])).acquire())
    File(args[1]).writeText("ready")
    Thread.sleep(60_000)
}
