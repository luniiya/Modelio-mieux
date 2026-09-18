package org.modelio.app.mcp.server.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.modelio.app.mcp.server.model.PackageInfo;
import org.modelio.app.mcp.server.tools.ListPackagesTool;

public class ListPackagesToolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void reportsNameAndOwnerOfEachPackage() throws Exception {
        final ListPackagesTool tool = new ListPackagesTool(new FakeModelBrowser(
                List.of(new PackageInfo("id-1", "Analysis", null), new PackageInfo("id-2", "Design", "Analysis"))));

        final JsonNode result = tool.call(this.mapper.createObjectNode(), this.mapper);

        assertEquals(2, result.get("count").asInt());
        final JsonNode packages = result.get("packages");
        assertEquals("id-1", packages.get(0).get("id").asText());
        assertEquals("Analysis", packages.get(0).get("name").asText());
        assertTrue("root package should have no 'owner' field", !packages.get(0).has("owner"));

        assertEquals("Design", packages.get(1).get("name").asText());
        assertEquals("Analysis", packages.get(1).get("owner").asText());
    }

    @Test
    public void emptyProjectReportsZeroPackages() throws Exception {
        final ListPackagesTool tool = new ListPackagesTool(new FakeModelBrowser(List.of()));

        final JsonNode result = tool.call(this.mapper.createObjectNode(), this.mapper);

        assertEquals(0, result.get("count").asInt());
        assertEquals(0, result.get("packages").size());
    }

    @Test(expected = IllegalStateException.class)
    public void propagatesFailureWhenNoProjectIsOpen() throws Exception {
        final ListPackagesTool tool = new ListPackagesTool(
                new FakeModelBrowser(new IllegalStateException("No Modelio project is currently open.")));

        tool.call(this.mapper.createObjectNode(), this.mapper);
    }

}
