#!/usr/bin/env python3
"""Narrow unit tests for scripts/modelio-mcp-gateway.py.

Covers the logic that's easy to get subtly wrong and hard to notice by
inspection: resolving the real Modelio binary through the modelio.sh
wrapper (needed for duplicate-launch detection once the wrapper has
exec'd and its own name has disappeared from /proc), the tokenless
loopback defaults, and stdout/stderr separation for diagnostics.

Run directly: python3 scripts/tests/test_modelio_mcp_gateway.py
"""

import importlib.util
import io
import subprocess
import sys
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from tempfile import TemporaryDirectory

GATEWAY_PATH = Path(__file__).resolve().parent.parent / "modelio-mcp-gateway.py"


def load_gateway_module():
    spec = importlib.util.spec_from_file_location("modelio_mcp_gateway", GATEWAY_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ResolveTargetBinaryTest(unittest.TestCase):
    def setUp(self):
        self.gateway = load_gateway_module()

    def test_resolves_through_modelio_sh_wrapper(self):
        # Mirrors scripts/install.sh: ~/.local/bin/modelio-mieux -> a
        # modelio.sh wrapper that execs the sibling "modelio" binary.
        with TemporaryDirectory() as tmp:
            install_dir = Path(tmp) / "opt" / "modelio"
            install_dir.mkdir(parents=True)
            wrapper = install_dir / "modelio.sh"
            wrapper.write_text("#!/bin/bash\nexec \"$(dirname \"$0\")/modelio\" \"$@\"\n")
            wrapper.chmod(0o755)
            bin_link = Path(tmp) / "bin" / "modelio-mieux"
            bin_link.parent.mkdir(parents=True)
            bin_link.symlink_to(wrapper)

            target = self.gateway.resolve_target_binary(bin_link)

            self.assertEqual(target, install_dir / "modelio")

    def test_non_wrapper_launcher_resolves_to_itself(self):
        with TemporaryDirectory() as tmp:
            binary = Path(tmp) / "modelio-direct-binary"
            binary.write_text("")

            target = self.gateway.resolve_target_binary(binary)

            self.assertEqual(target, binary.resolve())

    def test_missing_launcher_does_not_raise(self):
        missing = Path("/nonexistent/does/not/exist/modelio-mieux")

        target = self.gateway.resolve_target_binary(missing)

        self.assertEqual(target, missing)


class DefaultsTest(unittest.TestCase):
    """Defaults must be tokenless, loopback-only, and match the shared
    Modelio/eclipse-mieux workspace decided in CLAUDE.md."""

    def test_defaults_in_a_clean_environment(self):
        script = (
            "import importlib.util, sys;"
            f"spec = importlib.util.spec_from_file_location('m', {str(GATEWAY_PATH)!r});"
            "m = importlib.util.module_from_spec(spec);"
            "spec.loader.exec_module(m);"
            "print(m.ENDPOINT); print(m.LAUNCHER); print(m.WORKSPACE)"
        )
        result = subprocess.run(
            [sys.executable, "-c", script],
            env={"HOME": "/home/testuser", "PATH": "/usr/bin"},
            capture_output=True,
            text=True,
            check=True,
        )
        endpoint, launcher, workspace = result.stdout.strip().splitlines()
        self.assertEqual(endpoint, "http://127.0.0.1:8765/mcp")
        self.assertEqual(launcher, "/home/testuser/.local/bin/modelio-mieux")
        self.assertEqual(workspace, "/home/testuser/.local/share/eclipse-mieux/workspace")
        self.assertNotIn("token", result.stdout.lower())

    def test_no_auth_header_or_token_env_var_is_used(self):
        # The backend is loopback-only and never checks credentials, so the
        # gateway must not send an Authorization/Bearer header nor read a
        # *_TOKEN env var to build one (a prose mention of "token" in a
        # comment explaining this is fine; a header or env lookup is not).
        source = GATEWAY_PATH.read_text()
        self.assertNotIn("Authorization", source)
        self.assertNotIn("Bearer", source)
        self.assertNotRegex(source, r"environ\.get\([\"'][A-Za-z_]*TOKEN")


class DiagnosticsStayOffStdoutTest(unittest.TestCase):
    def setUp(self):
        self.gateway = load_gateway_module()

    def test_log_writes_only_to_stderr(self):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            self.gateway.log("hello from a test")

        self.assertEqual(out.getvalue(), "")
        self.assertIn("hello from a test", err.getvalue())


class CompileTest(unittest.TestCase):
    def test_py_compiles(self):
        result = subprocess.run(
            [sys.executable, "-m", "py_compile", str(GATEWAY_PATH)],
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
