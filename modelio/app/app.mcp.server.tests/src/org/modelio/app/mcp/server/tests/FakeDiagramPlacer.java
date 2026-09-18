package org.modelio.app.mcp.server.tests;

import java.util.ArrayList;
import java.util.List;

import org.modelio.app.mcp.server.diagram.DiagramPlacer;
import org.modelio.app.mcp.server.diagram.ElementPlacement;
import org.modelio.app.mcp.server.diagram.PlacedElement;

/**
 * Test double for {@link DiagramPlacer}, letting Diagram*Tool tests run without a live Modelio
 * session, diagram API or GUI.
 */
final class FakeDiagramPlacer implements DiagramPlacer {

    private final RuntimeException failure;
    private String lastDiagramId;
    private List<ElementPlacement> lastPlacements;
    private boolean lastLayout;
    private String lastTargetPath;
    private String lastFormat;
    private int lastMargin;
    private boolean lastOverwrite;

    FakeDiagramPlacer() {
        this.failure = null;
    }

    FakeDiagramPlacer(final RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public List<PlacedElement> placeElements(final String diagramId, final List<ElementPlacement> placements,
            final boolean layout) {
        failIfNeeded();
        this.lastDiagramId = diagramId;
        this.lastPlacements = placements;
        this.lastLayout = layout;
        final List<PlacedElement> results = new ArrayList<>();
        for (final ElementPlacement placement : placements) {
            results.add(new PlacedElement(placement.elementId(), "Name-" + placement.elementId(), placement.x(),
                    placement.y(), 1));
        }
        return results;
    }

    @Override
    public void exportImage(final String diagramId, final String targetPath, final String format, final int margin,
            final boolean overwrite) {
        failIfNeeded();
        this.lastDiagramId = diagramId;
        this.lastTargetPath = targetPath;
        this.lastFormat = format;
        this.lastMargin = margin;
        this.lastOverwrite = overwrite;
    }

    String lastDiagramId() {
        return this.lastDiagramId;
    }

    List<ElementPlacement> lastPlacements() {
        return this.lastPlacements;
    }

    boolean lastLayout() {
        return this.lastLayout;
    }

    String lastTargetPath() {
        return this.lastTargetPath;
    }

    String lastFormat() {
        return this.lastFormat;
    }

    int lastMargin() {
        return this.lastMargin;
    }

    boolean lastOverwrite() {
        return this.lastOverwrite;
    }

    private void failIfNeeded() {
        if (this.failure != null) {
            throw this.failure;
        }
    }

}
