# ClojureMCP Documentation

This directory contains documentation for creating MCP (Model Context Protocol) components using ClojureMCP.

## Documentation Files

### Configuration Guides

### [The Full Toolkit (legacy)](full-toolkit.md)
Claude Desktop setup, the older editing profiles (`:cli-assist`, `:cli-assist-agent`, `:cli-assist-full`), API keys for the agent tools, the full tool list, and file-tool configuration. In maintenance mode; the recommended setup is the `:repl` profile.

### [Component Filtering Configuration](component-filtering.md)
Learn how to control which tools, prompts, and resources are exposed by your MCP server using enable/disable lists. Perfect for creating focused, secure, or specialized MCP servers with only the components you need.

### [Model Configuration](model-configuration.md)
Configure custom LLM models with your own API keys, endpoints, and parameters. Support for OpenAI, Anthropic, Google Gemini, and more through the LangChain4j integration.

### [Tools Configuration](tools-configuration.md)
Configure individual tools with custom settings, including model selection for AI-powered tools like dispatch_agent, architect, and code_critique.

### Creating Custom Servers

### [Creating Your Own Custom MCP Server](custom-mcp-server.md)
Learn how to create your own personalized MCP server by customizing tools, prompts, and resources. This is the primary way to configure ClojureMCP during the alpha phase, and it's both easy and empowering!

### [Generate Your Custom MCP Server with AI](gen-your-mcp-server.md)
Welcome to the new age of MCP server configuration! Learn how to use Large Language Models to generate a fully customized Clojure MCP server by providing documentation as context and describing your needs. Includes numerous examples and prompt templates.

### [Creating Tools with ClojureMCP's Multimethod System](creating-tools-multimethod.md)
Learn how to create tools using ClojureMCP's structured multimethod approach. This provides validation, error handling, and integration benefits when building tools within the ClojureMCP ecosystem.

### [Creating Tools Without ClojureMCP](creating-tools-without-clojuremcp.md)
Learn how to create tools as simple Clojure maps without depending on ClojureMCP's multimethod system. This approach allows you to create standalone tools that can be easily shared and integrated into any MCP server.

### [Creating Prompts](creating-prompts.md)
The standard guide for creating prompts in MCP. Prompts generate conversation contexts to help AI assistants understand specific tasks or workflows. This same approach works whether you're using ClojureMCP or creating standalone prompts.

### [Creating Resources](creating-resources.md)
The standard guide for creating resources in MCP. Resources provide read-only content like documentation, configuration files, or project information. This same approach works whether you're using ClojureMCP or creating standalone resources.

## Quick Start

For most users, start with [Creating Your Own Custom MCP Server](custom-mcp-server.md) to learn how to configure ClojureMCP for your specific needs.

## Key Concepts

- **Tools**: Perform actions and computations
- **Prompts**: Generate conversation contexts for AI assistants
- **Resources**: Provide read-only content

## Quick Reference

| Component | Schema | Callback Signature |
|-----------|--------|-------------------|
| Tool | `{:name, :description, :schema, :tool-fn}` | `(callback result-vector error-boolean)` |
| Prompt | `{:name, :description, :arguments, :prompt-fn}` | `(callback {:description "...", :messages [...]})` |
| Resource | `{:url, :name, :description, :mime-type, :resource-fn}` | `(callback ["content..."])` |

## Notes

- **Tools** can be created either using ClojureMCP's multimethod system or as simple maps (see the tools documentation)
- **Prompts** and **Resources** are always created as simple maps, making them inherently portable
- All components can be tested independently without an MCP server
- String keys are used for all parameter maps passed to component functions
