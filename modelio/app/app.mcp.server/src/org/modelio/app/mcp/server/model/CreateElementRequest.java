package org.modelio.app.mcp.server.model;

/**
 * Arguments shared by the supported UML creation operations.
 * <p>
 * Not every field applies to every {@code kind}; see {@code CreateElementTool}
 * for the exact combination required by each one. Roughly:
 * <ul>
 * <li>{@code package}/{@code class}/{@code instance}/{@code class_diagram}/{@code object_diagram}: {@code name}, {@code ownerId}.</li>
 * <li>{@code attribute}: {@code name}, {@code ownerId} (owning classifier), optional {@code typeId}.</li>
 * <li>{@code instance}: {@code ownerId} (owning package), optional {@code typeId} (classifier typing the instance).</li>
 * <li>{@code association}: {@code ownerId} (source classifier), {@code targetId}, optional {@code roleName},
 * {@code aggregation} ({@code association}/{@code aggregation}/{@code composition}), and the multiplicity fields.</li>
 * <li>{@code generalization}: {@code ownerId} (sub-type), {@code targetId} (super-type).</li>
 * <li>{@code link}: {@code ownerId} (source instance), {@code targetId} (destination instance), optional {@code roleName}.</li>
 * <li>{@code slot}: {@code ownerId} (instance), {@code attributeId} (attribute being instantiated), optional {@code value}.</li>
 * </ul>
 */
public record CreateElementRequest(String kind, String name, String ownerId, String typeId, String targetId,
        String roleName, String attributeId, String value, String aggregation, String sourceMultiplicityMin,
        String sourceMultiplicityMax, String targetMultiplicityMin, String targetMultiplicityMax,
        String sourceLifelineId, String targetLifelineId, String operationId, String messageSort,
        Integer lineNumber) {
}
