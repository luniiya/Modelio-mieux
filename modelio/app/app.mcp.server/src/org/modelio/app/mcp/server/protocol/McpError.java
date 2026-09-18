package org.modelio.app.mcp.server.protocol;

/**
 * A JSON-RPC 2.0 protocol-level error (as opposed to a tool execution
 * failure, which is reported inside a successful {@code tools/call} result
 * instead, per the MCP specification).
 */
public final class McpError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public McpError(final int code, final String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return this.code;
    }

}
