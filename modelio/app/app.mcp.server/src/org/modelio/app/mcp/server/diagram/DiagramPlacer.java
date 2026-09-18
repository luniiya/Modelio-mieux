package org.modelio.app.mcp.server.diagram;

import java.util.List;

/**
 * Populates an existing diagram (typically one just created through {@code create_element}) with
 * graphical representations of existing model elements, so an agent can turn a bare class/object
 * diagram into an actual picture without a human in the GUI.
 * <p>
 * Kept separate from {@link org.modelio.app.mcp.server.model.ModelBrowser}: it is backed by the
 * diagram API ({@code IDiagramService}/{@code IDiagramHandle}) rather than the plain kernel session,
 * and kept as its own interface (rather than folded into {@code ModelBrowser}) so tools can be unit
 * tested against a fake implementation, without a live session or GUI.
 */
public interface DiagramPlacer {

    /**
     * Unmasks each of {@code placements} onto the diagram identified by {@code diagramId}, at its
     * requested (x, y) canvas position, then saves the diagram.
     * <p>
     * When {@code layout} is {@code true}, additionally asks Modelio's layout engine to rearrange the
     * diagram afterwards. This is best-effort: not every diagram kind has a suitable layout algorithm,
     * so a failure to lay out never undoes the already-saved placements.
     *
     * @param diagramId  UUID of the diagram to place elements onto.
     * @param placements the elements to unmask, and where; must not be {@code null} or empty.
     * @param layout     whether to run the automatic layout engine on the diagram afterwards.
     * @return one {@link PlacedElement} per input placement, in the same order.
     * @throws IllegalArgumentException if {@code diagramId} is missing or does not identify a diagram,
     *                                   {@code placements} is missing/empty, or an element id does not
     *                                   resolve to a live model element.
     * @throws IllegalStateException    if no project is currently open.
     */
    List<PlacedElement> placeElements(String diagramId, List<ElementPlacement> placements, boolean layout);

    /**
     * Exports the diagram identified by {@code diagramId} as an image file at {@code targetPath}.
     *
     * @param diagramId  UUID of the diagram to export.
     * @param targetPath absolute path of the file to write; must not already exist unless
     *                   {@code overwrite} is {@code true}, and its parent directory must exist.
     * @param format     one of {@code PNG}, {@code BMP}, {@code JPEG}, {@code GIF} (case-insensitive);
     *                   {@code null}/blank defaults to {@code PNG}.
     * @param margin     margin, in pixels, to add around the diagram's content.
     * @param overwrite  whether an existing file at {@code targetPath} may be replaced.
     * @throws IllegalArgumentException if {@code diagramId}/{@code targetPath} are missing,
     *                                   {@code diagramId} does not identify a diagram, {@code targetPath}
     *                                   is not absolute, its parent directory does not exist, or the
     *                                   target file already exists and {@code overwrite} is {@code false}.
     * @throws IllegalStateException    if no project is currently open.
     */
    void exportImage(String diagramId, String targetPath, String format, int margin, boolean overwrite);

}
