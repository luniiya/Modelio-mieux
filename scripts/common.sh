#!/usr/bin/env bash
# Shared setup for the Modelio build/install scripts.
# Pins JDK 21 and a user-local Maven (no sudo needed), and points Tycho
# at this checkout via ECLIPSE_WS.
#
# Meant to be sourced, not executed directly.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

MAVEN_VERSION="3.9.9"
MAVEN_HOME="${HOME}/.local/opt/apache-maven-${MAVEN_VERSION}"
MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"

JAVA21_HOME="/usr/lib/jvm/java-21-openjdk"

setup_java() {
    if [[ -x "${JAVA21_HOME}/bin/java" ]]; then
        export JAVA_HOME="${JAVA21_HOME}"
    else
        echo "error: Java 21 not found at ${JAVA21_HOME}. Modelio (Tycho) requires JDK 21." >&2
        echo "       Install it with: sudo pacman -S jdk21-openjdk" >&2
        exit 1
    fi
    export PATH="${JAVA_HOME}/bin:${PATH}"
}

setup_maven() {
    if [[ ! -x "${MAVEN_HOME}/bin/mvn" ]]; then
        echo "==> Installing Apache Maven ${MAVEN_VERSION} to ${MAVEN_HOME} (user-local, no sudo)"
        mkdir -p "$(dirname "${MAVEN_HOME}")"
        local tmp
        tmp="$(mktemp -d)"
        curl -fsSL "${MAVEN_URL}" -o "${tmp}/maven.tar.gz"
        tar -xzf "${tmp}/maven.tar.gz" -C "$(dirname "${MAVEN_HOME}")"
        rm -rf "${tmp}"
    fi
    export PATH="${MAVEN_HOME}/bin:${PATH}"
}

# Sets up JAVA_HOME, PATH (java+maven) and ECLIPSE_WS for the build.
setup_env() {
    setup_java
    setup_maven
    export ECLIPSE_WS="${REPO_ROOT}"
    export MAVEN_OPTS="${MAVEN_OPTS:--Xmx2g}"
}
