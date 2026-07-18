/*
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Visual Code Space is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Visual Code Space.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.teixeira.vcspace.pi

import com.teixeira.vcspace.terminal.alpineDir
import com.teixeira.vcspace.terminal.home
import com.teixeira.vcspace.terminal.prefix
import java.io.File

object PiInstaller {
    val markerFile: File
        get() = File(home, ".vcspace/pi-installed")

    val agentDir: File
        get() = File(home, ".pi/agent")

    val extensionFile: File
        get() = File(agentDir, "extensions/vcspace-bridge.ts")

    val authFile: File
        get() = File(agentDir, "auth.json")

    private val possiblePiBinaries: List<File>
        get() = listOf(
            File(alpineDir, "usr/bin/pi"),
            File(alpineDir, "usr/local/bin/pi"),
            File(prefix, "bin/pi")
        )

    private val possibleNodeBinaries: List<File>
        get() = listOf(
            File(alpineDir, "usr/bin/node"),
            File(prefix, "bin/node")
        )

    private val possibleNpmBinaries: List<File>
        get() = listOf(
            File(alpineDir, "usr/bin/npm"),
            File(prefix, "bin/npm")
        )

    val piBinary: File
        get() = possiblePiBinaries.firstOrNull { it.exists() } ?: possiblePiBinaries.first()

    val nodeBinary: File
        get() = possibleNodeBinaries.firstOrNull { it.exists() } ?: possibleNodeBinaries.first()

    val npmBinary: File
        get() = possibleNpmBinaries.firstOrNull { it.exists() } ?: possibleNpmBinaries.first()

    fun isPiInstalled(): Boolean = possiblePiBinaries.any { it.exists() } || markerFile.exists()

    fun isNodeInstalled(): Boolean =
        possibleNodeBinaries.any { it.exists() } || possibleNpmBinaries.any { it.exists() }

    fun status(): PiInstallStatus = PiInstallStatus(
        rootFsReady = alpineDir.exists() && alpineDir.listFiles().isNullOrEmpty().not(),
        nodeInstalled = isNodeInstalled(),
        piInstalled = isPiInstalled(),
        extensionInstalled = extensionFile.exists(),
        markerPath = markerFile.absolutePath,
        extensionPath = extensionFile.absolutePath,
        piBinaryPath = piBinary.absolutePath
    )
}

data class PiInstallStatus(
    val rootFsReady: Boolean,
    val nodeInstalled: Boolean,
    val piInstalled: Boolean,
    val extensionInstalled: Boolean,
    val markerPath: String,
    val extensionPath: String,
    val piBinaryPath: String
) {
    val summary: String
        get() = when {
            piInstalled && extensionInstalled -> "Pi installed · VCSpace extension ready"
            piInstalled -> "Pi installed · extension missing"
            nodeInstalled -> "Node installed · Pi missing"
            rootFsReady -> "Terminal ready · Pi missing"
            else -> "Terminal environment not ready"
        }
}
