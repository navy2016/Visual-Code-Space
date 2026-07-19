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

package com.vcspace.plugins.agent

/**
 * Convenience implementation for plugins that want to register a tool without creating a class.
 */
class SimpleAgentTool @JvmOverloads constructor(
    override val name: String,
    override val label: String,
    override val description: String,
    override val parameterSchemaJson: String,
    override val permissions: Set<AgentToolPermission>,
    private val handler: Handler,
    override val dangerous: Boolean = permissions.any {
        it == AgentToolPermission.WRITE_EDITOR ||
            it == AgentToolPermission.WRITE_WORKSPACE ||
            it == AgentToolPermission.RUN_TERMINAL ||
            it == AgentToolPermission.NETWORK ||
            it == AgentToolPermission.PLUGIN
    }
) : AgentTool {
    override fun execute(argumentsJson: String): AgentToolResult = handler.execute(argumentsJson)

    fun interface Handler {
        fun execute(argumentsJson: String): AgentToolResult
    }
}
