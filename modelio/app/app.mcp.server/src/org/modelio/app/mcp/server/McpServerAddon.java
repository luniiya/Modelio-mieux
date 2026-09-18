package org.modelio.app.mcp.server;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.annotations.Execute;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.modelio.app.mcp.server.diagram.SessionDiagramPlacer;
import org.modelio.app.mcp.server.model.SessionModelBrowser;
import org.modelio.app.mcp.server.project.SessionProjectLifecycleService;
import org.modelio.app.mcp.server.protocol.McpDispatcher;
import org.modelio.app.mcp.server.tools.CreateElementTool;
import org.modelio.app.mcp.server.tools.CreateProjectTool;
import org.modelio.app.mcp.server.tools.DiagramExportImageTool;
import org.modelio.app.mcp.server.tools.DiagramPlaceElementsTool;
import org.modelio.app.mcp.server.tools.GetElementTool;
import org.modelio.app.mcp.server.tools.GetProjectTool;
import org.modelio.app.mcp.server.tools.ListElementsTool;
import org.modelio.app.mcp.server.tools.ListPackagesTool;
import org.modelio.app.mcp.server.tools.OpenProjectTool;
import org.modelio.app.mcp.server.tools.RenameElementTool;
import org.modelio.app.mcp.server.tools.SaveProjectTool;
import org.modelio.app.mcp.server.transport.HttpMcpTransport;
import org.modelio.app.mcp.server.ui.ModelioUiAutomation;
import org.modelio.app.mcp.server.ui.ModelioUiTools;
import org.modelio.platform.project.services.IProjectService;

/**
 * e4 startup processor that boots the MCP HTTP/SSE server inside the running
 * Modelio GUI process (see {@code plugin.xml}'s
 * {@code org.eclipse.e4.workbench.model} extension).
 * <p>
 * Bound to the running application's own {@link IEclipseContext}, exactly
 * like {@code org.modelio.api.impl.services.ModelioServices} bootstraps the
 * module API: this is how the rest of the codebase reaches
 * {@link IProjectService} at startup.
 */
public class McpServerAddon {

    /** System property overriding the HTTP port; defaults to {@link #DEFAULT_PORT}. */
    public static final String PORT_PROPERTY = "modelio.mcp.port";
    public static final int DEFAULT_PORT = 8765;

    private HttpMcpTransport transport;

    @Execute
    void initialize(final IEclipseContext context) {
        final IProjectService projectService = context.get(IProjectService.class);
        if (projectService == null) {
            // Not running as a full GUI application (e.g. batch/headless
            // mode) -- nothing to bridge, so skip starting the server.
            return;
        }

        final McpDispatcher dispatcher = new McpDispatcher(new ObjectMapper());
        final SessionModelBrowser browser = new SessionModelBrowser(projectService);
        dispatcher.registerTool(new GetProjectTool(browser));
        dispatcher.registerTool(new ListPackagesTool(browser));
        dispatcher.registerTool(new ListElementsTool(browser));
        dispatcher.registerTool(new GetElementTool(browser));
        dispatcher.registerTool(new CreateElementTool(browser));
        dispatcher.registerTool(new RenameElementTool(browser));

        final SessionProjectLifecycleService projectLifecycle = new SessionProjectLifecycleService(projectService);
        dispatcher.registerTool(new CreateProjectTool(projectLifecycle));
        dispatcher.registerTool(new OpenProjectTool(projectLifecycle));
        dispatcher.registerTool(new SaveProjectTool(projectService));

        final SessionDiagramPlacer diagramPlacer = new SessionDiagramPlacer(projectService, context);
        dispatcher.registerTool(new DiagramPlaceElementsTool(diagramPlacer));
        dispatcher.registerTool(new DiagramExportImageTool(diagramPlacer));

        final ModelioUiAutomation uiAutomation = new ModelioUiAutomation();
        ModelioUiTools.registerAll(dispatcher, uiAutomation);

        final int port = readPort();
        try {
            this.transport = new HttpMcpTransport(dispatcher, new ObjectMapper(),
                    new InetSocketAddress("127.0.0.1", port));
            this.transport.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> this.transport.close(), "modelio-mcp-shutdown"));
            System.out.println( // NOSONAR: startup diagnostic, no logger wired in yet
                    "[modelio-mcp] MCP server listening on http://127.0.0.1:" + this.transport.getPort() + "/mcp");
        } catch (final IOException e) {
            System.err.println("[modelio-mcp] Failed to start MCP server on port " + port + ": " + e.getMessage());
        }
    }

    private static int readPort() {
        final String raw = System.getProperty(PORT_PROPERTY);
        if (raw == null) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

}
