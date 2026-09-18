package org.modelio.app.mcp.server.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.tools.DiagramExportImageTool;
import org.modelio.app.mcp.server.tools.DiagramPlaceElementsTool;

public class DiagramToolsTest {

    private ObjectMapper mapper;
    private FakeDiagramPlacer placer;

    @Before
    public void setUp() {
        this.mapper = new ObjectMapper();
        this.placer = new FakeDiagramPlacer();
    }

    @Test
    public void placesSingleElement() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("diagram_id", "diag-1");
        args.put("element_id", "elem-1");
        args.put("x", 10);
        args.put("y", 20);
        final JsonNode result = new DiagramPlaceElementsTool(this.placer).call(args, this.mapper);
        assertEquals("diag-1", result.get("diagramId").asText());
        assertFalse(result.get("layoutApplied").asBoolean());
        assertEquals(1, result.get("placements").size());
        assertEquals("elem-1", result.get("placements").get(0).get("elementId").asText());
        assertEquals(10, result.get("placements").get(0).get("x").asInt());
        assertEquals(20, result.get("placements").get(0).get("y").asInt());
        assertEquals("diag-1", this.placer.lastDiagramId());
        assertEquals(1, this.placer.lastPlacements().size());
        assertFalse(this.placer.lastLayout());
    }

    @Test
    public void placesBatchWithLayout() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("diagram_id", "diag-1");
        final ArrayNode placements = args.putArray("placements");
        final ObjectNode first = placements.addObject();
        first.put("element_id", "elem-1");
        first.put("x", 0);
        first.put("y", 0);
        final ObjectNode second = placements.addObject();
        second.put("element_id", "elem-2");
        second.put("x", 100);
        second.put("y", 50);
        args.put("layout", true);
        final JsonNode result = new DiagramPlaceElementsTool(this.placer).call(args, this.mapper);
        assertEquals(2, result.get("placements").size());
        assertTrue(result.get("layoutApplied").asBoolean());
        assertTrue(this.placer.lastLayout());
        assertEquals(2, this.placer.lastPlacements().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void placeRequiresDiagramId() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("element_id", "elem-1");
        args.put("x", 0);
        args.put("y", 0);
        new DiagramPlaceElementsTool(this.placer).call(args, this.mapper);
    }

    @Test(expected = IllegalArgumentException.class)
    public void placeRequiresAtLeastOnePlacement() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("diagram_id", "diag-1");
        new DiagramPlaceElementsTool(this.placer).call(args, this.mapper);
    }

    @Test(expected = IllegalArgumentException.class)
    public void batchPlacementRequiresElementId() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("diagram_id", "diag-1");
        final ArrayNode placements = args.putArray("placements");
        final ObjectNode item = placements.addObject();
        item.put("x", 0);
        item.put("y", 0);
        new DiagramPlaceElementsTool(this.placer).call(args, this.mapper);
    }

    @Test(expected = IllegalArgumentException.class)
    public void batchPlacementRequiresCoordinates() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("diagram_id", "diag-1");
        final ArrayNode placements = args.putArray("placements");
        final ObjectNode item = placements.addObject();
        item.put("element_id", "elem-1");
        new DiagramPlaceElementsTool(this.placer).call(args, this.mapper);
    }

    @Test
    public void exportsImageWithDefaults() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("diagram_id", "diag-1");
        args.put("target_path", "/tmp/out.png");
        final JsonNode result = new DiagramExportImageTool(this.placer).call(args, this.mapper);
        assertEquals("/tmp/out.png", result.get("targetPath").asText());
        assertEquals("PNG", result.get("format").asText());
        assertFalse(result.get("overwrite").asBoolean());
        assertEquals("diag-1", this.placer.lastDiagramId());
        assertEquals("/tmp/out.png", this.placer.lastTargetPath());
        assertEquals("PNG", this.placer.lastFormat());
        assertEquals(10, this.placer.lastMargin());
        assertFalse(this.placer.lastOverwrite());
    }

    @Test
    public void exportsImageWithOverwriteAndFormat() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("diagram_id", "diag-1");
        args.put("target_path", "/tmp/out.bmp");
        args.put("format", "BMP");
        args.put("margin", 25);
        args.put("overwrite", true);
        new DiagramExportImageTool(this.placer).call(args, this.mapper);
        assertEquals("BMP", this.placer.lastFormat());
        assertEquals(25, this.placer.lastMargin());
        assertTrue(this.placer.lastOverwrite());
    }

    @Test(expected = IllegalArgumentException.class)
    public void exportRequiresDiagramId() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("target_path", "/tmp/out.png");
        new DiagramExportImageTool(this.placer).call(args, this.mapper);
    }

    @Test(expected = IllegalArgumentException.class)
    public void exportRequiresTargetPath() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("diagram_id", "diag-1");
        new DiagramExportImageTool(this.placer).call(args, this.mapper);
    }
}
