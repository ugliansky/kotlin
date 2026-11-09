/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.jvm.index

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.jvm.environment.JvmClasspath
import org.jetbrains.kotlin.jvm.environment.containsRoot
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches classpath acceptance by indexed root, rather than matching every class file against the classpath.
 *
 * Acceptance applies to the whole indexed root. A classpath entry nested inside a broader indexed root does not make only that
 * subdirectory visible.
 */
class JvmClasspathRootFilter(private val classpath: JvmClasspath) : (VirtualFile) -> Boolean {
    private val acceptedRoots = ConcurrentHashMap<VirtualFile, Boolean>()

    override fun invoke(root: VirtualFile): Boolean = acceptedRoots.computeIfAbsent(root) { classpath.containsRoot(it) }
}