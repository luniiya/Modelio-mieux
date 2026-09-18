package org.modelio.app.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.model.ModelBrowser;
import org.modelio.app.mcp.server.protocol.McpTool;

/** Renames one UML element through Modelio's transaction/undo system. */
public final class RenameElementTool implements McpTool {
    private final ModelBrowser browser;

    public RenameElementTool(final ModelBrowser browser) {
        this.browser = browser;
    }

    @Override public String name() { return "rename_element"; }

    @Override public String description() {
        return "Renames a model element by UUID in an undoable Modelio transaction.";
    }

    @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
        final ObjectNode schema = ToolJson.emptySchema(mapper);
        final ObjectNode properties = (ObjectNode) schema.get("properties");
        properties.putObject("id").put("type", "string");
        properties.putObject("name").put("type", "string");
        schema.putArray("required").add("id").add("name");
        return schema;
    }

    @Override public JsonNode call(final JsonNode arguments, final ObjectMapper mapper) {
        final String id = arguments.path("id").asText(null);
        final String name = arguments.path("name").asText(null);
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: id or name");
        }
        return ToolJson.element(mapper, this.browser.renameElement(id, name));
    }
}
