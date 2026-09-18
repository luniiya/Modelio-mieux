package org.modelio.app.mcp.server.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.tools.OpenProjectTool;

public class OpenProjectToolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void opensAndReportsWorkspaceProjectAndPath() throws Exception {
        final FakeProjectLifecycleService service = new FakeProjectLifecycleService();
        final OpenProjectTool tool = new OpenProjectTool(service);
        final ObjectNode arguments = this.mapper.createObjectNode();
        arguments.put("name", "benchmark");

        final JsonNode result = tool.call(arguments, this.mapper);

        assertEquals("benchmark", service.lastOpenName());
        assertEquals("benchmark", result.get("project").asText());
        assertEquals("/home/user/modelio-workspace", result.get("workspace").asText());
        assertEquals("/home/user/modelio-workspace/benchmark", result.get("path").asText());
        assertFalse("open_project never creates", result.get("created").asBoolean());
        assertTrue(result.get("opened").asBoolean());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingName() throws Exception {
        // A null service is safe here: validation must fail before it is ever touched.
        new OpenProjectTool(null).call(this.mapper.createObjectNode(), this.mapper);
    }

    @Test(expected = IllegalArgumentException.class)
    public void propagatesFailureWhenProjectDoesNotExist() throws Exception {
        final OpenProjectTool tool = new OpenProjectTool(
                new FakeProjectLifecycleService(new IllegalArgumentException("No project named 'ghost' found")));
        final ObjectNode arguments = this.mapper.createObjectNode();
        arguments.put("name", "ghost");

        tool.call(arguments, this.mapper);
    }

}
