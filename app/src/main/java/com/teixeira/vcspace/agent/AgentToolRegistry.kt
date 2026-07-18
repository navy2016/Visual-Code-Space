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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

object AgentToolRegistry {
    private val mutex = Mutex()
    private val tools = linkedMapOf<String, AgentTool>()

    suspend fun register(tool: AgentTool) {
        require(tool.name.matches(TOOL_NAME_REGEX)) {
            "Invalid agent tool name: ${tool.name}"
        }

        mutex.withLock {
            tools[tool.name] = tool
        }
    }

    suspend fun unregister(name: String) {
        mutex.withLock {
            tools.remove(name)
        }
    }

    suspend fun clearProvider(provider: String) {
        mutex.withLock {
            tools.entries.removeAll { it.value.provider == provider }
        }
    }

    suspend fun clearProvidersWithPrefix(providerPrefix: String) {
        mutex.withLock {
            tools.entries.removeAll { it.value.provider.startsWith(providerPrefix) }
        }
    }

    suspend fun listTools(): List<AgentToolSpec> = mutex.withLock {
        tools.values.map { it.toSpec() }
    }

    suspend fun listTools(providerPrefix: String): List<AgentToolSpec> = mutex.withLock {
        tools.values.filter { it.provider.startsWith(providerPrefix) }.map { it.toSpec() }
    }

    suspend fun findTool(name: String): AgentToolSpec? = mutex.withLock {
        tools[name]?.toSpec()
    }

    suspend fun invoke(name: String, arguments: JsonObject): AgentToolResult {
        val tool = mutex.withLock { tools[name] }
            ?: return AgentToolResult.error("Unknown agent tool: $name")

        return runCatching {
            tool.execute(arguments)
        }.getOrElse { error ->
            AgentToolResult.error(error.message ?: error::class.java.simpleName)
        }
    }

    private val TOOL_NAME_REGEX = Regex("^[a-zA-Z0-9_\\-.:]+$")
}
