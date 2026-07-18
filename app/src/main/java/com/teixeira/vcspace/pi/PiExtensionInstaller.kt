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

package com.teixeira.vcspace.pi

import com.teixeira.vcspace.terminal.home
import java.io.File

object PiExtensionInstaller {
    private const val EXTENSION_FILE_NAME = "vcspace-bridge.ts"

    fun installOrUpdate(): File {
        val extensionsDir = File(home, ".pi/agent/extensions").apply { mkdirs() }
        return File(extensionsDir, EXTENSION_FILE_NAME).apply {
            writeText(extensionSource())
        }
    }

    private fun extensionSource(): String = """
        import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
        import { Type } from "typebox";

        type VCSpaceToolSpec = {
          name: string;
          label?: string;
          description?: string;
          parameters?: Record<string, unknown>;
          permissions?: string[];
          dangerous?: boolean;
        };

        const bridgeUrl = process.env.VCSPACE_BRIDGE_URL;
        const bridgeToken = process.env.VCSPACE_BRIDGE_TOKEN;

        async function callBridge(path: string, init: any = {}) {
          if (!bridgeUrl || !bridgeToken) {
            throw new Error("VCSpace bridge is not configured. Open Pi from Visual Code Space terminal.");
          }

          const response = await fetch(`${'$'}{bridgeUrl}${'$'}{path}`, {
            ...init,
            headers: {
              "content-type": "application/json",
              "authorization": `Bearer ${'$'}{bridgeToken}`,
              ...(init.headers ?? {}),
            },
          });

          const text = await response.text();
          let data: any;
          try {
            data = text ? JSON.parse(text) : {};
          } catch {
            data = { text };
          }

          if (!response.ok || data.ok === false) {
            throw new Error(data.error || data.text || `VCSpace bridge error: ${'$'}{response.status}`);
          }

          return data;
        }

        async function refreshVCSpaceTools(pi: ExtensionAPI, ctx?: any) {
          const capabilities = await callBridge("/capabilities");
          const tools = Array.isArray(capabilities.tools) ? capabilities.tools as VCSpaceToolSpec[] : [];

          for (const tool of tools) {
            const toolName = `vcspace_${'$'}{tool.name}`;
            pi.registerTool({
              name: toolName,
              label: tool.label ?? tool.name,
              description: tool.description ?? `Invoke Visual Code Space tool ${'$'}{tool.name}`,
              promptSnippet: `Invoke Visual Code Space tool ${'$'}{tool.name}`,
              promptGuidelines: [
                `Use ${'$'}{toolName} when the user asks to operate on Visual Code Space editor, workspace, UI, or plugin state.`,
              ],
              parameters: Type.Unsafe<any>((tool.parameters as any) ?? { type: "object", properties: {}, additionalProperties: true }),
              async execute(_toolCallId, params, _signal, _onUpdate, execCtx) {
                if (tool.dangerous && execCtx?.hasUI) {
                  const ok = await execCtx.ui.confirm(
                    `Allow VCSpace tool ${'$'}{tool.name}?`,
                    `Permissions: ${'$'}{(tool.permissions ?? []).join(", ") || "unknown"}`,
                  );
                  if (!ok) {
                    throw new Error("Blocked by user");
                  }
                }

                const result = await callBridge("/tools/invoke", {
                  method: "POST",
                  body: JSON.stringify({ name: tool.name, arguments: params ?? {} }),
                });

                return {
                  content: [{ type: "text", text: result.text ?? JSON.stringify(result, null, 2) }],
                  details: result,
                };
              },
            });
          }

          ctx?.ui?.notify?.(`VCSpace bridge loaded ${'$'}{tools.length} tool(s).`, "info");
        }

        export default function vcspaceBridgeExtension(pi: ExtensionAPI) {
          if (!bridgeUrl || !bridgeToken) {
            return;
          }

          pi.on("session_start", async (_event, ctx) => {
            try {
              await refreshVCSpaceTools(pi, ctx);
            } catch (error: any) {
              ctx.ui.notify(`VCSpace bridge failed: ${'$'}{error?.message ?? error}`, "warning");
            }
          });

          pi.registerCommand("vcspace-reload-tools", {
            description: "Reload Visual Code Space bridge tools",
            handler: async (_args, ctx) => {
              await refreshVCSpaceTools(pi, ctx);
            },
          });
        }
    """.trimIndent()
}
