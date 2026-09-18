package org.modelio.app.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.model.ModelBrowser;
import org.modelio.app.mcp.server.protocol.McpTool;

/** Looks up one live Modelio element by UUID. */
public final class GetElementTool implements McpTool {
    private final ModelBrowser browser;

    public GetElementTool(final ModelBrowser browser) {
        this.browser = browser;
    }

    @Override public String name() { return "get_element"; }

    @Override public String description() {
        return "Returns one model element by UUID, including metaclass, owner, type or association endpoints when applicable.";
    }

    @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
        final ObjectNode schema = ToolJson.emptySchema(mapper);
        schema.withObject("properties").putObject("id").put("type", "string");
        schema.putArray("required").add("id");
        return schema;
    }

    @Override public JsonNode call(final JsonNode arguments, final ObjectMapper mapper) {
        final String id = arguments.path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: id");
        }
        return ToolJson.element(mapper, this.browser.getElement(id));
    }
}
