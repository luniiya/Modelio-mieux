# Modelio-mieux

A fork of [Modelio](https://www.modelio.org) 6.2 — an Eclipse RCP-based
UML/BPMN/ArchiMate/SysML modeling tool. This fork's goal: extend Modelio with
**MCP support** so AI agents can inspect and edit models live, alongside a
human working in the GUI.

## Build & install

No system Maven is installed or required — everything is self-contained:

```bash
scripts/install.sh              # build + install + (re)create app launcher
scripts/install.sh --no-build   # reinstall only, skip the rebuild
scripts/build.sh                # build only, don't touch the install
```

`scripts/common.sh` pins the toolchain: JDK 21 (`/usr/lib/jvm/java-21-openjdk`)
and a user-local Maven 3.9.9 (auto-downloaded to `~/.local/opt/apache-maven-3.9.9`
on first run, no sudo). `ECLIPSE_WS` is set to the repo root — Tycho resolves
the vendored p2 repos under `dev-platform/rcp-target/**` relative to it.

Install target: `~/.local/opt/modelio/` (the materialized product), symlinked
as `~/.local/bin/modelio` (already on PATH), with a `.desktop` launcher entry
regenerated at `~/.local/share/applications/modelio-mieux.desktop` on every
install.

Run Modelio through the repository launcher:

```bash
scripts/start-modelio-mieux.sh
```

It closes any running Modelio or Eclipse process, waits for it to exit, and
only then starts `/home/ayaya/.local/bin/modelio-mieux`. This is mandatory for
agent-driven work: never start Modelio directly while another Modelio or
Eclipse instance may still be running. Set `MODELIO_WORKSPACE` and
`MODELIO_PROJECT` when a specific workspace/project is needed; extra
arguments are passed to Modelio.

## Build system

Real build entry point: `maven/aggregators/opensource/pom.xml`, via parent
`maven/modelio-parent/pom.xml` (Tycho 4.0.13, Java 21). **Ignore the stale
root `pom.xml`** (version `5.4.1-SNAPSHOT`, Tycho 2.2.0, Java 11) — leftover
cruft, not the real build.

Build invocation used by `scripts/build.sh`:
```
mvn -f maven/aggregators/opensource/pom.xml -Pproduct.org,platform.linux clean package
```
`product.org` triggers `tycho-p2-director-plugin` (materialize-products +
archive-products); `platform.linux` narrows the target environment to
linux/gtk/x86_64 only. Materialized output lands under
`products/opensource/target/products/org.modelio.product/linux/gtk/x86_64/Modelio 6.2/`.

Product identity: uid `org.modelio.product`, launcher name `modelio`
(`products/opensource/modelio-os.product`).

Some `AGENTS.md` files and `build/build.sh` scripts scattered under `modelio/**`
are leftovers from a *different* environment — they `source
/work/modelio/alouette/toolkit/env_toolkit.sh`, which doesn't exist here.
Don't use them; use `scripts/build.sh` instead.

## Source layout

- `modelio/core/**` — kernel, session, metamodel API/impl (the model engine).
- `modelio/platform/**` — shared platform services (search, preferences, etc.).
- `modelio/app/**` — application-level UI (SWT/JFace/e4).
- `modelio/uml/`, `modelio/bpmn/`, `modelio/archimate/` — per-metamodel bundles.
- `features/opensource/**` — Eclipse feature projects assembled into the product.
- `products/opensource/` — the product definition (`modelio-os.product`) and
  packaging profiles.
- `maven/aggregators/**` — Tycho reactor module lists (what actually gets built).
- `dev-platform/rcp-target/**` — vendored p2 repos (Eclipse/SWT/GEF/etc. deps).

Toolchain notes: bundles targeting Java 17 execution environment (core/UI) vs
Java 21 for newer MQL-related code — check each bundle's
`Bundle-RequiredExecutionEnvironment` before assuming.

## The MCP initiative

Goal: let AI agents read and edit a Modelio model live, in the same process a
human has open in the GUI — not a detached headless batch tool.

Decided architecture (2026-09-03):
- **Transport**: a new OSGi plugin bundle runs *inside* the normal Modelio
  application, exposing an MCP server over **HTTP/SSE** on localhost. Agents
  connect to whatever project is currently open in the running GUI.
- **Scope (v1)**: read (browse packages/classes/diagrams, properties,
  relationships) **+ create/edit** UML elements, attributes, and relationships
  through the existing kernel session API (`core.kernel`, `core.session`) —
  reusing Modelio's existing transaction/undo support rather than
  bypassing it. Diagram creation/layout is out of scope for v1.
- A headless `--mcp` stdio subprocess mode (for batch/scripted work without a
  GUI) is a possible phase 2, not started.

Not yet started: no MCP bundle exists yet. When adding it, it needs to be
wired into `features/opensource/**` and the relevant `maven/aggregators/**`
module list to actually be included in the build.

## Environment notes

- No passwordless sudo — don't shell out to `sudo`; the scripts are designed
  to need none.
- User's shell is fish; `~/.local/bin` is confirmed on PATH for both fish and
  bash.
- Desktop environment is Hyprland — `.desktop` entries go through
  `~/.local/share/applications/` for launcher (rofi/wofi/fuzzel-style) pickup.
