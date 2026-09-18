#!/usr/bin/env bash
# Runs the headless Modelio MCP JUnit suite against the classes built by Tycho.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

SERVER_CLASSES="${REPO_ROOT}/modelio/app/app.mcp.server/target/classes"
TEST_CLASSES="${REPO_ROOT}/modelio/app/app.mcp.server.tests/target/classes"
JACKSON_DIR="${REPO_ROOT}/dev-platform/rcp-target/jackson/integ/plugins"
JUNIT_DIR="${REPO_ROOT}/dev-platform/rcp-target/junit/v5/plugins"

CLASSPATH="${TEST_CLASSES}:${SERVER_CLASSES}"
CLASSPATH+=":${JACKSON_DIR}/com.fasterxml.jackson.core.jackson-annotations_2.18.3.jar"
CLASSPATH+=":${JACKSON_DIR}/com.fasterxml.jackson.core.jackson-core_2.18.3.jar"
CLASSPATH+=":${JACKSON_DIR}/com.fasterxml.jackson.core.jackson-databind_2.18.3.jar"
CLASSPATH+=":${JUNIT_DIR}/junit_4.13.2.jar"
CLASSPATH+=":${JUNIT_DIR}/org.hamcrest_3.0.0.jar"

exec java -cp "${CLASSPATH}" org.junit.runner.JUnitCore \
    org.modelio.app.mcp.server.tests.ListPackagesToolTest \
    org.modelio.app.mcp.server.tests.ModelToolsTest \
    org.modelio.app.mcp.server.tests.McpDispatcherTest \
    org.modelio.app.mcp.server.tests.HttpMcpTransportTest
