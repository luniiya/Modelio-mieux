package org.modelio.app.mcp.server.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.modelio.app.mcp.server.protocol.McpDispatcher;

/**
 * Minimal HTTP transport for the MCP server: a single {@code POST /mcp}
 * endpoint that accepts one JSON-RPC 2.0 request per call.
 * <p>
 * The response is sent as plain {@code application/json} unless the caller
 * sends {@code Accept: text/event-stream}, in which case it is wrapped as a
 * single Server-Sent Events {@code message} frame. Streaming multiple
 * server-initiated messages per call is not implemented in v1.
 */
public final class HttpMcpTransport implements AutoCloseable {

    private static final String PATH = "/mcp";

    private final HttpServer server;
    private final McpDispatcher dispatcher;
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    public HttpMcpTransport(final McpDispatcher dispatcher, final ObjectMapper mapper, final InetSocketAddress address)
            throws IOException {
        this.dispatcher = dispatcher;
        this.mapper = mapper;
        this.server = HttpServer.create(address, 0);
        this.executor = Executors.newCachedThreadPool(r -> {
            final Thread t = new Thread(r, "modelio-mcp-http");
            t.setDaemon(true);
            return t;
        });
        this.server.setExecutor(this.executor);
        this.server.createContext(PATH, this::handle);
    }

    public void start() {
        this.server.start();
    }

    public int getPort() {
        return this.server.getAddress().getPort();
    }

    @Override
    public void close() {
        this.server.stop(0);
        this.executor.shutdownNow();
    }

    private void handle(final HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendPlain(exchange, 405, "Method Not Allowed: use POST " + PATH);
                return;
            }
            final byte[] body = exchange.getRequestBody().readAllBytes();
            final ObjectNode response = this.dispatcher.handleRaw(body);
            if (response == null) {
                exchange.sendResponseHeaders(202, -1);
                return;
            }
            final String accept = exchange.getRequestHeaders().getFirst("Accept");
            if (accept != null && accept.contains("text/event-stream")) {
                sendSse(exchange, response);
            } else {
                sendJson(exchange, 200, response);
            }
        } finally {
            exchange.close();
        }
    }

    private void sendJson(final HttpExchange exchange, final int status, final ObjectNode body) throws IOException {
        final byte[] bytes = this.mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendSse(final HttpExchange exchange, final ObjectNode body) throws IOException {
        final String frame = "event: message\ndata: " + this.mapper.writeValueAsString(body) + "\n\n";
        final byte[] bytes = frame.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendPlain(final HttpExchange exchange, final int status, final String message) throws IOException {
        final byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

}
