package org.modelio.app.mcp.server.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Renders the {@link ProjectLifecycleResult} shared by {@link CreateProjectTool} and {@link OpenProjectTool}. */
final class ProjectLifecycleJson {
    private ProjectLifecycleJson() {
    }

    static ObjectNode result(final ObjectMapper mapper, final ProjectLifecycleResult result) {
        final ObjectNode node = mapper.createObjectNode();
        node.put("workspace", result.workspace());
        node.put("project", result.project());
        node.put("path", result.path());
        node.put("created", result.created());
        node.put("opened", result.opened());
        return node;
    }
}
