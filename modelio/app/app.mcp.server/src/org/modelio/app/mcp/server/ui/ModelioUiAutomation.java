package org.modelio.app.mcp.server.ui;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** SWT-thread-confined, text-only representation of the Modelio UI. */
public final class ModelioUiAutomation {
    private final Display display = Display.getDefault();
    private final Map<Long, Widget> objects = new LinkedHashMap<>();
    private final IdentityHashMap<Widget, Long> ids = new IdentityHashMap<>();
    private long nextId = 1;

    public JsonNode call(final UiCall operation) throws Exception {
        if (this.display == null || this.display.isDisposed()) throw new IllegalStateException("SWT display is not available");
        final AtomicReference<JsonNode> result = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Runnable task = () -> { try { result.set(operation.run()); } catch (final Throwable e) { failure.set(e); } };
        if (Display.getCurrent() == this.display) task.run(); else this.display.syncExec(task);
        final Throwable error = failure.get();
        if (error instanceof Exception exception) throw exception;
        if (error != null) throw new IllegalStateException("UI operation failed", error);
        return result.get();
    }

    public void defer(final Runnable task) { this.display.asyncExec(task); }

    public ObjectNode listWindows(final ObjectMapper mapper) {
        pruneDisposed();
        final ArrayNode windows = mapper.createArrayNode();
        for (final Shell shell : this.display.getShells()) if (!shell.isDisposed()) {
            final ObjectNode node = basic(mapper, register(shell), "window", shell.getText(), shell.isEnabled(), shell.isVisible());
            node.put("active", this.display.getActiveShell() == shell);
            windows.add(node);
        }
        return mapper.createObjectNode().set("windows", windows);
    }

    public ObjectNode snapshot(final ObjectMapper mapper, final long shellId, final int maxDepth) {
        final Widget widget = this.objects.get(shellId);
        if (!(widget instanceof Shell shell) || shell.isDisposed()) throw new IllegalArgumentException("Unknown or stale shell_id: " + shellId);
        if (maxDepth < 0 || maxDepth > 30) throw new IllegalArgumentException("maxDepth must be between 0 and 30");
        pruneDisposed();
        return mapper.createObjectNode().set("snapshot", shellNode(mapper, shell, maxDepth, 0));
    }

    public Widget requireWidget(final long id) {
        final Widget widget = this.objects.get(id);
        if (widget == null || widget.isDisposed()) throw new IllegalArgumentException("Unknown or stale widget id: " + id);
        return widget;
    }

    private ObjectNode shellNode(final ObjectMapper mapper, final Shell shell, final int maxDepth, final int depth) {
        final ObjectNode node = basic(mapper, register(shell), "window", shell.getText(), shell.isEnabled(), shell.isVisible());
        if (depth < maxDepth) addControls(mapper, node, shell, maxDepth, depth + 1);
        final Menu menu = shell.getMenuBar();
        if (menu != null && !menu.isDisposed()) node.set("menu", menuNode(mapper, menu, maxDepth, depth + 1));
        return node;
    }

    private void addControls(final ObjectMapper mapper, final ObjectNode parent, final Composite composite, final int maxDepth, final int depth) {
        final ArrayNode children = mapper.createArrayNode();
        for (final Control control : composite.getChildren()) {
            if (control.isDisposed()) continue;
            final ObjectNode child = basic(mapper, register(control), role(control), label(control), control.isEnabled(), control.isVisible());
            addState(mapper, child, control);
            if (depth < maxDepth) {
                if (control instanceof Tree tree) addTreeItems(mapper, child, tree.getItems(), maxDepth, depth + 1);
                else if (control instanceof Table table) addTableItems(mapper, child, table.getItems());
                else if (control instanceof ToolBar bar) addToolItems(mapper, child, bar.getItems());
                else if (control instanceof Composite nested) addControls(mapper, child, nested, maxDepth, depth + 1);
            }
            final Menu context = control.getMenu();
            if (context != null && !context.isDisposed()) child.set("menu", menuNode(mapper, context, maxDepth, depth + 1));
            children.add(child);
        }
        parent.set("children", children);
    }

