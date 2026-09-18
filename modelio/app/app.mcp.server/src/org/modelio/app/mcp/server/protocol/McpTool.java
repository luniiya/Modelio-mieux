package org.modelio.app.mcp.server.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A single MCP tool: a named, invokable operation advertised by
 * {@code tools/list} and invoked by {@code tools/call}.
 */
public interface McpTool {

    /** Stable machine name, as advertised to and used by MCP clients. */
    String name();

    /** Human-readable description shown to the model/user by MCP clients. */
    String description();

    /** JSON Schema describing the {@code arguments} object accepted by {@link #call}. */
    ObjectNode inputSchema(ObjectMapper mapper);

    /**
     * Executes the tool.
     *
     * @param arguments the {@code arguments} object from the {@code tools/call} request
     * @param mapper    a shared Jackson mapper for building the result
     * @return the tool's result, serialized into the response's text content
     * @throws Exception on any failure; caught by the dispatcher and reported
     *                    as an {@code isError} tool result rather than a
     *                    JSON-RPC protocol error
     */
    JsonNode call(JsonNode arguments, ObjectMapper mapper) throws Exception;

}
