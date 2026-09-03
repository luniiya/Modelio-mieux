#!/usr/bin/env bash
# Builds the Modelio open-source product from source with Tycho.
#
# Usage: scripts/build.sh [--with-tests] [-- <extra mvn args>]
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

WITH_TESTS=0
EXTRA_ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --with-tests) WITH_TESTS=1; shift ;;
        --) shift; EXTRA_ARGS+=("$@"); break ;;
        *) EXTRA_ARGS+=("$1"); shift ;;
    esac
done

setup_env

AGGREGATOR="${REPO_ROOT}/maven/aggregators/opensource/pom.xml"

MVN_ARGS=(
    -f "${AGGREGATOR}"
    -Pproduct.org,platform.linux
    --no-transfer-progress
    -Dstyle.color=never
)
if [[ "${WITH_TESTS}" -eq 0 ]]; then
    MVN_ARGS+=(-DskipTests=true)
fi
MVN_ARGS+=(clean package)

echo "==> JAVA_HOME=${JAVA_HOME}"
echo "==> ECLIPSE_WS=${ECLIPSE_WS}"
echo "==> MAVEN_OPTS=${MAVEN_OPTS}"
echo "==> mvn ${MVN_ARGS[*]} ${EXTRA_ARGS[*]:-}"

exec mvn "${MVN_ARGS[@]}" "${EXTRA_ARGS[@]}"
