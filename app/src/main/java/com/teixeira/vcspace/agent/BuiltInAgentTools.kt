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

package com.teixeira.vcspace.agent

import android.content.Context
import android.content.Intent
import com.teixeira.vcspace.activities.TerminalActivity
import com.teixeira.vcspace.file.FileAccessCheck
import com.teixeira.vcspace.file.WorkspaceAccessManager
import com.teixeira.vcspace.file.wrapFile
import com.teixeira.vcspace.pi.PiCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object BuiltInAgentTools {
    private const val PROVIDER = "vcspace"

    suspend fun register(context: Context) {
        AgentToolRegistry.clearProvider(PROVIDER)
        tools(context.applicationContext).forEach { tool ->
            AgentToolRegistry.register(tool)
        }
    }

    private fun tools(context: Context): List<AgentTool> = listOf(
        SimpleAgentTool(
            name = "editor_get_current_file",
            label = "Get Current Editor File",
            description = "Return the absolute path of the file currently active in Visual Code Space.",
            parameters = AgentToolSchemas.emptyObject,
            permissions = setOf(AgentToolPermission.READ_EDITOR)
        ) {
            AgentEditorBridge.getCurrentFilePath()
        },
        SimpleAgentTool(
            name = "editor_get_text",
            label = "Get Current Editor Text",
            description = "Return the full text from the currently active Visual Code Space editor.",
            parameters = AgentToolSchemas.emptyObject,
            permissions = setOf(AgentToolPermission.READ_EDITOR)
        ) {
            AgentEditorBridge.getCurrentEditorText()
        },
        SimpleAgentTool(
            name = "editor_replace_text",
            label = "Replace Current Editor Text",
            description = "Replace the entire text of the currently active Visual Code Space editor.",
            parameters = AgentToolSchemas.editorText,
            permissions = setOf(AgentToolPermission.WRITE_EDITOR)
        ) { args ->
            val text = args.string("content")
                ?: return@SimpleAgentTool AgentToolResult.error("Missing required argument: content")
            AgentEditorBridge.replaceCurrentEditorText(text)
        },
        SimpleAgentTool(
            name = "editor_save",
            label = "Save Current Editor File",
            description = "Save the currently active Visual Code Space editor file.",
            parameters = AgentToolSchemas.emptyObject,
            permissions = setOf(AgentToolPermission.WRITE_EDITOR)
        ) {
            AgentEditorBridge.saveCurrentFile()
        },
        SimpleAgentTool(
            name = "editor_save_all",
            label = "Save All Editor Files",
            description = "Save all opened Visual Code Space editor files.",
            parameters = AgentToolSchemas.emptyObject,
            permissions = setOf(AgentToolPermission.WRITE_EDITOR)
        ) {
            AgentEditorBridge.saveAllFiles()
        },
        SimpleAgentTool(
            name = "editor_list_open_files",
            label = "List Open Editor Files",
            description = "List all files currently opened in Visual Code Space editor tabs.",
            parameters = AgentToolSchemas.emptyObject,
            permissions = setOf(AgentToolPermission.READ_EDITOR)
        ) {
            AgentEditorBridge.listOpenFiles()
        },
        SimpleAgentTool(
            name = "editor_select_file",
            label = "Select Open Editor File",
            description = "Switch the active Visual Code Space editor tab by absolute path or zero-based index.",
            parameters = AgentToolSchemas.editorFileSelector,
            permissions = setOf(AgentToolPermission.READ_EDITOR, AgentToolPermission.MANAGE_EDITOR)
        ) { args ->
            AgentEditorBridge.selectOpenFile(args.string("path"), args.int("index"))
        },
        SimpleAgentTool(
            name = "editor_close_file",
            label = "Close Open Editor File",
            description = "Close a Visual Code Space editor tab by absolute path or zero-based index. Defaults to the active tab.",
            parameters = AgentToolSchemas.editorFileSelector,
            permissions = setOf(AgentToolPermission.MANAGE_EDITOR)
        ) { args ->
            AgentEditorBridge.closeOpenFile(args.string("path"), args.int("index"))
        },
        SimpleAgentTool(
            name = "editor_open_file",
            label = "Open File in Editor",
            description = "Open an existing file in the Visual Code Space editor.",
            parameters = AgentToolSchemas.filePath,
            permissions = setOf(AgentToolPermission.READ_WORKSPACE, AgentToolPermission.READ_EDITOR)
        ) { args ->
            val path = args.string("path")
                ?: return@SimpleAgentTool AgentToolResult.error("Missing required argument: path")
            AgentEditorBridge.openFile(path)
        },
        SimpleAgentTool(
            name = "workspace_get_root",
            label = "Get Workspace Root",
            description = "Return the currently opened Visual Code Space workspace folder path, if any.",
            parameters = AgentToolSchemas.emptyObject,
            permissions = setOf(AgentToolPermission.READ_WORKSPACE)
        ) {
            AgentToolResult.text(AgentEditorBridge.getWorkspaceRootPath() ?: "")
        },
        SimpleAgentTool(
            name = "workspace_list_roots",
            label = "List Allowed Workspace Roots",
            description = "List app-internal, terminal workspace, opened workspace, and user-authorized external roots available to agents.",
            parameters = AgentToolSchemas.listRoots,
            permissions = setOf(AgentToolPermission.READ_WORKSPACE)
        ) {
            listAllowedRoots(context)
        },
        SimpleAgentTool(
            name = "workspace_list_files",
            label = "List Workspace Files",
            description = "List files under a workspace or Android-accessible directory.",
            parameters = AgentToolSchemas.listFiles,
            permissions = setOf(AgentToolPermission.READ_WORKSPACE)
        ) { args ->
            listFiles(
                context = context,
                path = args.string("path") ?: AgentEditorBridge.getWorkspaceRootPath(),
                recursive = args.boolean("recursive") ?: false,
                maxEntries = args.int("max_entries") ?: 200
            )
        },
        SimpleAgentTool(
            name = "workspace_read_file",
            label = "Read Workspace File",
            description = "Read a UTF-8 text file from the Android-accessible filesystem.",
            parameters = AgentToolSchemas.filePath,
            permissions = setOf(AgentToolPermission.READ_WORKSPACE)
        ) { args ->
            val path = args.string("path")
                ?: return@SimpleAgentTool AgentToolResult.error("Missing required argument: path")
            readFile(context, path)
        },
        SimpleAgentTool(
            name = "workspace_write_file",
            label = "Write Workspace File",
            description = "Replace a UTF-8 text file on the Android-accessible filesystem.",
            parameters = AgentToolSchemas.writeFile,
            permissions = setOf(AgentToolPermission.WRITE_WORKSPACE)
        ) { args ->
            val path = args.string("path")
                ?: return@SimpleAgentTool AgentToolResult.error("Missing required argument: path")
            val content = args.string("content")
                ?: return@SimpleAgentTool AgentToolResult.error("Missing required argument: content")
            writeFile(context, path, content)
        },
        SimpleAgentTool(
            name = "app_show_toast",
            label = "Show VCSpace Toast",
            description = "Show a short Android toast message in Visual Code Space.",
            parameters = AgentToolSchemas.showToast,
            permissions = setOf(AgentToolPermission.UI)
        ) { args ->
            val message = args.string("message")
                ?: return@SimpleAgentTool AgentToolResult.error("Missing required argument: message")
            AgentEditorBridge.showToast(message, args.boolean("long") ?: false)
        },
        SimpleAgentTool(
            name = "app_open_terminal",
            label = "Open Terminal",
            description = "Open Visual Code Space Terminal, optionally running Pi or a shell command.",
            parameters = AgentToolSchemas.openTerminal,
            permissions = setOf(AgentToolPermission.RUN_TERMINAL, AgentToolPermission.UI)
        ) { args ->
            openTerminal(
                context = context,
                command = args.string("command"),
                workingDirectory = args.string("working_directory"),
                runPi = args.boolean("run_pi") ?: false
            )
        },
        SimpleAgentTool(
            name = "plugin_list_tools",
            label = "List Plugin Agent Tools",
            description = "List AI-callable tools registered by Visual Code Space plugins.",
            parameters = AgentToolSchemas.emptyObject,
            permissions = setOf(AgentToolPermission.READ_PLUGIN)
        ) {
            listPluginTools()
        },
        SimpleAgentTool(
            name = "plugin_invoke_tool",
            label = "Invoke Plugin Agent Tool",
            description = "Invoke an AI-callable tool registered by a Visual Code Space plugin.",
            parameters = AgentToolSchemas.pluginInvokeTool,
            permissions = setOf(AgentToolPermission.PLUGIN)
        ) { args ->
            val toolName = args.string("tool")
                ?: return@SimpleAgentTool AgentToolResult.error("Missing required argument: tool")
            val toolArgs = args["arguments"]?.jsonObject ?: JsonObject(emptyMap())
            PluginAgentToolRegistry.invoke(toolName, toolArgs)
        }
    )

    private fun openTerminal(
        context: Context,
        command: String?,
        workingDirectory: String?,
        runPi: Boolean
    ): AgentToolResult {
        val intent = Intent(context, TerminalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (!workingDirectory.isNullOrBlank()) {
                putExtra(TerminalActivity.KEY_WORKING_DIRECTORY, workingDirectory)
            }
            if (runPi) {
                putExtra(TerminalActivity.KEY_RUN_PI, true)
                putExtra(TerminalActivity.KEY_PROOT_COMMAND, command ?: PiCommands.OPEN_PI)
            } else if (!command.isNullOrBlank()) {
                putExtra(TerminalActivity.KEY_PROOT_COMMAND, command)
            }
        }
        context.startActivity(intent)
        return AgentToolResult.text("Opened Visual Code Space terminal")
    }

    private suspend fun listPluginTools(): AgentToolResult {
        val tools = PluginAgentToolRegistry.listPluginTools()
        val data = buildJsonObject {
            put("tools", buildJsonArray {
                tools.forEach { spec ->
                    add(
                        buildJsonObject {
                            put("name", spec.name)
                            put("label", spec.label)
                            put("description", spec.description)
                            put("parameters", spec.parameters)
                            put("provider", spec.provider)
                            put("dangerous", spec.dangerous)
                            put("permissions", buildJsonArray {
                                spec.permissions.forEach { permission -> add(permission.name) }
                            })
                        }
                    )
                }
            })
        }
        val text = if (tools.isEmpty()) {
            "No plugin agent tools registered."
        } else {
            data["tools"]?.jsonArray?.joinToString("\n") { tool ->
                val item = tool.jsonObject
                "${item["name"]?.jsonPrimitive?.contentOrNull}: ${item["description"]?.jsonPrimitive?.contentOrNull}"
            }.orEmpty()
        }
        return AgentToolResult.text(text, data)
    }

    private fun listAllowedRoots(context: Context): AgentToolResult {
        val roots = WorkspaceAccessManager.roots(
            context = context,
            workspaceRoot = AgentEditorBridge.getWorkspaceRootPath(),
            terminalWorkingDirectory = WorkspaceAccessManager.terminalWorkingRoot(context).absolutePath
        )
        val text = roots.joinToString("\n") { root ->
            "${root.id}: ${root.path} (${root.label})"
        }
        return AgentToolResult.text(text)
    }

    private suspend fun listFiles(
        context: Context,
        path: String?,
        recursive: Boolean,
        maxEntries: Int
    ): AgentToolResult = withContext(Dispatchers.IO) {
        val root = when (val check = WorkspaceAccessManager.requireAllowedPath(
            context = context,
            path = path,
            workspaceRoot = AgentEditorBridge.getWorkspaceRootPath(),
            terminalWorkingDirectory = WorkspaceAccessManager.terminalWorkingRoot(context).absolutePath
        )) {
            is FileAccessCheck.Allowed -> check.file
            is FileAccessCheck.Denied -> return@withContext AgentToolResult.error(check.reason)
        }

        if (!root.exists() || !root.isDirectory) {
            return@withContext AgentToolResult.error("Directory does not exist: ${root.absolutePath}")
        }

        val limit = maxEntries.coerceIn(1, 2000)
        val files = if (recursive) {
            root.walkTopDown().drop(1).take(limit).toList()
        } else {
            root.listFiles()?.take(limit).orEmpty()
        }

        val text = files.joinToString("\n") { file ->
            val type = if (file.isDirectory) "dir" else "file"
            "[$type] ${file.absolutePath}"
        }.ifBlank { "Directory is empty: ${root.absolutePath}" }

        AgentToolResult.text(text)
    }

    private suspend fun readFile(context: Context, path: String): AgentToolResult =
        withContext(Dispatchers.IO) {
            val file = when (val check = WorkspaceAccessManager.requireAllowedPath(
                context = context,
                path = path,
                workspaceRoot = AgentEditorBridge.getWorkspaceRootPath(),
                terminalWorkingDirectory = WorkspaceAccessManager.terminalWorkingRoot(context).absolutePath
            )) {
                is FileAccessCheck.Allowed -> check.file
                is FileAccessCheck.Denied -> return@withContext AgentToolResult.error(check.reason)
            }
            if (!file.exists() || !file.isFile) {
                return@withContext AgentToolResult.error("File does not exist: ${file.absolutePath}")
            }

            AgentToolResult.text(file.wrapFile().readFile2String(context) ?: "")
        }

    private suspend fun writeFile(context: Context, path: String, content: String): AgentToolResult =
        withContext(Dispatchers.IO) {
            val file = when (val check = WorkspaceAccessManager.requireAllowedPath(
                context = context,
                path = path,
                write = true,
                workspaceRoot = AgentEditorBridge.getWorkspaceRootPath(),
                terminalWorkingDirectory = WorkspaceAccessManager.terminalWorkingRoot(context).absolutePath
            )) {
                is FileAccessCheck.Allowed -> check.file
                is FileAccessCheck.Denied -> return@withContext AgentToolResult.error(check.reason)
            }
            file.parentFile?.mkdirs()
            val ok = file.wrapFile().write(context, content)
            if (ok) AgentToolResult.text("Wrote file: ${file.absolutePath}")
            else AgentToolResult.error("Failed to write file: $path")
        }

    private class SimpleAgentTool(
        override val name: String,
        override val label: String,
        override val description: String,
        override val parameters: JsonObject,
        override val permissions: Set<AgentToolPermission>,
        private val handler: suspend (JsonObject) -> AgentToolResult
    ) : AgentTool {
        override val provider: String = PROVIDER

        override suspend fun execute(arguments: JsonObject): AgentToolResult = handler(arguments)
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.boolean(name: String): Boolean? =
        this[name]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.int(name: String): Int? =
        this[name]?.jsonPrimitive?.intOrNull
}
