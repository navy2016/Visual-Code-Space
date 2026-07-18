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

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

interface AgentTool {
    val name: String
    val label: String
    val description: String
    val parameters: JsonObject
    val permissions: Set<AgentToolPermission>
    val provider: String
    val dangerous: Boolean
        get() = permissions.any {
            it == AgentToolPermission.WRITE_EDITOR ||
                it == AgentToolPermission.WRITE_WORKSPACE ||
                it == AgentToolPermission.MANAGE_EDITOR ||
                it == AgentToolPermission.RUN_TERMINAL ||
                it == AgentToolPermission.NETWORK ||
                it == AgentToolPermission.PLUGIN
        }

    suspend fun execute(arguments: JsonObject): AgentToolResult
}

data class AgentToolSpec(
    val name: String,
    val label: String,
    val description: String,
    val parameters: JsonObject,
    val permissions: Set<AgentToolPermission>,
    val provider: String,
    val dangerous: Boolean
)

data class AgentToolResult(
    val text: String,
    val isError: Boolean = false,
    val data: JsonElement? = null
) {
    companion object {
        fun text(text: String, data: JsonElement? = null): AgentToolResult =
            AgentToolResult(text = text, data = data)

        fun error(message: String, data: JsonElement? = null): AgentToolResult =
            AgentToolResult(text = message, isError = true, data = data)
    }
}

fun AgentTool.toSpec(): AgentToolSpec = AgentToolSpec(
    name = name,
    label = label,
    description = description,
    parameters = parameters,
    permissions = permissions,
    provider = provider,
    dangerous = dangerous
)
