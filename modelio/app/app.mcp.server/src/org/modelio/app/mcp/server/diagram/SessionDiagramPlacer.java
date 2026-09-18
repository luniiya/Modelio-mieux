package org.modelio.app.mcp.server.diagram;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.swt.widgets.Display;

import org.modelio.api.impl.diagrams.DiagramService;
import org.modelio.api.modelio.diagram.IDiagramGraphic;
import org.modelio.api.modelio.diagram.IDiagramHandle;
import org.modelio.api.modelio.diagram.IDiagramService;
import org.modelio.gproject.core.IGProject;
import org.modelio.metamodel.diagrams.AbstractDiagram;
import org.modelio.metamodel.uml.infrastructure.ModelElement;
import org.modelio.platform.project.services.IProjectService;
import org.modelio.vcore.session.api.ICoreSession;
import org.modelio.vcore.session.api.model.IModel;
import org.modelio.vcore.session.api.transactions.ITransaction;
import org.modelio.vcore.smkernel.mapi.MObject;

/**
 * Real {@link DiagramPlacer} implementation, backed by the live Modelio kernel session (for
 * diagram/element lookups, exactly like {@code org.modelio.app.mcp.server.model.SessionModelBrowser})
 * and by {@code IDiagramService} (for the actual unmasking, layout and export).
 * <p>
 * {@code IDiagramService} is not published into the running application's {@link IEclipseContext} the
 * way {@code IProjectService} is; instead it is constructed directly, the same way
 * {@code org.modelio.api.impl.services.ModelioServices} builds its own {@code IDiagramService} lazily
 * (see its {@code initializeServices()}): {@code new DiagramService(eclipseContext)}. Construction is
 * deferred to first use so that by the time it runs, every context service the diagram API needs
 * (tool registry, diagram editors manager, etc.) is guaranteed to already be bound -- the same
 * guarantee {@link #getSessionOrThrow()} relies on for a project actually being open.
 */
public final class SessionDiagramPlacer implements DiagramPlacer {

    private final IProjectService projectService;
    private final IEclipseContext eclipseContext;
    private volatile IDiagramService diagramService;

    public SessionDiagramPlacer(final IProjectService projectService, final IEclipseContext eclipseContext) {
        this.projectService = projectService;
        this.eclipseContext = eclipseContext;
    }

    @Override
    public List<PlacedElement> placeElements(final String diagramId, final List<ElementPlacement> placements,
            final boolean layout) {
        return onUiThread(() -> placeElementsOnUiThread(diagramId, placements, layout));
    }

    private List<PlacedElement> placeElementsOnUiThread(final String diagramId,
            final List<ElementPlacement> placements, final boolean layout) {
        if (placements == null || placements.isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: placements");
        }
        final ICoreSession session = getSessionOrThrow();
        final AbstractDiagram diagram = requireDiagram(session, diagramId);
        final IDiagramService service = getDiagramServiceOrThrow();
        final List<PlacedElement> results = new ArrayList<>(placements.size());
        try (ITransaction transaction = session.getTransactionSupport()
                .createTransaction("MCP place diagram elements")) {
            try (IDiagramHandle handle = service.getDiagramHandle(diagram)) {
                handle.setBatchMode(true);
                try {
                    for (final ElementPlacement placement : placements) {
                        final MObject element = requireElement(session, placement.elementId());
                        final List<IDiagramGraphic> graphics = handle.unmask(element, placement.x(), placement.y());
                        results.add(toPlacedElement(placement, element, graphics));
                    }
                } finally {
                    handle.setBatchMode(false);
                }
                handle.save();
            }
            if (layout) {
                applyLayoutBestEffort(service, diagram);
            }
            transaction.commit();
        }
        return results;
    }

    @Override
    public void exportImage(final String diagramId, final String targetPath, final String format, final int margin,
            final boolean overwrite) {
        onUiThread(() -> {
            exportImageOnUiThread(diagramId, targetPath, format, margin, overwrite);
            return null;
        });
    }

