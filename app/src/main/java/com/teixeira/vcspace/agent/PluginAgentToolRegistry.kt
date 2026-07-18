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

import com.vcspace.plugins.agent.AgentTool as PluginAgentTool

object PluginAgentToolRegistry {
    private const val PROVIDER_PREFIX = "plugin:"

    suspend fun register(pluginId: String, tool: PluginAgentTool): String {
        val adapter = PluginAgentToolAdapter(pluginId, tool)
        AgentToolRegistry.register(adapter)
        return adapter.name
    }

    suspend fun unregister(toolName: String) {
        AgentToolRegistry.unregister(toolName)
    }

    suspend fun clearPlugin(pluginId: String) {
        AgentToolRegistry.clearProvider("$PROVIDER_PREFIX$pluginId")
    }

    suspend fun clearAllPlugins() {
        AgentToolRegistry.clearProvidersWithPrefix(PROVIDER_PREFIX)
    }

    suspend fun listPluginTools(): List<AgentToolSpec> =
        AgentToolRegistry.listTools(PROVIDER_PREFIX)

    suspend fun invoke(toolName: String, arguments: kotlinx.serialization.json.JsonObject): AgentToolResult {
        val spec = AgentToolRegistry.findTool(toolName)
            ?: return AgentToolResult.error("Unknown plugin agent tool: $toolName")

        if (!spec.provider.startsWith(PROVIDER_PREFIX)) {
            return AgentToolResult.error("Tool is not provided by a plugin: $toolName")
        }

        return AgentToolRegistry.invoke(toolName, arguments)
    }
}
