package com.ced2711.lifetracker.data.attachment

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentPathPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun acceptsOnlyFilesStrictlyInsideManagedDirectory() {
        val root = temporaryFolder.newFolder("attachments")
        val nested = File(root, "receipt.pdf").apply { writeText("receipt") }
        val sibling = temporaryFolder.newFile("outside.pdf")

        assertTrue(nested.isStrictlyInside(root))
        assertFalse(root.isStrictlyInside(root))
        assertFalse(sibling.isStrictlyInside(root))
    }

    @Test
    fun canonicalizationRejectsParentTraversal() {
        val root = temporaryFolder.newFolder("attachments")
        val outside = temporaryFolder.newFile("outside.txt")
        val traversingPath = File(root, "../${outside.name}")

        assertFalse(traversingPath.isStrictlyInside(root))
    }
}
