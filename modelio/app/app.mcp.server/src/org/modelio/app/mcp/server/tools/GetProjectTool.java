package org.modelio.app.mcp.server.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.model.ModelBrowser;
import org.modelio.app.mcp.server.model.ProjectInfo;
import org.modelio.app.mcp.server.protocol.McpTool;

/** Reports which project the live Modelio GUI currently has open. */
public final class GetProjectTool implements McpTool {
    private final ModelBrowser browser;

    public GetProjectTool(final ModelBrowser browser) {
        this.browser = browser;
    }

    @Override public String name() { return "get_project"; }

    @Override public String description() {
        return "Returns the name of the project currently open in Modelio.";
    }

    @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
        return ToolJson.emptySchema(mapper);
    }

    @Override public JsonNode call(final JsonNode arguments, final ObjectMapper mapper) {
        final ProjectInfo project = this.browser.getProject();
        final ObjectNode result = mapper.createObjectNode();
        result.put("name", project.name());
        return result;
    }
}
