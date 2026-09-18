package org.modelio.app.mcp.server.model;

/**
 * A read-only, protocol-agnostic snapshot of one UML package, decoupled from
 * the live Modelio kernel object so it can flow through the MCP layer (and
 * be constructed by tests) without a live session.
 *
 * @param id        the package's stable kernel UUID
 * @param name      the package's name
 * @param ownerName the name of the composition owner, or {@code null} if the
 *                  package is a model root or its owner has no name
 */
public record PackageInfo(String id, String name, String ownerName) {
}
