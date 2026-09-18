package org.modelio.app.mcp.server.diagram;

/**
 * Result of unmasking one {@link ElementPlacement} onto a diagram.
 *
 * @param graphicCount number of diagram graphics created by the unmask (0 if the element was already
 *                      masked/hidden and could not be represented, more than 1 for elements that unmask
 *                      as several graphics such as a compartment plus its label).
 */
public record PlacedElement(String elementId, String elementName, int x, int y, int graphicCount) {
}
