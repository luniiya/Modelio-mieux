package org.modelio.app.mcp.server.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.tools.CreateProjectTool;

public class CreateProjectToolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void createsAndReportsWorkspaceProjectAndPath() throws Exception {
        final FakeProjectLifecycleService service = new FakeProjectLifecycleService();
        final CreateProjectTool tool = new CreateProjectTool(service);
        final ObjectNode arguments = this.mapper.createObjectNode();
        arguments.put("name", "benchmark");

        final JsonNode result = tool.call(arguments, this.mapper);

        assertEquals("benchmark", service.lastCreateName());
        assertFalse("default open_existing should be false", service.lastOpenExisting());
        assertEquals("benchmark", result.get("project").asText());
        assertEquals("/home/user/modelio-workspace", result.get("workspace").asText());
        assertEquals("/home/user/modelio-workspace/benchmark", result.get("path").asText());
        assertTrue(result.get("created").asBoolean());
        assertTrue(result.get("opened").asBoolean());
    }

    @Test
    public void forwardsOpenExistingFlag() throws Exception {
        final FakeProjectLifecycleService service = new FakeProjectLifecycleService();
        final CreateProjectTool tool = new CreateProjectTool(service);
        final ObjectNode arguments = this.mapper.createObjectNode();
        arguments.put("name", "benchmark");
        arguments.put("open_existing", true);

        final JsonNode result = tool.call(arguments, this.mapper);

        assertTrue(service.lastOpenExisting());
        assertFalse("open_existing should skip (re)creation", result.get("created").asBoolean());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingName() throws Exception {
        // A null service is safe here: validation must fail before it is ever touched.
        new CreateProjectTool(null).call(this.mapper.createObjectNode(), this.mapper);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankName() throws Exception {
        final ObjectNode arguments = this.mapper.createObjectNode();
        arguments.put("name", "   ");

        new CreateProjectTool(null).call(arguments, this.mapper);
    }

    @Test(expected = IllegalStateException.class)
    public void propagatesRejectionOfExistingPath() throws Exception {
        final CreateProjectTool tool = new CreateProjectTool(new FakeProjectLifecycleService(
                new IllegalStateException("A project named 'benchmark' already exists")));
        final ObjectNode arguments = this.mapper.createObjectNode();
        arguments.put("name", "benchmark");

        tool.call(arguments, this.mapper);
    }

}
