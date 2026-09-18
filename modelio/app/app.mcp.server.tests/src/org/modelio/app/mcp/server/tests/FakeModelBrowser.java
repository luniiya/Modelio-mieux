package org.modelio.app.mcp.server.tests;

import java.util.List;
import java.util.Map;

import org.modelio.app.mcp.server.model.ElementInfo;
import org.modelio.app.mcp.server.model.CreateElementRequest;
import org.modelio.app.mcp.server.model.ModelBrowser;
import org.modelio.app.mcp.server.model.PackageInfo;
import org.modelio.app.mcp.server.model.ProjectInfo;

/**
 * Test double for {@link ModelBrowser}, letting tool/dispatcher tests run
 * without a live Modelio session or GUI.
 */
final class FakeModelBrowser implements ModelBrowser {

    private final List<PackageInfo> packages;
    private final RuntimeException failure;
    private CreateElementRequest lastCreateRequest;

    FakeModelBrowser(final List<PackageInfo> packages) {
        this.packages = packages;
        this.failure = null;
    }

    FakeModelBrowser(final RuntimeException failure) {
        this.packages = null;
        this.failure = failure;
    }

    @Override
    public List<PackageInfo> listPackages() {
        if (this.failure != null) {
            throw this.failure;
        }
        return this.packages;
    }

    @Override
    public ProjectInfo getProject() {
        failIfNeeded();
        return new ProjectInfo("MCP test project");
    }

    @Override
    public List<ElementInfo> listElements(final String metaclass, final String ownerId, final int limit) {
        failIfNeeded();
        return List.of(new ElementInfo("class-1", "Customer", "Class", "pkg-1", "Domain",
                Map.of("childCount", "1")));
    }

    @Override
    public ElementInfo getElement(final String id) {
        failIfNeeded();
        return new ElementInfo(id, "Customer", "Class", "pkg-1", "Domain", Map.of("childCount", "1"));
    }

    @Override
    public ElementInfo createElement(final CreateElementRequest request) {
        failIfNeeded();
        this.lastCreateRequest = request;
        return new ElementInfo("created-1", request.name(), title(request.kind()), request.ownerId(), "Domain",
                Map.of("transaction", "committed"));
    }

    /** @return the request last passed to {@link #createElement(CreateElementRequest)}, or {@code null} if none yet. */
    CreateElementRequest lastCreateRequest() {
        return this.lastCreateRequest;
    }

    @Override
    public ElementInfo renameElement(final String id, final String name) {
        failIfNeeded();
        return new ElementInfo(id, name, "Class", "pkg-1", "Domain", Map.of("transaction", "committed"));
    }

    private static String title(final String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void failIfNeeded() {
        if (this.failure != null) {
            throw this.failure;
        }
    }

}
