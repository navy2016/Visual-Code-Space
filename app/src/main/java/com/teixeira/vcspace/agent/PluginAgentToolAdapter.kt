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

import com.vcspace.plugins.agent.AgentToolPermission as PluginAgentToolPermission
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import com.vcspace.plugins.agent.AgentTool as PluginAgentTool

class PluginAgentToolAdapter(
    private val pluginId: String,
    private val delegate: PluginAgentTool
) : AgentTool {
    override val name: String = "plugin_${sanitizeName(pluginId)}_${sanitizeName(delegate.name)}"
    override val label: String = delegate.label
    override val description: String = delegate.description
    override val parameters: JsonObject = parseSchema(delegate.parameterSchemaJson)
    override val permissions: Set<AgentToolPermission> = delegate.permissions.map { it.toAppPermission() }.toSet()
    override val provider: String = "plugin:$pluginId"
    override val dangerous: Boolean = delegate.dangerous || permissions.any {
        it == AgentToolPermission.WRITE_EDITOR ||
            it == AgentToolPermission.WRITE_WORKSPACE ||
            it == AgentToolPermission.RUN_TERMINAL ||
            it == AgentToolPermission.NETWORK ||
            it == AgentToolPermission.PLUGIN
    }

    override suspend fun execute(arguments: JsonObject): AgentToolResult {
        val result = delegate.execute(arguments.toString())
        return AgentToolResult(
            text = result.text,
            isError = result.isError,
            data = result.dataJson?.let { parseData(it) }
        )
    }

    private fun PluginAgentToolPermission.toAppPermission(): AgentToolPermission = when (this) {
        PluginAgentToolPermission.READ_EDITOR -> AgentToolPermission.READ_EDITOR
        PluginAgentToolPermission.WRITE_EDITOR -> AgentToolPermission.WRITE_EDITOR
        PluginAgentToolPermission.READ_WORKSPACE -> AgentToolPermission.READ_WORKSPACE
        PluginAgentToolPermission.WRITE_WORKSPACE -> AgentToolPermission.WRITE_WORKSPACE
        PluginAgentToolPermission.READ_PLUGIN -> AgentToolPermission.READ_PLUGIN
        PluginAgentToolPermission.RUN_TERMINAL -> AgentToolPermission.RUN_TERMINAL
        PluginAgentToolPermission.NETWORK -> AgentToolPermission.NETWORK
        PluginAgentToolPermission.UI -> AgentToolPermission.UI
        PluginAgentToolPermission.PLUGIN -> AgentToolPermission.PLUGIN
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun sanitizeName(value: String): String = value
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "tool" }

        private fun parseSchema(schemaJson: String): JsonObject = runCatching {
            json.parseToJsonElement(schemaJson).jsonObject
        }.getOrElse {
            buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", buildJsonObject {})
                put("additionalProperties", true)
            }
        }

        private fun parseData(dataJson: String) = runCatching {
            json.parseToJsonElement(dataJson)
        }.getOrElse { JsonPrimitive(dataJson) }
    }
}
