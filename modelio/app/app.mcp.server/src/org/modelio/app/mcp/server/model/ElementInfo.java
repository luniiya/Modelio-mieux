package org.modelio.app.mcp.server.model;

import java.util.Map;

/** Transport-neutral description of one Modelio model element. */
public record ElementInfo(String id, String name, String metaclass, String ownerId,
        String ownerName, Map<String, String> properties) {
}
