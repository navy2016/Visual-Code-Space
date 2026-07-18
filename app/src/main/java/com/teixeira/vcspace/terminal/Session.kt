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

package com.teixeira.vcspace.terminal

import com.teixeira.vcspace.activities.TerminalActivity
import com.teixeira.vcspace.extensions.child
import com.teixeira.vcspace.extensions.tmpDir
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

// https://github.com/RohitKushvaha01/ReTerminal/blob/main/app/src/main/java/com/rk/terminal/terminal/MkSession.kt
object Session {
    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private fun shellWorkingDir(path: String): String = when {
        path.startsWith("/storage") || path.startsWith("/sdcard") -> path
        path == home.absolutePath -> "/home"
        else -> "/home"
    }

    fun createSession(
        activity: TerminalActivity,
        sessionClient: TerminalSessionClient,
        sessionId: String,
        prootCommandOverride: String? = null,
        workingDirectoryOverride: String? = null
    ): TerminalSession {
        with(activity) {
            val envVariables = mapOf(
                "ANDROID_ART_ROOT" to System.getenv("ANDROID_ART_ROOT"),
                "ANDROID_DATA" to System.getenv("ANDROID_DATA"),
                "ANDROID_I18N_ROOT" to System.getenv("ANDROID_I18N_ROOT"),
                "ANDROID_ROOT" to System.getenv("ANDROID_ROOT"),
                "ANDROID_RUNTIME_ROOT" to System.getenv("ANDROID_RUNTIME_ROOT"),
                "ANDROID_TZDATA_ROOT" to System.getenv("ANDROID_TZDATA_ROOT"),
                "BOOTCLASSPATH" to System.getenv("BOOTCLASSPATH"),
                "DEX2OATBOOTCLASSPATH" to System.getenv("DEX2OATBOOTCLASSPATH"),
                "EXTERNAL_STORAGE" to System.getenv("EXTERNAL_STORAGE")
            )

            val workingDir = if (!workingDirectoryOverride.isNullOrBlank()) {
                workingDirectoryOverride
            } else if (intent.hasExtra(TerminalActivity.KEY_WORKING_DIRECTORY)) {
                intent.getStringExtra(TerminalActivity.KEY_WORKING_DIRECTORY).toString()
            } else if (intent.hasExtra("cwd")) {
                intent.getStringExtra("cwd").toString()
            } else {
                home.absolutePath
            }

            val tmpDir = File(activity.tmpDir, "terminal/$sessionId")

            if (tmpDir.exists()) {
                tmpDir.deleteRecursively()
                tmpDir.mkdirs()
            } else {
                tmpDir.mkdirs()
            }

            val env = mutableListOf(
                "PROOT_TMP_DIR=${tmpDir.absolutePath}",
                "HOME=${home.absolutePath}",
                "VCSPACE_HOME=${home.absolutePath}",
                "PI_CODING_AGENT_DIR=${home.absolutePath}/.pi/agent",
                "PI_CODING_AGENT_SESSION_DIR=${home.absolutePath}/.pi/agent/sessions",
                "XDG_CACHE_HOME=${home.absolutePath}/.cache",
                "PUBLIC_HOME=${getExternalFilesDir(null)?.absolutePath}",
                "COLORTERM=truecolor",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "PREFIX=${prefix.absolutePath}",
                "LD_LIBRARY_PATH=${lib.absolutePath}",
                "ALPINE=${alpineDir.absolutePath}",
                "APK_REPOSITORY_BASE=https://mirrors.aliyun.com/alpine/v3.22",
                "LINKER=${Executor.linker}",
                "VCSPACE_BRIDGE_URL=${activity.terminalBinder?.service?.piBridgeUrl.orEmpty()}",
                "VCSPACE_BRIDGE_TOKEN=${activity.terminalBinder?.service?.piBridgeToken.orEmpty()}",
                "PROOT=${
                    File(filesDir, "proot").apply {
                        if (exists().not()) {
                            assets.open("terminal/proot").use {
                                writeBytes(it.readBytes())
                            }
                        }
                        setExecutable(true)
                    }.absolutePath
                }"
            )

            env.addAll(envVariables.map { "${it.key}=${it.value}" })

            val initHost = bin.child("init-host").apply {
                writeText(
                    assets.open("terminal/init-host.sh").bufferedReader().use { it.readText() })
                setExecutable(true)
            }
            bin.child("init").apply {
                writeText(assets.open("terminal/init.sh").bufferedReader().use { it.readText() })
                setExecutable(true)
            }
            bin.child("vcspace-pi-manager").apply {
                writeText(assets.open("terminal/vcspace-pi-manager.sh").bufferedReader().use { it.readText() })
                setExecutable(true)
            }

            val shell = "/system/bin/sh"
            val prootCommand = prootCommandOverride ?: intent.getStringExtra(TerminalActivity.KEY_PROOT_COMMAND)
            val prootWorkingDir = shellWorkingDir(workingDir)
            val command = if (prootCommand.isNullOrBlank()) {
                "cd ${shellQuote(prootWorkingDir)} && exec /bin/bash"
            } else {
                "cd ${shellQuote(prootWorkingDir)} && $prootCommand"
            }
            intent.removeExtra(TerminalActivity.KEY_PROOT_COMMAND)
            val args = arrayOf(shell, initHost.absolutePath, command)

            return TerminalSession(
                shell,
                workingDir,
                args,
                env.toTypedArray(),
                TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
                sessionClient
            )
        }
    }
}
