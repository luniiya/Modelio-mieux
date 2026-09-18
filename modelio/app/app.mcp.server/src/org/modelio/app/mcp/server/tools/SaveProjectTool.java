package org.modelio.app.mcp.server.tools;

import java.io.IOException;

import org.eclipse.core.runtime.NullProgressMonitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.platform.project.services.IProjectService;
import org.modelio.app.mcp.server.protocol.McpTool;

/** Persists the currently open Modelio project. */
public final class SaveProjectTool implements McpTool {
    private final IProjectService projectService;

    public SaveProjectTool(final IProjectService projectService) {
        this.projectService = projectService;
    }

    @Override public String name() { return "save_project"; }

    @Override public String description() {
        return "Persists all changes in the currently open Modelio project to its workspace files.";
    }

    @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
        return ToolJson.emptySchema(mapper);
    }

    @Override public JsonNode call(final JsonNode arguments, final ObjectMapper mapper) {
        try {
            this.projectService.saveProject(new NullProgressMonitor());
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to save the current Modelio project: " + e.getMessage(), e);
        }
        final ObjectNode result = mapper.createObjectNode();
        result.put("saved", true);
        final var project = this.projectService.getOpenedProject();
        if (project != null) {
            result.put("project", project.getName());
        }
        return result;
    }
}
