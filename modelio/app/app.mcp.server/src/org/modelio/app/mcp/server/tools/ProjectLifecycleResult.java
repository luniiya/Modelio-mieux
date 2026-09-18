package org.modelio.app.mcp.server.tools;

/**
 * Outcome of a {@link ProjectLifecycleService} create/open operation.
 *
 * @param workspace the current workspace path.
 * @param project   the project name.
 * @param path      the project's directory path (a direct child of {@code workspace}).
 * @param created   {@code true} if this call actually created the project (as opposed to opening a pre-existing one).
 * @param opened    {@code true} if the project is open in the application after this call returns.
 */
public record ProjectLifecycleResult(String workspace, String project, String path, boolean created, boolean opened) {
}
