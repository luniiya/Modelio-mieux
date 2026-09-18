package org.modelio.app.mcp.server.tools;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.model.ElementInfo;

final class ToolJson {
    private ToolJson() {
    }

    static ObjectNode emptySchema(final ObjectMapper mapper) {
        final ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.createObjectNode());
        schema.putArray("required");
        return schema;
    }

    static ObjectNode element(final ObjectMapper mapper, final ElementInfo info) {
        final ObjectNode node = mapper.createObjectNode();
        node.put("id", info.id());
        node.put("name", info.name());
        node.put("metaclass", info.metaclass());
        if (info.ownerId() != null) {
            node.put("ownerId", info.ownerId());
            node.put("ownerName", info.ownerName());
        }
        final ObjectNode properties = mapper.createObjectNode();
        for (final Map.Entry<String, String> entry : info.properties().entrySet()) {
            properties.put(entry.getKey(), entry.getValue());
        }
        node.set("properties", properties);
        return node;
    }
}
