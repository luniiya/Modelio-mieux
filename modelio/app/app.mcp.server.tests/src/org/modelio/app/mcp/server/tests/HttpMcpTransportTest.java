package org.modelio.app.mcp.server.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.model.PackageInfo;
import org.modelio.app.mcp.server.protocol.McpDispatcher;
import org.modelio.app.mcp.server.tools.ListPackagesTool;
import org.modelio.app.mcp.server.transport.HttpMcpTransport;

/**
 * End-to-end tests over real HTTP, against the transport bound to an
 * ephemeral port -- no live Modelio session or GUI required.
 */
public class HttpMcpTransportTest {

    private HttpMcpTransport transport;
    private ObjectMapper mapper;
    private HttpClient client;

    @Before
    public void setUp() throws Exception {
        this.mapper = new ObjectMapper();
        final McpDispatcher dispatcher = new McpDispatcher(this.mapper);
        dispatcher.registerTool(
                new ListPackagesTool(new FakeModelBrowser(List.of(new PackageInfo("id-1", "Analysis", null)))));

        this.transport = new HttpMcpTransport(dispatcher, this.mapper, new InetSocketAddress("127.0.0.1", 0));
        this.transport.start();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @After
    public void tearDown() {
        if (this.transport != null) {
            this.transport.close();
        }
    }

    @Test
    public void initializeHandshakeOverHttp() throws Exception {
        final ObjectNode req = this.mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", 1);
        req.put("method", "initialize");
        req.set("params", this.mapper.createObjectNode());

        final HttpResponse<String> resp = post(req, "application/json");

        assertEquals(200, resp.statusCode());
        final JsonNode body = this.mapper.readTree(resp.body());
        assertEquals("2.0", body.get("jsonrpc").asText());
        assertTrue(body.get("result").has("protocolVersion"));
    }

    @Test
    public void toolsCallOverSseAccept() throws Exception {
        final ObjectNode params = this.mapper.createObjectNode();
        params.put("name", "list_packages");
        params.set("arguments", this.mapper.createObjectNode());
        final ObjectNode req = this.mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", 2);
        req.put("method", "tools/call");
        req.set("params", params);

        final HttpResponse<String> resp = post(req, "text/event-stream");

        assertEquals(200, resp.statusCode());
        assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("text/event-stream"));
        assertTrue(resp.body().contains("\"Analysis\""));
    }

    @Test
    public void malformedJsonReturnsParseErrorOverHttp() throws Exception {
        final HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + this.transport.getPort() + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{not json"))
                .build();

        final HttpResponse<String> resp = this.client.send(httpReq, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        final JsonNode body = this.mapper.readTree(resp.body());
        assertEquals(-32700, body.get("error").get("code").asInt());
    }

    @Test
    public void notificationGetsAcceptedWithNoBody() throws Exception {
        final ObjectNode req = this.mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("method", "notifications/initialized");

        final HttpResponse<String> resp = post(req, "application/json");

        assertEquals(202, resp.statusCode());
        assertTrue(resp.body().isEmpty());
    }

    @Test
    public void getIsRejectedAsMethodNotAllowed() throws Exception {
        final HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + this.transport.getPort() + "/mcp"))
                .GET()
                .build();

        final HttpResponse<String> resp = this.client.send(httpReq, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, resp.statusCode());
    }

    private HttpResponse<String> post(final ObjectNode body, final String accept) throws Exception {
        final HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + this.transport.getPort() + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", accept)
                .POST(HttpRequest.BodyPublishers.ofString(this.mapper.writeValueAsString(body)))
                .build();
        return this.client.send(httpReq, HttpResponse.BodyHandlers.ofString());
    }

}
