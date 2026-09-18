package org.modelio.app.mcp.server.model;

import java.util.List;

/**
 * Read-only access to the currently open Modelio model, as needed by MCP
 * tools. Kept separate from the kernel API so tools can be unit tested
 * against a fake implementation, without a live session or GUI.
 */
public interface ModelBrowser {

    /**
     * @return every UML package in the currently open project.
     * @throws IllegalStateException if no project is currently open.
     */
    List<PackageInfo> listPackages();

    /** @return basic information about the currently open project. */
    ProjectInfo getProject();

    /** List a bounded set of model elements, optionally filtered by owner. */
    List<ElementInfo> listElements(String metaclass, String ownerId, int limit);

    /** Look up one model element by its stable UUID. */
    ElementInfo getElement(String id);

    /** Create a supported UML element inside an undoable Modelio transaction. */
    ElementInfo createElement(CreateElementRequest request);

    /** Rename a UML element inside an undoable Modelio transaction. */
    ElementInfo renameElement(String id, String name);

}
