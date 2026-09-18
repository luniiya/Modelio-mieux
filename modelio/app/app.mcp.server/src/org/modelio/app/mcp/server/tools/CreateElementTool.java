package org.modelio.app.mcp.server.tools;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.model.CreateElementRequest;
import org.modelio.app.mcp.server.model.ModelBrowser;
import org.modelio.app.mcp.server.protocol.McpTool;

/**
 * Creates common UML elements through Modelio's transaction/undo system.
 * <p>
 * Supports the elements needed to build a class diagram (packages, classes,
 * attributes, associations with aggregation/composition and multiplicities,
 * generalizations) as well as an object diagram instantiating it (class and
 * object diagrams, instances typed by a class, links between instances, and
 * attribute slots/values on an instance).
 */
public final class CreateElementTool implements McpTool {
    /** Kinds that never take a {@code name} (they are identified by their endpoints instead). */
    private static final Set<String> NAMELESS_KINDS = Set.of("association", "generalization", "link", "slot");
    /** Kinds that require {@code target_id}. */
    private static final Set<String> TARGETED_KINDS = Set.of("association", "generalization", "link");

    private final ModelBrowser browser;

    public CreateElementTool(final ModelBrowser browser) {
        this.browser = browser;
    }

    @Override public String name() { return "create_element"; }

    @Override public String description() {
        return "Creates a UML element in an undoable Modelio transaction. Supported kinds: package, class, "
                + "attribute, operation, association, generalization, class_diagram, object_diagram, instance, link, slot, "
                + "interaction, sequence_diagram, lifeline and message. "
                + "owner_id is the containing namespace/classifier (source classifier for association/link/generalization "
                + "endpoints, the owning package for instance, the owning instance for slot, the context element for "
                + "diagrams). target_id is required for association, generalization (super-type) and link (destination "
                + "instance). type_id types an attribute or instance. aggregation (association|aggregation|composition) "
                + "and the *_multiplicity_* fields refine associations. attribute_id and value are used by slot. "
                + "For messages, source_lifeline_id and target_lifeline_id identify the participants, operation_id "
                + "optionally identifies the invoked operation, message_sort defaults to SYNCCALL, and line_number "
                + "sets the sequence position.";
    }

    @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
        final ObjectNode schema = ToolJson.emptySchema(mapper);
        final ObjectNode properties = (ObjectNode) schema.get("properties");
        final ObjectNode kind = properties.putObject("kind");
        kind.put("type", "string");
        kind.putArray("enum").add("package").add("class").add("attribute").add("association")
                .add("generalization").add("class_diagram").add("object_diagram").add("instance")
                .add("link").add("slot").add("operation").add("interaction").add("sequence_diagram")
                .add("lifeline").add("message");
        properties.putObject("name").put("type", "string");
        properties.putObject("owner_id").put("type", "string");
        properties.putObject("type_id").put("type", "string");
        properties.putObject("target_id").put("type", "string");
        properties.putObject("role_name").put("type", "string");
        properties.putObject("attribute_id").put("type", "string");
        properties.putObject("value").put("type", "string");
        final ObjectNode aggregation = properties.putObject("aggregation");
        aggregation.put("type", "string");
        aggregation.putArray("enum").add("association").add("aggregation").add("composition");
        aggregation.put("default", "association");
        properties.putObject("source_multiplicity_min").put("type", "string");
        properties.putObject("source_multiplicity_max").put("type", "string");
        properties.putObject("target_multiplicity_min").put("type", "string");
        properties.putObject("target_multiplicity_max").put("type", "string");
        properties.putObject("source_lifeline_id").put("type", "string");
        properties.putObject("target_lifeline_id").put("type", "string");
        properties.putObject("operation_id").put("type", "string");
        properties.putObject("message_sort").put("type", "string");
        properties.putObject("line_number").put("type", "integer");
        schema.putArray("required").add("kind").add("owner_id");
        return schema;
    }

    @Override public JsonNode call(final JsonNode arguments, final ObjectMapper mapper) {
        final String kind = text(arguments, "kind");
        final String name = text(arguments, "name");
        final String ownerId = text(arguments, "owner_id");
        if (kind == null || ownerId == null) {
            throw new IllegalArgumentException("Missing required argument: kind or owner_id");
        }
        if (!NAMELESS_KINDS.contains(kind) && (name == null || name.isBlank())) {
            throw new IllegalArgumentException("Missing required argument: name");
        }
        if (TARGETED_KINDS.contains(kind) && text(arguments, "target_id") == null) {
            throw new IllegalArgumentException("Missing required argument: target_id");
        }
        if ("slot".equals(kind) && text(arguments, "attribute_id") == null) {
            throw new IllegalArgumentException("Missing required argument: attribute_id");
        }
        return ToolJson.element(mapper, this.browser.createElement(new CreateElementRequest(kind, name, ownerId,
                text(arguments, "type_id"), text(arguments, "target_id"), text(arguments, "role_name"),
                text(arguments, "attribute_id"), text(arguments, "value"), text(arguments, "aggregation"),
                text(arguments, "source_multiplicity_min"), text(arguments, "source_multiplicity_max"),
                text(arguments, "target_multiplicity_min"), text(arguments, "target_multiplicity_max"),
                text(arguments, "source_lifeline_id"), text(arguments, "target_lifeline_id"),
                text(arguments, "operation_id"), text(arguments, "message_sort"),
                arguments.hasNonNull("line_number") ? arguments.get("line_number").asInt() : null)));
    }

    private static String text(final JsonNode arguments, final String name) {
        return arguments.hasNonNull(name) ? arguments.get(name).asText() : null;
    }
}