    private void addTreeItems(final ObjectMapper mapper, final ObjectNode parent, final TreeItem[] items, final int maxDepth, final int depth) {
        final ArrayNode children = mapper.createArrayNode();
        for (final TreeItem item : items) if (!item.isDisposed()) {
            final ObjectNode child = basic(mapper, register(item), "treeitem", item.getText(), !item.getGrayed(), item.getParent().isVisible());
            child.put("expanded", item.getExpanded()).put("checked", item.getChecked());
            if (depth < maxDepth && item.getExpanded()) addTreeItems(mapper, child, item.getItems(), maxDepth, depth + 1);
            children.add(child);
        }
        parent.set("children", children);
    }

    private void addTableItems(final ObjectMapper mapper, final ObjectNode parent, final TableItem[] items) {
        final ArrayNode children = mapper.createArrayNode();
        for (final TableItem item : items) if (!item.isDisposed()) {
            final ObjectNode child = basic(mapper, register(item), "tableitem", item.getText(), !item.getGrayed(), item.getParent().isVisible());
            child.put("checked", item.getChecked()); children.add(child);
        }
        parent.set("children", children);
    }

    private void addToolItems(final ObjectMapper mapper, final ObjectNode parent, final ToolItem[] items) {
        final ArrayNode children = mapper.createArrayNode();
        for (final ToolItem item : items) if (!item.isDisposed()) {
            final ObjectNode child = basic(mapper, register(item), "toolitem", item.getText(), item.isEnabled(), item.getParent().isVisible());
            child.put("checked", item.getSelection()); children.add(child);
        }
        parent.set("children", children);
    }

    private ObjectNode menuNode(final ObjectMapper mapper, final Menu menu, final int maxDepth, final int depth) {
        final ObjectNode node = basic(mapper, register(menu), "menu", "", menu.isEnabled(), menu.isVisible());
        final ArrayNode children = mapper.createArrayNode();
        if (depth <= maxDepth) for (final MenuItem item : menu.getItems()) if (!item.isDisposed()) {
            final ObjectNode child = basic(mapper, register(item), "menuitem", item.getText(), item.isEnabled(), menu.isVisible());
            child.put("checked", item.getSelection());
            final Menu submenu = item.getMenu();
            if (submenu != null && !submenu.isDisposed()) child.set("children", menuNode(mapper, submenu, maxDepth, depth + 1).get("children"));
            children.add(child);
        }
        node.set("children", children); return node;
    }

    private static ObjectNode basic(final ObjectMapper mapper, final long id, final String role, final String label, final boolean enabled, final boolean visible) {
        return mapper.createObjectNode().put("id", id).put("role", role).put("label", label == null ? "" : label).put("enabled", enabled).put("visible", visible);
    }

    private static String role(final Control c) {
        if (c instanceof Button) return "button"; if (c instanceof Text) return "text"; if (c instanceof StyledText) return "texteditor";
        if (c instanceof Combo) return "combo"; if (c instanceof org.eclipse.swt.widgets.List) return "list"; if (c instanceof Tree) return "tree";
        if (c instanceof Table) return "table"; if (c instanceof Label) return "label"; if (c instanceof ToolBar) return "toolbar";
        if (c instanceof Group) return "group"; return "composite";
    }

    private static String label(final Control c) {
        if (c instanceof Button b) return b.getText(); if (c instanceof Label l) return l.getText(); if (c instanceof Group g) return g.getText();
        if (c instanceof Text t) return t.getMessage(); if (c instanceof Combo combo) return combo.getText(); return "";
    }

    private static void addState(final ObjectMapper mapper, final ObjectNode node, final Control c) {
        if (c instanceof Text t) node.put("value", t.getText()); else if (c instanceof StyledText t) node.put("value", t.getText());
        else if (c instanceof Combo combo) { node.put("value", combo.getText()).put("selection", combo.getSelectionIndex()); node.set("items", mapper.valueToTree(combo.getItems())); }
        else if (c instanceof org.eclipse.swt.widgets.List list) { node.put("selection", list.getSelectionIndex()); node.set("items", mapper.valueToTree(list.getItems())); }
        else if (c instanceof Button b) node.put("checked", b.getSelection()); else if (c instanceof Tree t) node.put("selection", t.getSelectionCount());
        else if (c instanceof Table t) node.put("selection", t.getSelectionIndex());
    }

    private void pruneDisposed() {
        this.objects.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isDisposed());
        this.ids.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isDisposed());
    }

    private long register(final Widget widget) {
        final Long existing = this.ids.get(widget); if (existing != null) return existing.longValue();
        final long id = this.nextId++; this.ids.put(widget, id); this.objects.put(id, widget); return id;
    }

    @FunctionalInterface public interface UiCall { JsonNode run() throws Exception; }
}
