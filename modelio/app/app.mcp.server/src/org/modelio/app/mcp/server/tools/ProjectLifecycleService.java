package org.modelio.app.mcp.server.tools;

/**
 * Abstraction over the project lifecycle operations ({@code create_project},
 * {@code open_project}) needed by the MCP bridge, decoupled from the live
 * {@code IProjectService} so {@link CreateProjectTool} and
 * {@link OpenProjectTool} can be exercised with a test double, exactly like
 * {@link org.modelio.app.mcp.server.model.ModelBrowser} does for the model
 * inspection/editing tools.
 */
public interface ProjectLifecycleService {

    /**
     * Creates a new local project named {@code name} under the current
     * workspace and opens it.
     * <p>
     * Never deletes or overwrites anything: if a project already exists at
     * that path it is left untouched -- opened as-is when
     * {@code openExisting} is {@code true}, otherwise rejected.
     *
     * @param name         the project name (also its workspace-relative directory name).
     * @param openExisting when {@code true}, open the already-existing project instead of failing.
     */
    ProjectLifecycleResult createProject(String name, boolean openExisting);

    /**
     * Opens the existing local project named {@code name} from the current
     * workspace. Never creates, deletes or overwrites anything.
     *
     * @param name the project name (also its workspace-relative directory name).
     */
    ProjectLifecycleResult openProject(String name);

}
