package org.modelio.app.mcp.server.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Dispatches JSON-RPC 2.0 requests implementing the subset of the Model
 * Context Protocol (MCP) needed for a v1 Modelio bridge: the
 * {@code initialize} handshake plus {@code tools/list} and {@code tools/call}.
 * <p>
 * Deliberately transport-agnostic and free of any Eclipse/OSGi dependency so
 * it can be unit tested directly, without a live Modelio session or GUI.
 */
public final class McpDispatcher {

    public static final String PROTOCOL_VERSION = "2024-11-05";
    public static final String SERVER_NAME = "modelio-mcp";
    public static final String SERVER_VERSION = "0.1.0";

    private final ObjectMapper mapper;
    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    public McpDispatcher(final ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void registerTool(final McpTool tool) {
        this.tools.put(tool.name(), tool);
    }

    /**
     * Parses raw bytes as a single JSON-RPC request and dispatches it.
     *
     * @return the response object to send back, or {@code null} if the
     *         request was a notification (no {@code id}) and no response
     *         should be sent.
     */
    public ObjectNode handleRaw(final byte[] raw) {
        final JsonNode request;
        try {
            request = this.mapper.readTree(raw);
        } catch (final Exception e) {
            return errorResponse(null, -32700, "Parse error: " + e.getMessage());
        }
        return handle(request);
    }

    /**
     * Dispatches one already-parsed JSON-RPC request object.
     *
     * @return the response object, or {@code null} for a notification.
     */
    public ObjectNode handle(final JsonNode request) {
        if (request == null || !request.isObject()) {
            return errorResponse(null, -32600, "Invalid Request: expected a JSON object");
        }
        final JsonNode idNode = request.get("id");
        final String method = request.path("method").asText(null);
        try {
            final JsonNode result = dispatchMethod(method, request.path("params"));
            if (idNode == null) {
                return null;
            }
            final ObjectNode resp = this.mapper.createObjectNode();
            resp.put("jsonrpc", "2.0");
            resp.set("id", idNode);
            resp.set("result", result);
            return resp;
        } catch (final McpError e) {
            if (idNode == null) {
                return null;
            }
            return errorResponse(idNode, e.getCode(), e.getMessage());
        }
    }

    private JsonNode dispatchMethod(final String method, final JsonNode params) {
        if (method == null || method.isEmpty()) {
            throw new McpError(-32600, "Invalid Request: missing method");
        }
        switch (method) {
            case "initialize":
                return handleInitialize();
            case "notifications/initialized":
            case "ping":
                return this.mapper.createObjectNode();
            case "tools/list":
                return handleToolsList();
            case "tools/call":
                return handleToolsCall(params);
            default:
                throw new McpError(-32601, "Method not found: " + method);
        }
    }

    private ObjectNode handleInitialize() {
        final ObjectNode result = this.mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        final ObjectNode capabilities = this.mapper.createObjectNode();
        capabilities.set("tools", this.mapper.createObjectNode());
        result.set("capabilities", capabilities);
        final ObjectNode serverInfo = this.mapper.createObjectNode();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.set("serverInfo", serverInfo);
        return result;
    }

    private ObjectNode handleToolsList() {
        final ObjectNode result = this.mapper.createObjectNode();
        final ArrayNode arr = this.mapper.createArrayNode();
        for (final McpTool tool : this.tools.values()) {
            final ObjectNode n = this.mapper.createObjectNode();
            n.put("name", tool.name());
            n.put("description", tool.description());
            n.set("inputSchema", tool.inputSchema(this.mapper));
            arr.add(n);
        }
        result.set("tools", arr);
        return result;
    }

    private ObjectNode handleToolsCall(final JsonNode params) {
        final String toolName = params.path("name").asText(null);
        if (toolName == null) {
            throw new McpError(-32602, "Invalid params: missing tool 'name'");
        }
        final McpTool tool = this.tools.get(toolName);
        if (tool == null) {
            throw new McpError(-32602, "Unknown tool: " + toolName);
        }
        final JsonNode arguments = params.has("arguments") ? params.get("arguments") : this.mapper.createObjectNode();

        final ObjectNode result = this.mapper.createObjectNode();
        final ArrayNode content = this.mapper.createArrayNode();
        try {
            final JsonNode toolResult = tool.call(arguments, this.mapper);
            final ObjectNode text = this.mapper.createObjectNode();
            text.put("type", "text");
            text.put("text", toolResult.toString());
            content.add(text);
            result.set("content", content);
            result.set("structuredContent", toolResult);
            result.put("isError", false);
        } catch (final Exception e) {
            final ObjectNode text = this.mapper.createObjectNode();
            text.put("type", "text");
            text.put("text", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            content.add(text);
            result.set("content", content);
            result.put("isError", true);
        }
        return result;
    }

    private ObjectNode errorResponse(final JsonNode idNode, final int code, final String message) {
        final ObjectNode resp = this.mapper.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (idNode != null) {
            resp.set("id", idNode);
        } else {
            resp.putNull("id");
        }
        final ObjectNode err = this.mapper.createObjectNode();
        err.put("code", code);
        err.put("message", message);
        resp.set("error", err);
        return resp;
    }

}
