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
 * A tool exposed by a plugin to AI agents such as Pi.
 *
 * parameterSchemaJson must be a JSON Schema object. Keep schemas strict and small so models can
 * call the tool reliably.
 */
interface AgentTool {
    val name: String
    val label: String
    val description: String
    val parameterSchemaJson: String
    val permissions: Set<AgentToolPermission>

    val dangerous: Boolean
        get() = permissions.any {
            it == AgentToolPermission.WRITE_EDITOR ||
                it == AgentToolPermission.WRITE_WORKSPACE ||
                it == AgentToolPermission.RUN_TERMINAL ||
                it == AgentToolPermission.NETWORK ||
                it == AgentToolPermission.PLUGIN
        }

    /**
     * Execute the tool.
     *
     * @param argumentsJson JSON object string validated by Pi against [parameterSchemaJson].
     */
    fun execute(argumentsJson: String): AgentToolResult
}
