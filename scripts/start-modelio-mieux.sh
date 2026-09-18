#!/usr/bin/env bash
set -euo pipefail

# Always enforce the one-IDE-at-a-time rule before starting Modelio.
# The launcher may be called repeatedly, so stopping an already running
# instance is intentional.

MODELIO_BIN="${MODELIO_BIN:-$HOME/.local/bin/modelio-mieux}"
MODELIO_WORKSPACE="${MODELIO_WORKSPACE:-$HOME/.local/share/eclipse-mieux/workspace}"
MODELIO_PROJECT="${MODELIO_PROJECT:-}"

running_ide_pids() {
    ps -eo pid=,args= | awk -v self="$$" '
        $1 != self &&
        ($0 ~ /\/\.local\/bin\/modelio(-mieux)?([[:space:]]|$)/ ||
         $0 ~ /\/\.local\/(opt|lib)\/modelio/ ||
         $0 ~ /\/\.local\/bin\/eclipse(-mieux)?([[:space:]]|$)/ ||
         $0 ~ /\/\.local\/(opt|lib)\/eclipse/ ||
         $0 ~ /org\.eclipse\.equinox\.launcher([._]|[[:space:]])/) {
        print $1
    }'
}

stop_running_ides() {
    local pids remaining pid
    pids="$(running_ide_pids)"
    if [[ -z "$pids" ]]; then
        return
    fi

    echo "Closing existing Modelio/Eclipse process(es): $pids" >&2
    while read -r pid; do
        [[ -n "$pid" ]] && kill -TERM "$pid" 2>/dev/null || true
    done <<< "$pids"

    for _ in {1..40}; do
        remaining="$(running_ide_pids)"
        [[ -z "$remaining" ]] && return
        sleep 0.25
    done

    echo "Some IDE process(es) did not close gracefully; forcing shutdown: $remaining" >&2
    while read -r pid; do
        [[ -n "$pid" ]] && kill -KILL "$pid" 2>/dev/null || true
    done <<< "$remaining"

    remaining="$(running_ide_pids)"
    if [[ -n "$remaining" ]]; then
        echo "Unable to close IDE process(es): $remaining" >&2
        exit 1
    fi
}

if [[ ! -x "$MODELIO_BIN" ]]; then
    echo "Modelio launcher not found or not executable: $MODELIO_BIN" >&2
    echo "Run scripts/install.sh first." >&2
    exit 1
fi

stop_running_ides

args=("$MODELIO_BIN" -workspace "$MODELIO_WORKSPACE")
if [[ -n "$MODELIO_PROJECT" ]]; then
    args+=(-project "$MODELIO_PROJECT")
fi
args+=("$@")

echo "Starting Modelio: ${args[*]}" >&2
exec "${args[@]}"
