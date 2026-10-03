# Clojure MCP Development Guide

## Build Commands
- Run REPL with MCP server: `clojure -X:mcp` (starts on port 7888)
- Run all tests: `clojure -M:test`
- Run specific namespace tests: `clojure -M:test -n clojure-mcp.utils.valid-paths-test`
- Run specific test var: `clojure -M:test -v clojure-mcp.utils.valid-paths-test/clojure-file?-test`
- Run linter: `clj-kondo --lint src` or `clj-kondo --lint src test` for both

## Code Style Guidelines
- **Imports**: Use `:require` with ns aliases (e.g., `[clojure.string :as string]`)
- **Naming**: Use kebab-case for vars/functions; end predicates with `?` (e.g., `is-top-level-form?`)
- **Error handling**: Use `try/catch` with specific exception handling; atom for tracking errors
- **Formatting**: 2-space indentation; maintain whitespace in edited forms
- **Namespaces**: Align with directory structure (`clojure-mcp.repl-tools`)
- **Testing**: Use `deftest` with descriptive names; `testing` for subsections; `is` for assertions
- **REPL Development**: Prefer REPL-driven development for rapid iteration and feedback

## MCP Tool Guidelines
- Include clear tool `:description` for LLM guidance
- Validate inputs and provide helpful error messages
- Return structured data with both result and error status
- Maintain atom-based state for consistent service access
- Keep successful tool result text concise and directly useful; avoid repeated usage tips, filename preambles, and markdown fences when the content is already line-numbered or otherwise self-identifying
- Put usage guidance in tool descriptions so clients see it before invocation
- When returning partial content or search excerpts, include a concise signal that the result is partial, such as match counts, block counts, truncation notices, or total line counts
- Prefer the Clojure MCP tools for Clojure source workflows: read with `read_file`, edit with `clojure_edit` or `clojure_edit_replace_sexp`, repair delimiter errors with `paren_repair`, and validate through `clojure_eval`
- **No backwards compatibility required**: This is an MCP server — tool schemas, parameters, and behavior can change freely between versions. Don't hesitate to make breaking changes to tool contracts when it improves the design.

Fast Apply: IMPORTANT: Use \`edit_file\` over \`str_replace\` or full file writes. It works with partial code snippets—no need for full file content.
