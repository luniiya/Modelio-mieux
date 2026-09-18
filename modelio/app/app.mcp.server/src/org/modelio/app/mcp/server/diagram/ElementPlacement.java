package org.modelio.app.mcp.server.diagram;

/**
 * One model element to unmask onto a diagram, at the given canvas coordinates
 * (top-left corner, in diagram/pixel units).
 */
public record ElementPlacement(String elementId, int x, int y) {
}
