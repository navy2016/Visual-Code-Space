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

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object AgentToolSchemas {
    val emptyObject: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
    }

    val openTerminal: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "command",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Optional shell command to run inside the Alpine/proot terminal.")
                    }
                )
                put(
                    "working_directory",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Optional working directory for the terminal session.")
                    }
                )
                put(
                    "run_pi",
                    buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether to open Pi in the terminal instead of a generic shell.")
                    }
                )
            }
        )
        put("additionalProperties", false)
    }

    val showToast: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "message",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Message to show in Visual Code Space.")
                    }
                )
                put(
                    "long",
                    buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether to show a longer toast.")
                    }
                )
            }
        )
        put("required", buildJsonArray { add("message") })
        put("additionalProperties", false)
    }

    val filePath: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "path",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Absolute file path to read.")
                    }
                )
            }
        )
        put("required", buildJsonArray { add("path") })
        put("additionalProperties", false)
    }

    val editorFileSelector: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "path",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Absolute path of an opened editor file.")
                    }
                )
                put(
                    "index",
                    buildJsonObject {
                        put("type", "integer")
                        put("description", "Zero-based index of an opened editor tab.")
                    }
                )
            }
        )
        put("additionalProperties", false)
    }

    val listFiles: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "path",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Directory path to list. Defaults to workspace root.")
                    }
                )
                put(
                    "recursive",
                    buildJsonObject {
                        put("type", "boolean")
                        put("description", "Whether to list files recursively.")
                    }
                )
                put(
                    "max_entries",
                    buildJsonObject {
                        put("type", "integer")
                        put("description", "Maximum number of entries to return.")
                    }
                )
            }
        )
        put("additionalProperties", false)
    }

    val editorText: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "content",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Complete replacement editor contents.")
                    }
                )
            }
        )
        put("required", buildJsonArray { add("content") })
        put("additionalProperties", false)
    }

    val pluginInvokeTool: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "tool",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Fully-qualified plugin agent tool name to invoke.")
                    }
                )
                put(
                    "arguments",
                    buildJsonObject {
                        put("type", "object")
                        put("description", "Arguments passed to the plugin tool.")
                        put("additionalProperties", true)
                    }
                )
            }
        )
        put("required", buildJsonArray { add("tool") })
        put("additionalProperties", false)
    }

    val writeFile: JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "path",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Absolute file path to write.")
                    }
                )
                put(
                    "content",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Complete replacement file contents.")
                    }
                )
            }
        )
        put("required", buildJsonArray {
            add("path")
            add("content")
        })
        put("additionalProperties", false)
    }
}
