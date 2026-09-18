#!/usr/bin/env python3
"""Keep an MCP client's stdio connection alive while Modelio Mieux starts,
restarts, or is already running with a human at the GUI.

Modeled on eclipse-mieux/scripts/mcp-gateway.py: this process speaks MCP
JSON-RPC 2.0 on stdin/stdout and proxies each request to the HTTP/SSE MCP
server that ``org.modelio.app.mcp.server`` starts inside the Modelio GUI
process (see McpServerAddon.DEFAULT_PORT / HttpMcpTransport). No auth token
is used anywhere in this path -- the backend is loopback-only and doesn't
require one, so the gateway must never invent one.

stdout carries ONLY JSON-RPC protocol frames (one per line); every
diagnostic goes to stderr so it never corrupts the stdio transport.
"""

import fcntl
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from urllib.error import URLError
from urllib.request import Request, urlopen


ENDPOINT = os.environ.get("MODELIO_MIEUX_MCP_ENDPOINT", "http://127.0.0.1:8765/mcp")
LAUNCHER = os.environ.get("MODELIO_MIEUX_LAUNCHER", str(Path.home() / ".local" / "bin" / "modelio-mieux"))
WORKSPACE = os.environ.get("MODELIO_MIEUX_WORKSPACE", str(Path.home() / ".local" / "share" / "eclipse-mieux" / "workspace"))
STATE_DIR = Path(os.environ.get("XDG_STATE_HOME", str(Path.home() / ".local" / "state"))) / "modelio-mieux"
RUNTIME_DIR = Path(os.environ["XDG_RUNTIME_DIR"]) if os.environ.get("XDG_RUNTIME_DIR") else None
LOCK_CANDIDATES = [
    (RUNTIME_DIR / "modelio-mieux" / "mcp-gateway.lock") if RUNTIME_DIR else None,
    STATE_DIR / "mcp-gateway.lock",
    Path("/tmp") / f"modelio-mieux-mcp-gateway-{os.getuid()}.lock",
]
START_TIMEOUT = float(os.environ.get("MODELIO_MIEUX_MCP_START_TIMEOUT", "120"))
REQUEST_TIMEOUT = float(os.environ.get("MODELIO_MIEUX_MCP_REQUEST_TIMEOUT", "30"))


def log(message):
    """Diagnostics only -- stdout is reserved for JSON-RPC frames."""
    print(f"[modelio-mcp-gateway] {message}", file=sys.stderr, flush=True)


def resolve_target_binary(launcher):
    """Resolve the real GUI binary that ``launcher`` eventually execs.

    ``~/.local/bin/modelio-mieux`` is a symlink to the installed
    ``modelio.sh`` wrapper (see scripts/install.sh), which itself
    ``exec``s ``<install dir>/modelio`` -- replacing the process image, so
    the long-running GUI process's /proc/<pid>/cmdline never contains
    "modelio-mieux" once it's up. Resolve through the symlink and the
    wrapper's naming convention so process-scan matching actually finds
    the running application, not just its wrapper script.
    """
    try:
        real = launcher.resolve(strict=False)
    except OSError:
        return launcher
    if real.name == "modelio.sh":
        return real.parent / "modelio"
    return real


class Gateway:
    def __init__(self):
        self.lock_file = None
        for lock_path in LOCK_CANDIDATES:
            if lock_path is None:
                continue
            try:
                lock_path.parent.mkdir(parents=True, exist_ok=True)
                self.lock_file = lock_path.open("a+")
                break
            except OSError:
                continue
        if self.lock_file is None:
            raise OSError("Could not create an MCP gateway lock file")
        fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_EX)
        self.modelio_process = None
        self.launcher_path = Path(LAUNCHER)
        self.target_binary = resolve_target_binary(self.launcher_path)

    def close(self):
        fcntl.flock(self.lock_file.fileno(), fcntl.LOCK_UN)
        self.lock_file.close()

    def request(self, message):
        payload = json.dumps(message, separators=(",", ":")).encode("utf-8")
        request = Request(ENDPOINT, data=payload, method="POST", headers={"Content-Type": "application/json"})
        with urlopen(request, timeout=REQUEST_TIMEOUT) as response:
            body = response.read()
        return json.loads(body) if body else None

    def backend_is_ready(self):
        try:
            response = self.request({"jsonrpc": "2.0", "id": "gateway-probe", "method": "initialize", "params": {}})
            return isinstance(response, dict) and response.get("result") is not None
        except (OSError, URLError, ValueError):
            return False

    def modelio_is_running(self):
        markers = {str(self.target_binary), str(self.launcher_path), "modelio-mieux"}
        for process_dir in Path("/proc").glob("[0-9]*"):
            try:
                pid = int(process_dir.name)
                if pid == os.getpid():
                    continue
                command = (process_dir / "cmdline").read_bytes().replace(b"\0", b" ").decode(errors="ignore")
            except (OSError, ValueError):
                continue
            if any(marker in command for marker in markers):
                return True
        return False

    def ensure_backend(self):
        if self.backend_is_ready():
            return
        if not self.launcher_path.is_file():
            raise RuntimeError(f"Modelio Mieux launcher is missing: {self.launcher_path}")
        if self.modelio_process is None or self.modelio_process.poll() is not None:
            if self.modelio_is_running():
                log("Modelio Mieux process already running elsewhere; waiting for its MCP server to come up")
            else:
                Path(WORKSPACE).mkdir(parents=True, exist_ok=True)
                log(f"starting {self.launcher_path} -workspace {WORKSPACE}")
                self.modelio_process = subprocess.Popen(
                    [str(self.launcher_path), "-workspace", WORKSPACE],
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    start_new_session=True,
                )
        log(f"waiting up to {START_TIMEOUT:.0f}s for MCP backend at {ENDPOINT}")
        deadline = time.monotonic() + START_TIMEOUT
        while time.monotonic() < deadline:
            if self.backend_is_ready():
                log("MCP backend is ready")
                return
            time.sleep(0.25)
        raise RuntimeError(f"Modelio Mieux MCP backend did not become ready at {ENDPOINT}")

    def run(self):
        log(f"gateway up, proxying stdio to {ENDPOINT} (launcher={self.launcher_path}, workspace={WORKSPACE})")
        for line in sys.stdin:
            if not line.strip():
                continue
            message = None
            try:
                message = json.loads(line)
                self.ensure_backend()
                response = self.request(message)
                if response is not None:
                    sys.stdout.write(json.dumps(response, separators=(",", ":")) + "\n")
                    sys.stdout.flush()
            except Exception as error:  # Keep the stdio transport alive after one failed call.
                log(f"request failed: {error}")
                request_id = message.get("id") if isinstance(message, dict) else None
                response = {
                    "jsonrpc": "2.0",
                    "id": request_id,
                    "error": {"code": -32603, "message": str(error)},
                }
                sys.stdout.write(json.dumps(response, separators=(",", ":")) + "\n")
                sys.stdout.flush()
        log("stdin closed, gateway exiting")


def main():
    gateway = Gateway()
    try:
        gateway.run()
    finally:
        gateway.close()


if __name__ == "__main__":
    main()
