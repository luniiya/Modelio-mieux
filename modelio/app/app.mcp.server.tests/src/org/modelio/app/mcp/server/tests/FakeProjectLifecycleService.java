package org.modelio.app.mcp.server.tests;

import org.modelio.app.mcp.server.tools.ProjectLifecycleResult;
import org.modelio.app.mcp.server.tools.ProjectLifecycleService;

/**
 * Test double for {@link ProjectLifecycleService}, letting
 * {@code CreateProjectTool}/{@code OpenProjectTool} be exercised without a
 * live Modelio workspace or GUI.
 */
final class FakeProjectLifecycleService implements ProjectLifecycleService {

    private static final String WORKSPACE = "/home/user/modelio-workspace";

    private final RuntimeException failure;
    private String lastCreateName;
    private boolean lastOpenExisting;
    private String lastOpenName;

    FakeProjectLifecycleService() {
        this.failure = null;
    }

    FakeProjectLifecycleService(final RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public ProjectLifecycleResult createProject(final String name, final boolean openExisting) {
        failIfNeeded();
        this.lastCreateName = name;
        this.lastOpenExisting = openExisting;
        return new ProjectLifecycleResult(WORKSPACE, name, WORKSPACE + "/" + name, !openExisting, true);
    }

    @Override
    public ProjectLifecycleResult openProject(final String name) {
        failIfNeeded();
        this.lastOpenName = name;
        return new ProjectLifecycleResult(WORKSPACE, name, WORKSPACE + "/" + name, false, true);
    }

    /** @return the {@code name} last passed to {@link #createProject(String, boolean)}, or {@code null} if none yet. */
    String lastCreateName() {
        return this.lastCreateName;
    }

    /** @return the {@code openExisting} last passed to {@link #createProject(String, boolean)}. */
    boolean lastOpenExisting() {
        return this.lastOpenExisting;
    }

    /** @return the {@code name} last passed to {@link #openProject(String)}, or {@code null} if none yet. */
    String lastOpenName() {
        return this.lastOpenName;
    }

    private void failIfNeeded() {
        if (this.failure != null) {
            throw this.failure;
        }
    }

}
