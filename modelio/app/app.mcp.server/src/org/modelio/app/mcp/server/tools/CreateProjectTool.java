package org.modelio.app.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.protocol.McpTool;

/**
 * Creates a new local Modelio project under the current workspace and opens
 * it, mirroring the create-then-open flow of the GUI's New Project wizard
 * and the {@code --create} command line option.
 * <p>
 * Never deletes or overwrites anything: if a project already exists at the
 * target path, creation is rejected unless {@code open_existing} is set, in
 * which case the existing project is opened rather than recreated.
 */
public final class CreateProjectTool implements McpTool {

    private final ProjectLifecycleService service;

    public CreateProjectTool(final ProjectLifecycleService service) {
        this.service = service;
    }

    @Override public String name() { return "create_project"; }

    @Override public String description() {
        return "Creates a new local Modelio project (name required, e.g. 'benchmark') under the current workspace "
                + "and opens it. Fails if a project already exists at that path unless open_existing is true, in "
                + "which case the existing project is opened instead of being recreated -- a project is never "
                + "deleted or overwritten.";
    }

    @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
        final ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        final ObjectNode properties = schema.putObject("properties");
        properties.putObject("name").put("type", "string");
        final ObjectNode openExisting = properties.putObject("open_existing");
        openExisting.put("type", "boolean");
        openExisting.put("default", false);
        schema.putArray("required").add("name");
        return schema;
    }

    @Override public JsonNode call(final JsonNode arguments, final ObjectMapper mapper) {
        final String name = text(arguments, "name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: name");
        }
        final boolean openExisting = arguments.path("open_existing").asBoolean(false);
        return ProjectLifecycleJson.result(mapper, this.service.createProject(name, openExisting));
    }

    private static String text(final JsonNode arguments, final String name) {
        return arguments.hasNonNull(name) ? arguments.get(name).asText() : null;
    }
}
