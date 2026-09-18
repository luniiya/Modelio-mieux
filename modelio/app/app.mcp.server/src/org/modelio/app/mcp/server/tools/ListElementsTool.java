package org.modelio.app.mcp.server.tools;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.model.ElementInfo;
import org.modelio.app.mcp.server.model.ModelBrowser;
import org.modelio.app.mcp.server.protocol.McpTool;

/** Lists UML model elements from the project currently open in Modelio. */
public final class ListElementsTool implements McpTool {
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;
    private final ModelBrowser browser;

    public ListElementsTool(final ModelBrowser browser) {
        this.browser = browser;
    }

    @Override public String name() { return "list_elements"; }

    @Override public String description() {
        return "Lists model elements. Filter by metaclass (model_element, package, class, attribute, operation, association, "
                + "interaction, sequence_diagram, lifeline, message, diagram), owner UUID, and limit.";
    }

    @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
        final ObjectNode schema = ToolJson.emptySchema(mapper);
        final ObjectNode properties = (ObjectNode) schema.get("properties");
        final ObjectNode metaclass = properties.putObject("metaclass");
        metaclass.put("type", "string");
        metaclass.putArray("enum").add("model_element").add("package").add("class")
                .add("attribute").add("operation").add("association").add("interaction")
                .add("sequence_diagram").add("lifeline").add("message").add("diagram");
        metaclass.put("default", "model_element");
        properties.putObject("owner_id").put("type", "string");
        final ObjectNode limit = properties.putObject("limit");
        limit.put("type", "integer");
        limit.put("minimum", 1);
        limit.put("maximum", MAX_LIMIT);
        limit.put("default", DEFAULT_LIMIT);
        return schema;
    }

    @Override public JsonNode call(final JsonNode arguments, final ObjectMapper mapper) {
        final String metaclass = arguments.path("metaclass").asText("model_element");
        final String ownerId = arguments.hasNonNull("owner_id") ? arguments.get("owner_id").asText() : null;
        final int limit = arguments.path("limit").asInt(DEFAULT_LIMIT);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        final List<ElementInfo> elements = this.browser.listElements(metaclass, ownerId, limit);
        final ArrayNode array = mapper.createArrayNode();
        for (final ElementInfo element : elements) {
            array.add(ToolJson.element(mapper, element));
        }
        final ObjectNode result = mapper.createObjectNode();
        result.put("count", elements.size());
        result.set("elements", array);
        return result;
    }
}
