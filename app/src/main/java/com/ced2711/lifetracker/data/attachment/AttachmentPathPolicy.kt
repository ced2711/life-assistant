package com.ced2711.lifetracker.data.attachment

import java.io.File

internal fun File.isStrictlyInside(directory: File): Boolean = runCatching {
    val root = directory.canonicalFile.toPath()
    val candidate = canonicalFile.toPath()
    candidate != root && candidate.startsWith(root)
}.getOrDefault(false)
