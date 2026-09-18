#!/usr/bin/env bash
# Builds (unless --no-build) and installs Modelio into ~/.local/opt,
# then symlinks the launcher into ~/.local/bin so `modelio-mieux` is on PATH.
#
# This is the "rebuild and reinstall" entry point: just re-run this
# script any time the source changes.
#
# Usage: scripts/install.sh [--no-build] [--with-tests]
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

DO_BUILD=1
BUILD_ARGS=()
for arg in "$@"; do
    case "$arg" in
        --no-build) DO_BUILD=0 ;;
        *) BUILD_ARGS+=("$arg") ;;
    esac
done

if [[ "${DO_BUILD}" -eq 1 ]]; then
    "${SCRIPT_DIR}/build.sh" "${BUILD_ARGS[@]}"
fi

INSTALL_DIR="${HOME}/.local/opt/modelio"
BIN_LINK="${HOME}/.local/bin/modelio-mieux"
LEGACY_BIN_LINK="${HOME}/.local/bin/modelio"
MODELIO_WORKSPACE="${MODELIO_WORKSPACE:-${HOME}/.local/share/eclipse-mieux/workspace}"

# Find the materialized product's launcher binary rather than assuming
# the exact os/ws/arch directory layout tycho-p2-director produces.
LAUNCHER="$(find "${REPO_ROOT}/products/opensource/target/products" \
    -type f -name modelio -perm -u+x 2>/dev/null | head -n1)"

if [[ -z "${LAUNCHER}" ]]; then
    echo "error: could not find a built 'modelio' launcher under" >&2
    echo "       products/opensource/target/products" >&2
    echo "       (did the build actually finish materialize-products?)" >&2
    exit 1
fi
SRC_DIR="$(dirname "${LAUNCHER}")"

echo "==> Installing $(basename "${SRC_DIR}") to ${INSTALL_DIR}"
rm -rf "${INSTALL_DIR}"
mkdir -p "$(dirname "${INSTALL_DIR}")"
cp -a "${SRC_DIR}" "${INSTALL_DIR}"

mkdir -p "${MODELIO_WORKSPACE}"

mkdir -p "$(dirname "${BIN_LINK}")"
ln -sf "${INSTALL_DIR}/modelio.sh" "${BIN_LINK}"
# Keep old terminal commands and saved launchers working.
ln -sf "${BIN_LINK}" "${LEGACY_BIN_LINK}"

DESKTOP_DIR="${HOME}/.local/share/applications"
DESKTOP_FILE="${DESKTOP_DIR}/modelio-mieux.desktop"
mkdir -p "${DESKTOP_DIR}"
cat > "${DESKTOP_FILE}" <<EOF
[Desktop Entry]
Type=Application
Name=Modelio Mieux
Comment=UML/BPMN/ArchiMate/SysML modeling tool (Modelio-mieux fork)
Exec=${BIN_LINK} -workspace ${MODELIO_WORKSPACE}
Icon=${INSTALL_DIR}/icon.xpm
Terminal=false
StartupNotify=true
Categories=Development;IDE;
Keywords=UML;BPMN;ArchiMate;SysML;modeling;
EOF
if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "${DESKTOP_DIR}" >/dev/null 2>&1 || true
fi

echo "==> Installed."
echo "==> Run with: modelio-mieux"
echo "==> App launcher entry: ${DESKTOP_FILE}"
if ! command -v modelio-mieux >/dev/null 2>&1; then
    echo "    Note: ${HOME}/.local/bin isn't on PATH in this shell session."
    echo "    Open a new terminal, or add it to your shell rc."
fi