    private void exportImageOnUiThread(final String diagramId, final String targetPath, final String format,
            final int margin, final boolean overwrite) {
        final ICoreSession session = getSessionOrThrow();
        final AbstractDiagram diagram = requireDiagram(session, diagramId);
        final File target = validateTargetFile(targetPath, overwrite);
        final IDiagramService service = getDiagramServiceOrThrow();
        final String resolvedFormat = (format == null || format.isBlank()) ? "PNG" : format.toUpperCase(Locale.ROOT);
        try (IDiagramHandle handle = service.getDiagramHandle(diagram)) {
            handle.saveInFile(resolvedFormat, target.getAbsolutePath(), Math.max(margin, 0));
        }
    }

    /** Runs diagram API work on SWT's UI thread; the diagram service is not thread-safe. */
    private static <T> T onUiThread(final java.util.function.Supplier<T> action) {
        final Display display = Display.getDefault();
        if (display.isDisposed() || display.getThread() == Thread.currentThread()) {
            return action.get();
        }
        final AtomicReference<T> result = new AtomicReference<>();
        final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        display.syncExec(() -> {
            try {
                result.set(action.get());
            } catch (final RuntimeException e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    /**
     * Runs Modelio's layout engine on {@code diagram}, if one is available for its kind. Best-effort:
     * the placements have already been unmasked and saved, so a layout failure is swallowed rather
     * than failing (or rolling back) the whole call.
     */
    private static void applyLayoutBestEffort(final IDiagramService service, final AbstractDiagram diagram) {
        try {
            service.layoutDiagram(diagram);
        } catch (@SuppressWarnings("unused") final RuntimeException e) {
            // No suitable layout algorithm for this diagram kind, or it failed to converge: the
            // elements are already unmasked at their requested position and saved, so just skip it.
        }
    }

    private static PlacedElement toPlacedElement(final ElementPlacement placement, final MObject element,
            final List<IDiagramGraphic> graphics) {
        final String name = (element instanceof ModelElement) ? ((ModelElement) element).getName() : element.getUuid();
        return new PlacedElement(placement.elementId(), name, placement.x(), placement.y(), graphics.size());
    }

    /** Validates {@code targetPath} without touching the filesystem beyond existence checks. */
    private static File validateTargetFile(final String targetPath, final boolean overwrite) {
        if (targetPath == null || targetPath.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: target_path");
        }
        final File target = new File(targetPath);
        if (!target.isAbsolute()) {
            throw new IllegalArgumentException("target_path must be an absolute path: " + targetPath);
        }
        final File parent = target.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            throw new IllegalArgumentException("target_path's parent directory does not exist: " + targetPath);
        }
        if (target.exists() && !overwrite) {
            throw new IllegalArgumentException(
                    "target_path already exists (pass overwrite=true to replace it): " + targetPath);
        }
        return target;
    }

    private static AbstractDiagram requireDiagram(final ICoreSession session, final String diagramId) {
        final MObject object = requireElement(session, diagramId);
        if (!(object instanceof AbstractDiagram)) {
            throw new IllegalArgumentException(
                    "diagram_id must identify a diagram, got " + object.getMClass().getName());
        }
        return (AbstractDiagram) object;
    }

    private static MObject requireElement(final ICoreSession session, final String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: id");
        }
        final MObject object = session.getModel().findById(MObject.class, id, IModel.NODELETED);
        if (object == null) {
            throw new IllegalArgumentException("No model element found with id: " + id);
        }
        return object;
    }

    private IGProject getProjectOrThrow() {
        final IGProject project = this.projectService.getOpenedProject();
        if (project == null) {
            throw new IllegalStateException("No Modelio project is currently open.");
        }
        return project;
    }

    private ICoreSession getSessionOrThrow() {
        getProjectOrThrow();
        return this.projectService.getSession();
    }

    private IDiagramService getDiagramServiceOrThrow() {
        IDiagramService service = this.diagramService;
        if (service == null) {
            synchronized (this) {
                service = this.diagramService;
                if (service == null) {
                    service = new DiagramService(this.eclipseContext);
                    this.diagramService = service;
                }
            }
        }
        return service;
    }

}
