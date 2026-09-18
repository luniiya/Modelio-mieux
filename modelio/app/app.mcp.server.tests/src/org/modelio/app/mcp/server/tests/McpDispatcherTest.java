package org.modelio.app.mcp.server.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.model.PackageInfo;
import org.modelio.app.mcp.server.protocol.McpDispatcher;
import org.modelio.app.mcp.server.tools.ListPackagesTool;

public class McpDispatcherTest {

    private ObjectMapper mapper;
    private McpDispatcher dispatcher;

    @Before
    public void setUp() {
        this.mapper = new ObjectMapper();
        this.dispatcher = new McpDispatcher(this.mapper);
        this.dispatcher.registerTool(new ListPackagesTool(new FakeModelBrowser(List.of(
                new PackageInfo("id-1", "Analysis", null), new PackageInfo("id-2", "Design", "Analysis")))));
    }

    @Test
    public void initializeReturnsProtocolVersionAndCapabilities() {
        final ObjectNode resp = this.dispatcher.handle(request(1, "initialize", this.mapper.createObjectNode()));

        assertEquals("2.0", resp.get("jsonrpc").asText());
        assertEquals(1, resp.get("id").asInt());
        final JsonNode result = resp.get("result");
        assertNotNull(result.get("protocolVersion"));
        assertTrue(result.has("capabilities"));
        assertTrue(result.has("serverInfo"));
    }

    @Test
    public void toolsListIncludesRegisteredTool() {
        final ObjectNode resp = this.dispatcher.handle(request(2, "tools/list", null));

        final JsonNode tools = resp.get("result").get("tools");
        assertEquals(1, tools.size());
        assertEquals("list_packages", tools.get(0).get("name").asText());
        assertTrue(tools.get(0).has("description"));
        assertTrue(tools.get(0).has("inputSchema"));
    }

    @Test
    public void toolsCallListPackagesReturnsContent() {
        final ObjectNode params = this.mapper.createObjectNode();
        params.put("name", "list_packages");
        params.set("arguments", this.mapper.createObjectNode());

        final ObjectNode resp = this.dispatcher.handle(request(3, "tools/call", params));

        final JsonNode result = resp.get("result");
        assertFalse(result.path("isError").asBoolean(true));
        assertTrue(result.has("structuredContent"));
        final String text = result.get("content").get(0).get("text").asText();
        assertTrue(text.contains("Analysis"));
        assertTrue(text.contains("Design"));
    }

    @Test
    public void unknownMethodReturnsJsonRpcError() {
        final ObjectNode resp = this.dispatcher.handle(request(4, "nope", null));

        assertTrue(resp.has("error"));
        assertEquals(-32601, resp.get("error").get("code").asInt());
    }

    @Test
    public void unknownToolReturnsJsonRpcError() {
        final ObjectNode params = this.mapper.createObjectNode();
        params.put("name", "does_not_exist");
        params.set("arguments", this.mapper.createObjectNode());

        final ObjectNode resp = this.dispatcher.handle(request(5, "tools/call", params));

        assertTrue(resp.has("error"));
        assertEquals(-32602, resp.get("error").get("code").asInt());
    }

    @Test
    public void notificationProducesNoResponse() {
        final ObjectNode req = this.mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("method", "notifications/initialized");

        assertNull(this.dispatcher.handle(req));
    }

    @Test
    public void toolExecutionErrorIsReportedAsIsErrorResultNotProtocolError() {
        final McpDispatcher failing = new McpDispatcher(this.mapper);
        failing.registerTool(new ListPackagesTool(
                new FakeModelBrowser(new IllegalStateException("No Modelio project is currently open."))));
        final ObjectNode params = this.mapper.createObjectNode();
        params.put("name", "list_packages");
        params.set("arguments", this.mapper.createObjectNode());

        final ObjectNode resp = failing.handle(request(6, "tools/call", params));

        assertFalse("a failing tool call is still a successful JSON-RPC response", resp.has("error"));
        final JsonNode result = resp.get("result");
        assertTrue(result.get("isError").asBoolean());
        assertTrue(result.get("content").get(0).get("text").asText().contains("No Modelio project is currently open"));
    }

    @Test
    public void malformedJsonProducesParseError() {
        final ObjectNode resp = this.dispatcher.handleRaw("{not json".getBytes());

        assertTrue(resp.has("error"));
        assertEquals(-32700, resp.get("error").get("code").asInt());
    }

    private ObjectNode request(final int id, final String method, final JsonNode params) {
        final ObjectNode req = this.mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        if (params != null) {
            req.set("params", params);
        }
        return req;
    }

}
