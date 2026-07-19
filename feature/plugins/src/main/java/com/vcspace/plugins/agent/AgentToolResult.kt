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
 * Result returned by an AI-callable plugin tool.
 *
 * @property text text sent back to the AI model.
 * @property isError whether the invocation failed.
 * @property dataJson optional JSON object/array/string payload for clients that need structured data.
 */
data class AgentToolResult @JvmOverloads constructor(
    val text: String,
    val isError: Boolean = false,
    val dataJson: String? = null
) {
    companion object {
        @JvmStatic
        fun text(text: String, dataJson: String? = null): AgentToolResult =
            AgentToolResult(text = text, dataJson = dataJson)

        @JvmStatic
        fun error(message: String, dataJson: String? = null): AgentToolResult =
            AgentToolResult(text = message, isError = true, dataJson = dataJson)
    }
}
