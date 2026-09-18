package org.modelio.app.mcp.server.tools;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.model.ModelBrowser;
import org.modelio.app.mcp.server.model.PackageInfo;
import org.modelio.app.mcp.server.protocol.McpTool;

/**
 * Read-only MCP tool listing every UML package in the currently open
 * Modelio project.
 */
public final class ListPackagesTool implements McpTool {

    private final ModelBrowser browser;

    public ListPackagesTool(final ModelBrowser browser) {
        this.browser = browser;
    }

    @Override
    public String name() {
        return "list_packages";
    }

    @Override
    public String description() {
        return "Lists every UML package in the currently open Modelio project, "
                + "with each package's UUID, name and owning package name.";
    }

    @Override
    public ObjectNode inputSchema(final ObjectMapper mapper) {
        final ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.createObjectNode());
        schema.putArray("required");
        return schema;
    }

    @Override
    public JsonNode call(final JsonNode arguments, final ObjectMapper mapper) {
        final List<PackageInfo> packages = this.browser.listPackages();
        final ArrayNode arr = mapper.createArrayNode();
        for (final PackageInfo p : packages) {
            final ObjectNode n = mapper.createObjectNode();
            n.put("id", p.id());
            n.put("name", p.name());
            if (p.ownerName() != null) {
                n.put("owner", p.ownerName());
            }
            arr.add(n);
        }
        final ObjectNode result = mapper.createObjectNode();
        result.put("count", packages.size());
        result.set("packages", arr);
        return result;
    }

}
