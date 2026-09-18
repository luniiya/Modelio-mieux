package org.modelio.app.mcp.server.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.modelio.app.mcp.server.tools.ProjectLifecycleResult;
import org.modelio.app.mcp.server.tools.ProjectLifecycleService;
import org.modelio.gproject.auth.GProjectAuthenticationException;
import org.modelio.gproject.core.IGProject;
import org.modelio.platform.project.creation.BasicProjectCreationDataModel;
import org.modelio.platform.project.creation.ProjectNameValidator;
import org.modelio.platform.project.services.IProjectService;

/**
 * Real {@link ProjectLifecycleService} implementation, backed by the live
 * {@link IProjectService} obtained through the running application's
 * {@code IEclipseContext} -- the same service the GUI's New Project wizard
 * and the {@code --create}/{@code --open} command line options use.
 * <p>
 * Runs entirely on the caller's thread: {@link IProjectService#createProject}
 * and {@link IProjectService#openProject} do not require the SWT UI thread
 * (unlike the interactive wizards, which additionally drive a progress
 * dialog on it), so this class must only ever be invoked off the SWT thread,
 * e.g. from the MCP HTTP transport's own worker threads. A
 * {@link NullProgressMonitor} is used throughout since there is no UI to
 * report progress to.
 */
public final class SessionProjectLifecycleService implements ProjectLifecycleService {

    /** Marker file at the root of a project directory (see {@code ProjectFileStructure#getProjectConfFile()}). */
    private static final String PROJECT_CONF_FILE_NAME = "project.conf";

    private final IProjectService projectService;

    public SessionProjectLifecycleService(final IProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public ProjectLifecycleResult createProject(final String name, final boolean openExisting) {
        final String projectName = validName(name);
        final Path workspace = requireWorkspace();
        final Path projectPath = resolveProjectPath(workspace, projectName);

        if (Files.exists(projectPath)) {
            if (!openExisting) {
                throw new IllegalStateException("A project named '" + projectName + "' already exists at '"
                        + projectPath + "'. Pass open_existing=true to open it instead of creating a new one.");
            }
            return open(projectName, workspace, projectPath, false);
        }

        final BasicProjectCreationDataModel data = new BasicProjectCreationDataModel(workspace);
        data.setProjectName(projectName);
        try {
            this.projectService.createProject(data, new NullProgressMonitor());
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to create project '" + projectName + "': " + e.getMessage(), e);
        }
        return open(projectName, workspace, projectPath, true);
    }

    @Override
    public ProjectLifecycleResult openProject(final String name) {
        final String projectName = validName(name);
        final Path workspace = requireWorkspace();
        final Path projectPath = resolveProjectPath(workspace, projectName);
        if (!Files.isRegularFile(projectPath.resolve(PROJECT_CONF_FILE_NAME))) {
            // IProjectService#openProject(String,...) silently no-ops instead of failing when there is no such
            // project, so this must be checked up front to give the caller an actual error.
            throw new IllegalArgumentException(
                    "No project named '" + projectName + "' found in workspace '" + workspace + "'.");
        }
        return open(projectName, workspace, projectPath, false);
    }

    /** Opens the project by name; no authentication data is used since this bridge only ever targets local projects. */
    private ProjectLifecycleResult open(final String projectName, final Path workspace, final Path projectPath,
            final boolean created) {
        try {
            this.projectService.openProject(projectName, null, new NullProgressMonitor());
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to open project '" + projectName + "': " + e.getMessage(), e);
        } catch (final GProjectAuthenticationException e) {
            throw new IllegalStateException(
                    "Authentication failed while opening project '" + projectName + "': " + e.getMessage(), e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Opening project '" + projectName + "' was interrupted.", e);
        }
        final IGProject opened = this.projectService.getOpenedProject();
        if (opened == null) {
            throw new IllegalStateException("Project '" + projectName + "' could not be opened.");
        }
        return new ProjectLifecycleResult(workspace.toString(), opened.getName(), projectPath.toString(), created,
                true);
    }

    private Path requireWorkspace() {
        final Path workspace = this.projectService.getWorkspace();
        if (workspace == null) {
            throw new IllegalStateException("No Modelio workspace is currently configured.");
        }
        return workspace;
    }

    private static String validName(final String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: name");
        }
        final String trimmed = name.trim();
        if (!ProjectNameValidator.PROJECT_NAME_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid project name: '" + trimmed
                    + "'. Only letters, digits, '.', '_' and spaces are allowed.");
        }
        return trimmed;
    }

    /** Resolves {@code name} to a direct child of {@code workspace}, rejecting anything that would escape it. */
    private static Path resolveProjectPath(final Path workspace, final String name) {
        final Path resolved = workspace.resolve(name).normalize();
        if (!workspace.normalize().equals(resolved.getParent())) {
            throw new IllegalArgumentException("Invalid project name: '" + name + "'.");
        }
        return resolved;
    }

}
