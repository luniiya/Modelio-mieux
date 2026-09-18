package org.modelio.app.mcp.server.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.model.CreateElementRequest;
import org.modelio.app.mcp.server.model.PackageInfo;
import org.modelio.app.mcp.server.tools.GetElementTool;
import org.modelio.app.mcp.server.tools.GetProjectTool;
import org.modelio.app.mcp.server.tools.ListElementsTool;
import org.modelio.app.mcp.server.tools.CreateElementTool;
import org.modelio.app.mcp.server.tools.RenameElementTool;

public class ModelToolsTest {
    private ObjectMapper mapper;
    private FakeModelBrowser browser;

    @Before
    public void setUp() {
        this.mapper = new ObjectMapper();
        this.browser = new FakeModelBrowser(List.of(new PackageInfo("pkg-1", "Domain", null)));
    }

    @Test
    public void getsOpenProject() {
        final JsonNode result = new GetProjectTool(this.browser).call(this.mapper.createObjectNode(), this.mapper);
        assertEquals("MCP test project", result.get("name").asText());
    }

    @Test
    public void listsTypedElements() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("metaclass", "class");
        args.put("limit", 20);
        final JsonNode result = new ListElementsTool(this.browser).call(args, this.mapper);
        assertEquals(1, result.get("count").asInt());
        assertEquals("Customer", result.get("elements").get(0).get("name").asText());
        assertEquals("Class", result.get("elements").get(0).get("metaclass").asText());
    }

    @Test
    public void getsElementWithProperties() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("id", "class-1");
        final JsonNode result = new GetElementTool(this.browser).call(args, this.mapper);
        assertEquals("class-1", result.get("id").asText());
        assertTrue(result.get("properties").has("childCount"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnboundedList() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("limit", 1001);
        new ListElementsTool(this.browser).call(args, this.mapper);
    }

    @Test
    public void createsClassInTransactionBackedModelAccess() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "class");
        args.put("name", "Invoice");
        args.put("owner_id", "pkg-1");
        final JsonNode result = new CreateElementTool(this.browser).call(args, this.mapper);
        assertEquals("created-1", result.get("id").asText());
        assertEquals("Invoice", result.get("name").asText());
        assertEquals("committed", result.get("properties").get("transaction").asText());
    }

    @Test
    public void renamesElement() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("id", "class-1");
        args.put("name", "Account");
        final JsonNode result = new RenameElementTool(this.browser).call(args, this.mapper);
        assertEquals("Account", result.get("name").asText());
    }

    @Test(expected = IllegalArgumentException.class)
    public void associationRequiresTarget() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "association");
        args.put("owner_id", "class-1");
        new CreateElementTool(this.browser).call(args, this.mapper);
    }

    @Test
    public void createsClassDiagram() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "class_diagram");
        args.put("name", "Overview");
        args.put("owner_id", "pkg-1");
        final JsonNode result = new CreateElementTool(this.browser).call(args, this.mapper);
        assertEquals("Overview", result.get("name").asText());
        final CreateElementRequest request = this.browser.lastCreateRequest();
        assertEquals("class_diagram", request.kind());
        assertEquals("pkg-1", request.ownerId());
    }

    @Test
    public void createsObjectDiagram() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "object_diagram");
        args.put("name", "Scenario");
        args.put("owner_id", "pkg-1");
        new CreateElementTool(this.browser).call(args, this.mapper);
        assertEquals("object_diagram", this.browser.lastCreateRequest().kind());
    }

    @Test(expected = IllegalArgumentException.class)
    public void classDiagramRequiresName() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "class_diagram");
        args.put("owner_id", "pkg-1");
        new CreateElementTool(this.browser).call(args, this.mapper);
    }

    @Test
    public void createsTypedInstance() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "instance");
        args.put("name", "anInvoice");
        args.put("owner_id", "pkg-1");
        args.put("type_id", "class-1");
        new CreateElementTool(this.browser).call(args, this.mapper);
        final CreateElementRequest request = this.browser.lastCreateRequest();
        assertEquals("instance", request.kind());
        assertEquals("pkg-1", request.ownerId());
        assertEquals("class-1", request.typeId());
    }

    @Test
    public void createsUntypedInstance() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "instance");
        args.put("name", "anonymous");
        args.put("owner_id", "pkg-1");
        new CreateElementTool(this.browser).call(args, this.mapper);
        assertEquals(null, this.browser.lastCreateRequest().typeId());
    }

    @Test
    public void createsGeneralization() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "generalization");
        args.put("owner_id", "class-1");
        args.put("target_id", "class-2");
        final JsonNode result = new CreateElementTool(this.browser).call(args, this.mapper);
        assertEquals("Generalization", result.get("metaclass").asText());
        final CreateElementRequest request = this.browser.lastCreateRequest();
        assertEquals("class-1", request.ownerId());
        assertEquals("class-2", request.targetId());
        assertEquals(null, request.name());
    }

    @Test(expected = IllegalArgumentException.class)
    public void generalizationRequiresTarget() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "generalization");
        args.put("owner_id", "class-1");
        new CreateElementTool(this.browser).call(args, this.mapper);
    }

    @Test
    public void createsCompositionAssociationWithMultiplicities() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "association");
        args.put("owner_id", "class-1");
        args.put("target_id", "class-2");
        args.put("role_name", "items");
        args.put("aggregation", "composition");
        args.put("source_multiplicity_min", "1");
        args.put("source_multiplicity_max", "1");
        args.put("target_multiplicity_min", "0");
        args.put("target_multiplicity_max", "*");
        new CreateElementTool(this.browser).call(args, this.mapper);
        final CreateElementRequest request = this.browser.lastCreateRequest();
        assertEquals("association", request.kind());
        assertEquals("class-1", request.ownerId());
        assertEquals("class-2", request.targetId());
        assertEquals("items", request.roleName());
        assertEquals("composition", request.aggregation());
        assertEquals("1", request.sourceMultiplicityMin());
        assertEquals("1", request.sourceMultiplicityMax());
        assertEquals("0", request.targetMultiplicityMin());
        assertEquals("*", request.targetMultiplicityMax());
    }

    @Test
    public void createsLinkBetweenInstances() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "link");
        args.put("owner_id", "inst-1");
        args.put("target_id", "inst-2");
        args.put("role_name", "owns");
        new CreateElementTool(this.browser).call(args, this.mapper);
        final CreateElementRequest request = this.browser.lastCreateRequest();
        assertEquals("link", request.kind());
        assertEquals("inst-1", request.ownerId());
        assertEquals("inst-2", request.targetId());
        assertEquals("owns", request.roleName());
        assertEquals(null, request.name());
    }

    @Test(expected = IllegalArgumentException.class)
    public void linkRequiresTarget() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "link");
        args.put("owner_id", "inst-1");
        new CreateElementTool(this.browser).call(args, this.mapper);
    }

    @Test
    public void createsSlotWithValue() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "slot");
        args.put("owner_id", "inst-1");
        args.put("attribute_id", "attr-1");
        args.put("value", "42");
        new CreateElementTool(this.browser).call(args, this.mapper);
        final CreateElementRequest request = this.browser.lastCreateRequest();
        assertEquals("slot", request.kind());
        assertEquals("inst-1", request.ownerId());
        assertEquals("attr-1", request.attributeId());
        assertEquals("42", request.value());
        assertEquals(null, request.name());
    }

    @Test(expected = IllegalArgumentException.class)
    public void slotRequiresAttributeId() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "slot");
        args.put("owner_id", "inst-1");
        new CreateElementTool(this.browser).call(args, this.mapper);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createElementRequiresKind() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("owner_id", "pkg-1");
        new CreateElementTool(this.browser).call(args, this.mapper);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createElementRequiresOwnerId() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "class");
        args.put("name", "Invoice");
        new CreateElementTool(this.browser).call(args, this.mapper);
    }

    @Test(expected = IllegalArgumentException.class)
    public void classRequiresName() {
        final ObjectNode args = this.mapper.createObjectNode();
        args.put("kind", "class");
        args.put("owner_id", "pkg-1");
        new CreateElementTool(this.browser).call(args, this.mapper);
    }
}
