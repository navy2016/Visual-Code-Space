# AI Agent Tools for Plugins

Visual Code Space plugins can expose tools to AI agents such as Pi. When Pi is opened from the built-in terminal, Visual Code Space starts a localhost bridge and installs a Pi extension that discovers these tools.

## Register a tool

```kotlin
import com.vcspace.plugins.Plugin
import com.vcspace.plugins.PluginContext
import com.vcspace.plugins.agent.AgentToolPermission
import com.vcspace.plugins.agent.AgentToolResult
import com.vcspace.plugins.agent.SimpleAgentTool

class MyPlugin : Plugin {
    override fun onPluginLoaded(context: PluginContext) {
        val exposedName = context.registerAgentTool(
            SimpleAgentTool(
                name = "hello",
                label = "Hello from plugin",
                description = "Return a greeting from this plugin.",
                parameterSchemaJson = """
                    {
                      "type": "object",
                      "properties": {
                        "name": { "type": "string" }
                      },
                      "required": ["name"],
                      "additionalProperties": false
                    }
                """.trimIndent(),
                permissions = setOf(AgentToolPermission.READ_PLUGIN)
            ) { argumentsJson ->
                AgentToolResult.text("Hello from plugin: $argumentsJson")
            }
        )

        context.log("Registered AI tool: $exposedName")
    }
}
```

The final tool name is namespaced by Visual Code Space, for example:

```text
plugin_my_plugin_hello
```

Pi exposes it as:

```text
vcspace_plugin_my_plugin_hello
```

## Generic plugin bridge tools

Visual Code Space also exposes generic plugin helpers:

```text
vcspace_plugin_list_tools
vcspace_plugin_invoke_tool
```

Use `vcspace_plugin_list_tools` to discover plugin-registered tools, then call `vcspace_plugin_invoke_tool` with:

```json
{
  "tool": "plugin_my_plugin_hello",
  "arguments": { "name": "Pi" }
}
```

## Security notes

Declare permissions accurately. Tools with write, terminal, network, or generic plugin permissions are treated as dangerous and Pi asks for user confirmation before execution.
