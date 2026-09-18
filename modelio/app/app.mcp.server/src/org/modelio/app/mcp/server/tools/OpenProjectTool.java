package org.modelio.app.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.protocol.McpTool;

/**
 * Opens an already-existing local Modelio project from the current
 * workspace, by name.
 * <p>
 * Never creates, deletes or overwrites anything -- use {@link CreateProjectTool}
 * to create a new project (optionally with {@code open_existing} to fall
 * back to opening this way when the project is already there).
 */
public final class OpenProjectTool implements McpTool {

    private final ProjectLifecycleService service;

    public OpenProjectTool(final ProjectLifecycleService service) {
        this.service = service;
    }

    @Override public String name() { return "open_project"; }

    @Override public String description() {
        return "Opens an existing local Modelio project by name from the current workspace. Fails if no such "
                + "project exists in the workspace; it never creates one.";
    }

    @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
        final ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        final ObjectNode properties = schema.putObject("properties");
        properties.putObject("name").put("type", "string");
        schema.putArray("required").add("name");
        return schema;
    }

    @Override public JsonNode call(final JsonNode arguments, final ObjectMapper mapper) {
        final String name = arguments.hasNonNull("name") ? arguments.get("name").asText() : null;
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: name");
        }
        return ProjectLifecycleJson.result(mapper, this.service.openProject(name));
    }
}
