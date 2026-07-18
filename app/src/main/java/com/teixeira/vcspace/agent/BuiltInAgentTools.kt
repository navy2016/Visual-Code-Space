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
import com.teixeira.vcspace.file.wrapFile
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
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

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

    private suspend fun readFile(context: Context, path: String): AgentToolResult =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.exists() || !file.isFile) {
                return@withContext AgentToolResult.error("File does not exist: $path")
            }

            AgentToolResult.text(file.wrapFile().readFile2String(context) ?: "")
        }

    private suspend fun writeFile(context: Context, path: String, content: String): AgentToolResult =
        withContext(Dispatchers.IO) {
            val file = File(path)
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
}
