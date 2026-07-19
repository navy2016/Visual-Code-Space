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
import com.teixeira.vcspace.BuildConfig
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.SecureRandom

class AgentBridgeServer(
    @Suppress("unused") private val context: Context,
    port: Int = 0
) : NanoHTTPD("127.0.0.1", port) {
    val token: String = createToken()

    val assignedPort: Int
        get() = listeningPort

    val bridgeUrl: String
        get() = "http://127.0.0.1:$assignedPort"

    fun ensureStarted() {
        if (isAlive || wasStarted()) return
        start(SOCKET_READ_TIMEOUT, false)
    }

    override fun serve(session: IHTTPSession): Response = runBlocking {
        try {
            when {
                session.method == Method.GET && session.uri == "/health" -> json(
                    buildJsonObject {
                        put("ok", true)
                        put("app", "Visual Code Space")
                        put("versionName", BuildConfig.VERSION_NAME)
                        put("workspace", AgentEditorBridge.getWorkspaceRootPath() ?: "")
                    }
                )

                session.method == Method.GET && session.uri == "/capabilities" -> {
                    if (!isAuthorized(session)) return@runBlocking unauthorized()
                    json(
                        buildJsonObject {
                            put("tools", buildJsonArray {
                                AgentToolRegistry.listTools().forEach { spec ->
                                    add(toolSpecToJson(spec))
                                }
                            })
                        }
                    )
                }

                session.method == Method.POST && session.uri == "/tools/invoke" -> {
                    if (!isAuthorized(session)) return@runBlocking unauthorized()
                    invokeTool(session)
                }

                else -> text(Response.Status.NOT_FOUND, "Not Found")
            }
        } catch (error: Throwable) {
            json(
                buildJsonObject {
                    put("ok", false)
                    put("error", error.message ?: error::class.java.simpleName)
                },
                Response.Status.INTERNAL_ERROR
            )
        }
    }

    private suspend fun invokeTool(session: IHTTPSession): Response {
        val body = parseBody(session)
        val payload = Json.parseToJsonElement(body).jsonObject
        val name = payload["name"]?.jsonPrimitive?.contentOrNull
            ?: return jsonError("Missing required field: name", Response.Status.BAD_REQUEST)
        val arguments = payload["arguments"]?.jsonObject ?: JsonObject(emptyMap())

        val result = AgentToolRegistry.invoke(name, arguments)
        return json(
            buildJsonObject {
                put("ok", !result.isError)
                put("text", result.text)
                put("isError", result.isError)
                result.data?.let { put("data", it) }
            },
            if (result.isError) Response.Status.BAD_REQUEST else Response.Status.OK
        )
    }

    private fun parseBody(session: IHTTPSession): String {
        val body = hashMapOf<String, String>()
        session.parseBody(body)
        return body["postData"].orEmpty()
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val auth = session.headers["authorization"].orEmpty()
        return auth == "Bearer $token"
    }

    private fun unauthorized(): Response = jsonError("Unauthorized", Response.Status.UNAUTHORIZED)

    private fun jsonError(message: String, status: Response.Status): Response = json(
        buildJsonObject {
            put("ok", false)
            put("error", message)
        },
        status
    )

    private fun json(element: JsonElement, status: Response.Status = Response.Status.OK): Response =
        newFixedLengthResponse(status, "application/json", jsonFormat.encodeToString(element))

    private fun text(status: Response.Status, text: String): Response =
        newFixedLengthResponse(status, MIME_PLAINTEXT, text)

    private fun toolSpecToJson(spec: AgentToolSpec): JsonElement = buildJsonObject {
        put("name", spec.name)
        put("label", spec.label)
        put("description", spec.description)
        put("parameters", spec.parameters)
        put("provider", spec.provider)
        put("dangerous", spec.dangerous)
        put("permissions", buildJsonArray {
            spec.permissions.forEach { add(it.name) }
        })
    }

    companion object {
        private val jsonFormat = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

        private fun createToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
            )
        }
    }
}
