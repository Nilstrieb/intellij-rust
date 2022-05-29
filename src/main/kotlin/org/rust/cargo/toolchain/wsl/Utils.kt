/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

fun WSLDistribution.expandUserHome(path: String): String {
    if (!path.startsWith("~/")) return path
    val userHome = userHome ?: return path
    return "$userHome${path.substring(1)}"
}

fun Path.hasExecutableOnWsl(toolName: String): Boolean {
    checkEdtAndReadAction()
    return pathToExecutableOnWsl(toolName).toFile().isFile
}

fun Path.pathToExecutableOnWsl(toolName: String): Path = resolve(toolName)

private val LOG: Logger = Logger.getInstance("org.rust.cargo.toolchain.Utils")

private fun checkEdtAndReadAction() {
    val application = ApplicationManager.getApplication() ?: return
    if (!application.isInternal || application.isHeadlessEnvironment) return
    @NonNls val message = when {
        application.isDispatchThread -> "Access to WSL filesystem on EDT"
        application.isReadAccessAllowed -> "Access to WSL filesystem under ReadAction"
        else -> return
    }
    LOG.error(message)
}
