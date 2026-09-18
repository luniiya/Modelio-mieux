package org.modelio.app.mcp.server.tools;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.diagram.DiagramPlacer;
import org.modelio.app.mcp.server.diagram.ElementPlacement;
import org.modelio.app.mcp.server.diagram.PlacedElement;
import org.modelio.app.mcp.server.protocol.McpTool;

/**
 * Populates a diagram (such as a bare class/object diagram just created by {@code create_element})
 * by unmasking existing model elements onto it at given canvas coordinates, then saving the diagram.
 * <p>
 * Accepts either a single placement ({@code element_id}/{@code x}/{@code y}) or a batch
 * ({@code placements} array); the two may be combined. Optionally runs Modelio's automatic layout
 * afterwards.
 */
public final class DiagramPlaceElementsTool implements McpTool {

    private final DiagramPlacer placer;

    public DiagramPlaceElementsTool(final DiagramPlacer placer) {
        this.placer = placer;
    }

    @Override public String name() { return "place_diagram_elements"; }

    @Override public String description() {
        return "Unmasks one or more existing model elements onto an existing diagram at given (x, y) canvas "
                + "coordinates, then saves the diagram. Give either a single element_id/x/y or a placements array "
                + "for a batch (or both). Set layout=true to additionally run Modelio's automatic layout on the "
                + "diagram afterwards; this is best-effort and silently skipped if unsupported for that diagram kind.";
    }

    @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
        final ObjectNode schema = ToolJson.emptySchema(mapper);
        final ObjectNode properties = (ObjectNode) schema.get("properties");
        properties.putObject("diagram_id").put("type", "string");
        properties.putObject("element_id").put("type", "string");
        properties.putObject("x").put("type", "integer");
        properties.putObject("y").put("type", "integer");
        final ObjectNode placements = properties.putObject("placements");
        placements.put("type", "array");
        final ObjectNode item = placements.putObject("items");
        item.put("type", "object");
        final ObjectNode itemProperties = item.putObject("properties");
        itemProperties.putObject("element_id").put("type", "string");
        itemProperties.putObject("x").put("type", "integer");
        itemProperties.putObject("y").put("type", "integer");
        item.putArray("required").add("element_id").add("x").add("y");
        final ObjectNode layout = properties.putObject("layout");
        layout.put("type", "boolean");
        layout.put("default", false);
        schema.putArray("required").add("diagram_id");
        return schema;
    }

    @Override public JsonNode call(final JsonNode arguments, final ObjectMapper mapper) {
        final String diagramId = text(arguments, "diagram_id");
        if (diagramId == null) {
            throw new IllegalArgumentException("Missing required argument: diagram_id");
        }
        final List<ElementPlacement> placements = readPlacements(arguments);
        final boolean layout = arguments.path("layout").asBoolean(false);

        final List<PlacedElement> placed = this.placer.placeElements(diagramId, placements, layout);

        final ObjectNode result = mapper.createObjectNode();
        result.put("diagramId", diagramId);
        result.put("layoutApplied", layout);
        final ArrayNode placedNode = result.putArray("placements");
        for (final PlacedElement element : placed) {
            final ObjectNode node = placedNode.addObject();
            node.put("elementId", element.elementId());
            node.put("elementName", element.elementName());
            node.put("x", element.x());
            node.put("y", element.y());
            node.put("graphicCount", element.graphicCount());
        }
        return result;
    }

    private static List<ElementPlacement> readPlacements(final JsonNode arguments) {
        final List<ElementPlacement> placements = new ArrayList<>();
        final JsonNode array = arguments.get("placements");
        if (array != null && !array.isNull()) {
            if (!array.isArray()) {
                throw new IllegalArgumentException("placements must be an array");
            }
            for (final JsonNode item : array) {
                placements.add(readPlacement(item));
            }
        }
        final String elementId = text(arguments, "element_id");
        if (elementId != null) {
            placements.add(new ElementPlacement(elementId, intValue(arguments, "x"), intValue(arguments, "y")));
        }
        if (placements.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required argument: either element_id/x/y or a non-empty placements array");
        }
        return placements;
    }

    private static ElementPlacement readPlacement(final JsonNode item) {
        final String elementId = text(item, "element_id");
        if (elementId == null) {
            throw new IllegalArgumentException("Missing required argument: placements[].element_id");
        }
        return new ElementPlacement(elementId, intValue(item, "x"), intValue(item, "y"));
    }

    private static int intValue(final JsonNode node, final String name) {
        if (!node.hasNonNull(name)) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return node.get(name).asInt();
    }

    private static String text(final JsonNode node, final String name) {
        return node.hasNonNull(name) ? node.get(name).asText() : null;
    }
}
