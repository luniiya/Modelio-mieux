package org.modelio.app.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.diagram.DiagramPlacer;
import org.modelio.app.mcp.server.protocol.McpTool;

/**
 * Exports a diagram to an image file at a caller-supplied absolute path, defaulting to PNG.
 * <p>
 * Refuses to silently overwrite an existing file at {@code target_path}: pass {@code overwrite=true}
 * to explicitly replace one.
 */
public final class DiagramExportImageTool implements McpTool {

    private static final int DEFAULT_MARGIN = 10;

    private final DiagramPlacer placer;

    public DiagramExportImageTool(final DiagramPlacer placer) {
        this.placer = placer;
    }

    @Override public String name() { return "export_diagram_image"; }

    @Override public String description() {
        return "Exports a diagram as an image file (PNG, BMP, JPEG or GIF; default PNG) at the given absolute "
                + "target_path. Fails rather than silently overwriting an existing file unless overwrite=true is "
                + "passed; target_path's parent directory must already exist.";
    }

    @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
        final ObjectNode schema = ToolJson.emptySchema(mapper);
        final ObjectNode properties = (ObjectNode) schema.get("properties");
        properties.putObject("diagram_id").put("type", "string");
        properties.putObject("target_path").put("type", "string");
        final ObjectNode format = properties.putObject("format");
        format.put("type", "string");
        format.putArray("enum").add("PNG").add("BMP").add("JPEG").add("GIF");
        format.put("default", "PNG");
        final ObjectNode margin = properties.putObject("margin");
        margin.put("type", "integer");
        margin.put("default", DEFAULT_MARGIN);
        final ObjectNode overwrite = properties.putObject("overwrite");
        overwrite.put("type", "boolean");
        overwrite.put("default", false);
        schema.putArray("required").add("diagram_id").add("target_path");
        return schema;
    }

    @Override public JsonNode call(final JsonNode arguments, final ObjectMapper mapper) {
        final String diagramId = text(arguments, "diagram_id");
        final String targetPath = text(arguments, "target_path");
        if (diagramId == null) {
            throw new IllegalArgumentException("Missing required argument: diagram_id");
        }
        if (targetPath == null) {
            throw new IllegalArgumentException("Missing required argument: target_path");
        }
        final String format = arguments.hasNonNull("format") ? arguments.get("format").asText() : "PNG";
        final int margin = arguments.path("margin").asInt(DEFAULT_MARGIN);
        final boolean overwrite = arguments.path("overwrite").asBoolean(false);

        this.placer.exportImage(diagramId, targetPath, format, margin, overwrite);

        final ObjectNode result = mapper.createObjectNode();
        result.put("diagramId", diagramId);
        result.put("targetPath", targetPath);
        result.put("format", format);
        result.put("margin", margin);
        result.put("overwrite", overwrite);
        return result;
    }

    private static String text(final JsonNode node, final String name) {
        return node.hasNonNull(name) ? node.get(name).asText() : null;
    }
}
