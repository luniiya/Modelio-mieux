package org.modelio.app.mcp.server.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.PlatformUI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.modelio.app.mcp.server.protocol.McpDispatcher;
import org.modelio.app.mcp.server.protocol.McpTool;

/**
 * MCP tools exposing GUI automation over the running Modelio SWT UI:
 * inspecting the widget tree ({@code ui_list_windows}, {@code ui_snapshot})
 * and driving it ({@code ui_click}, {@code ui_set_text},
 * {@code ui_set_checked}, {@code ui_select}, {@code ui_expand},
 * {@code ui_invoke_menu}), plus {@code ide_restart}. Mirrors
 * {@code org.eclipse.mieux.mcp.server.ui.UiTools} from eclipse-mieux, adapted
 * to Modelio's {@link McpTool}/Jackson-based protocol layer instead of that
 * project's own registry/JSON types.
 */
public final class ModelioUiTools {

    private ModelioUiTools() {
    }

    /** Registers all UI automation tools with the given dispatcher. */
    public static void registerAll(final McpDispatcher dispatcher, final ModelioUiAutomation automation) {
        dispatcher.registerTool(new ListWindowsTool(automation));
        dispatcher.registerTool(new SnapshotTool(automation));
        dispatcher.registerTool(new ClickTool(automation));
        dispatcher.registerTool(new SetTextTool(automation));
        dispatcher.registerTool(new SetCheckedTool(automation));
        dispatcher.registerTool(new SelectTool(automation));
        dispatcher.registerTool(new ExpandTool(automation));
        dispatcher.registerTool(new InvokeMenuTool(automation));
        dispatcher.registerTool(new RestartIdeTool(automation));
    }

    /** Base class: runs the tool body on the SWT UI thread via {@link ModelioUiAutomation#call}. */
    private abstract static class UiTool implements McpTool {
        final ModelioUiAutomation automation;

        UiTool(final ModelioUiAutomation automation) {
            this.automation = automation;
        }

        @Override public final JsonNode call(final JsonNode arguments, final ObjectMapper mapper) throws Exception {
            return this.automation.call(() -> executeOnUi(arguments, mapper));
        }

        abstract JsonNode executeOnUi(JsonNode arguments, ObjectMapper mapper) throws Exception;
    }

    private static final class ListWindowsTool extends UiTool {
        ListWindowsTool(final ModelioUiAutomation automation) { super(automation); }

        @Override public String name() { return "ui_list_windows"; }

        @Override public String description() {
            return "List open Modelio windows and get shell ids for ui_snapshot.";
        }

        @Override public ObjectNode inputSchema(final ObjectMapper mapper) { return emptySchema(mapper); }

        @Override JsonNode executeOnUi(final JsonNode arguments, final ObjectMapper mapper) {
            return this.automation.listWindows(mapper);
        }
    }

    private static final class SnapshotTool extends UiTool {
        SnapshotTool(final ModelioUiAutomation automation) { super(automation); }

        @Override public String name() { return "ui_snapshot"; }

        @Override public String description() {
            return "Return a structured, text-only snapshot of a Modelio window's widgets and menus.";
        }

        @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
            final ObjectNode schema = emptySchema(mapper);
            final ObjectNode properties = (ObjectNode) schema.get("properties");
            properties.putObject("shell_id").put("type", "integer").put("description", "Shell id from ui_list_windows");
            properties.putObject("maxDepth").put("type", "integer").put("description", "Maximum widget-tree depth, from 0 to 30");
            schema.putArray("required").add("shell_id");
            return schema;
        }

        @Override JsonNode executeOnUi(final JsonNode arguments, final ObjectMapper mapper) {
            final long shellId = requiredLong(arguments, "shell_id");
            final int maxDepth = arguments.path("maxDepth").asInt(8);
            return this.automation.snapshot(mapper, shellId, maxDepth);
        }
    }

    private static final class ClickTool extends UiTool {
        ClickTool(final ModelioUiAutomation automation) { super(automation); }

        @Override public String name() { return "ui_click"; }

        @Override public String description() {
            return "Activate a button, toolbar item, menu item, tree item, table item, or control from the latest snapshot.";
        }

        @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
            return idSchema(mapper, "Widget id from the latest snapshot");
        }

        @Override JsonNode executeOnUi(final JsonNode arguments, final ObjectMapper mapper) {
            final long id = requiredLong(arguments, "id");
            final Widget widget = this.automation.requireWidget(id);
            if (!isEnabled(widget)) {
                throw new IllegalStateException("Widget is disabled: " + id);
            }
            this.automation.defer(() -> click(widget));
            return mapper.createObjectNode().put("id", id).put("clicked", true).put("queued", true);
        }
    }

    private static final class SetTextTool extends UiTool {
        SetTextTool(final ModelioUiAutomation automation) { super(automation); }

        @Override public String name() { return "ui_set_text"; }

        @Override public String description() { return "Set the value of a text field, editable combo box, or text editor."; }

        @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
            final ObjectNode schema = emptySchema(mapper);
            final ObjectNode properties = (ObjectNode) schema.get("properties");
            properties.putObject("id").put("type", "integer").put("description", "Text, combo, or editor id");
            properties.putObject("text").put("type", "string").put("description", "New text");
            schema.putArray("required").add("id").add("text");
            return schema;
        }

        @Override JsonNode executeOnUi(final JsonNode arguments, final ObjectMapper mapper) {
            final long id = requiredLong(arguments, "id");
            final String text = requiredText(arguments, "text");
            final Widget widget = this.automation.requireWidget(id);
            if (widget instanceof Text field) {
                field.setText(text);
                field.notifyListeners(SWT.Modify, new Event());
            } else if (widget instanceof Combo combo) {
                combo.setText(text);
                combo.notifyListeners(SWT.Modify, new Event());
            } else if (widget instanceof StyledText editor) {
                editor.setText(text);
                editor.notifyListeners(SWT.Modify, new Event());
            } else {
                throw new IllegalArgumentException("Widget is not a text field, combo, or editor: " + id);
            }
            return mapper.createObjectNode().put("id", id).put("value", text);
        }
    }

    private static final class SetCheckedTool extends UiTool {
        SetCheckedTool(final ModelioUiAutomation automation) { super(automation); }

        @Override public String name() { return "ui_set_checked"; }

        @Override public String description() {
            return "Set the checked/selected state of a checkbox, radio, toggle, menu item, or toolbar item.";
        }

        @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
            final ObjectNode schema = emptySchema(mapper);
            final ObjectNode properties = (ObjectNode) schema.get("properties");
            properties.putObject("id").put("type", "integer").put("description", "Checkable widget id");
            properties.putObject("checked").put("type", "boolean");
            schema.putArray("required").add("id").add("checked");
            return schema;
        }

        @Override JsonNode executeOnUi(final JsonNode arguments, final ObjectMapper mapper) {
            final long id = requiredLong(arguments, "id");
            final boolean checked = requiredBoolean(arguments, "checked");
            final Widget widget = this.automation.requireWidget(id);
            if (widget instanceof Button button) {
                button.setSelection(checked);
            } else if (widget instanceof MenuItem item) {
                item.setSelection(checked);
            } else if (widget instanceof ToolItem item) {
                item.setSelection(checked);
            } else {
                throw new IllegalArgumentException("Widget is not checkable: " + id);
            }
            return mapper.createObjectNode().put("id", id).put("checked", checked);
        }
    }

    private static final class SelectTool extends UiTool {
        SelectTool(final ModelioUiAutomation automation) { super(automation); }

        @Override public String name() { return "ui_select"; }

        @Override public String description() { return "Select an item in a combo, list, tree, or table using index or visible text."; }

        @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
            final ObjectNode schema = emptySchema(mapper);
            final ObjectNode properties = (ObjectNode) schema.get("properties");
            properties.putObject("id").put("type", "integer").put("description", "Selectable widget or item id");
            properties.putObject("index").put("type", "integer").put("description", "Zero-based item index");
            properties.putObject("text").put("type", "string").put("description", "Item text");
            schema.putArray("required").add("id");
            return schema;
        }

        @Override JsonNode executeOnUi(final JsonNode arguments, final ObjectMapper mapper) throws Exception {
            final long id = requiredLong(arguments, "id");
            final Widget widget = this.automation.requireWidget(id);
            final int index;
            if (widget instanceof TreeItem item) {
                final TreeItem parentItem = item.getParentItem();
                item.getParent().setSelection(item);
                index = parentItem == null ? item.getParent().indexOf(item) : parentItem.indexOf(item);
            } else if (widget instanceof TableItem item) {
                item.getParent().setSelection(item);
                index = item.getParent().indexOf(item);
            } else {
                index = selectionIndex(arguments, widget);
                if (widget instanceof Combo combo) {
                    combo.select(index);
                } else if (widget instanceof org.eclipse.swt.widgets.List list) {
                    list.select(index);
                } else if (widget instanceof Tree tree) {
                    tree.setSelection(tree.getItem(index));
                } else if (widget instanceof Table table) {
                    table.setSelection(table.getItem(index));
                } else {
                    throw new IllegalArgumentException("Widget is not selectable: " + id);
                }
            }
            final Widget parent = widget instanceof TreeItem item ? item.getParent()
                    : widget instanceof TableItem item ? item.getParent() : widget;
            parent.notifyListeners(SWT.Selection, new Event());
            return mapper.createObjectNode().put("id", id).put("index", index);
        }
    }

    private static final class ExpandTool extends UiTool {
        ExpandTool(final ModelioUiAutomation automation) { super(automation); }

        @Override public String name() { return "ui_expand"; }

        @Override public String description() { return "Expand or collapse a tree node from the latest snapshot."; }

        @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
            final ObjectNode schema = emptySchema(mapper);
            final ObjectNode properties = (ObjectNode) schema.get("properties");
            properties.putObject("id").put("type", "integer").put("description", "Tree item id");
            properties.putObject("expanded").put("type", "boolean");
            schema.putArray("required").add("id");
            return schema;
        }

        @Override JsonNode executeOnUi(final JsonNode arguments, final ObjectMapper mapper) {
            final long id = requiredLong(arguments, "id");
            final Widget widget = this.automation.requireWidget(id);
            if (!(widget instanceof TreeItem item)) {
                throw new IllegalArgumentException("Widget is not a tree item: " + id);
            }
            final JsonNode requested = arguments.get("expanded");
            final boolean expanded = requested == null || !requested.isBoolean() ? !item.getExpanded() : requested.asBoolean();
            item.setExpanded(expanded);
            return mapper.createObjectNode().put("id", id).put("expanded", expanded);
        }
    }

    private static final class InvokeMenuTool extends UiTool {
        InvokeMenuTool(final ModelioUiAutomation automation) { super(automation); }

        @Override public String name() { return "ui_invoke_menu"; }

        @Override public String description() { return "Invoke a menu item from the latest snapshot."; }

        @Override public ObjectNode inputSchema(final ObjectMapper mapper) {
            return idSchema(mapper, "Menu item id");
        }

        @Override JsonNode executeOnUi(final JsonNode arguments, final ObjectMapper mapper) {
            final long id = requiredLong(arguments, "id");
            final Widget widget = this.automation.requireWidget(id);
            if (!(widget instanceof MenuItem item)) {
                throw new IllegalArgumentException("Widget is not a menu item: " + id);
            }
            if (!item.isEnabled()) {
                throw new IllegalStateException("Menu item is disabled: " + id);
            }
            this.automation.defer(() -> click(item));
            return mapper.createObjectNode().put("id", id).put("invoked", true).put("queued", true);
        }
    }

    private static final class RestartIdeTool extends UiTool {
        RestartIdeTool(final ModelioUiAutomation automation) { super(automation); }

        @Override public String name() { return "ide_restart"; }

        @Override public String description() {
            return "Restart the running Modelio workbench after this MCP response returns; use this instead of opening another Modelio process.";
        }

        @Override public ObjectNode inputSchema(final ObjectMapper mapper) { return emptySchema(mapper); }

        @Override JsonNode executeOnUi(final JsonNode arguments, final ObjectMapper mapper) {
            this.automation.defer(() -> {
                if (PlatformUI.isWorkbenchRunning()) {
                    PlatformUI.getWorkbench().restart();
                }
            });
            return mapper.createObjectNode().put("restarting", true).put("queued", true);
        }
    }

    private static void click(final Widget widget) {
        if (widget instanceof Button button) {
            final int style = button.getStyle();
            if ((style & (SWT.CHECK | SWT.TOGGLE)) != 0) {
                button.setSelection(!button.getSelection());
            } else if ((style & SWT.RADIO) != 0) {
                button.setSelection(true);
            }
            button.notifyListeners(SWT.Selection, new Event());
        } else if (widget instanceof MenuItem item) {
            if ((item.getStyle() & (SWT.CHECK | SWT.RADIO)) != 0) {
                item.setSelection(!item.getSelection());
            }
            item.notifyListeners(SWT.Selection, new Event());
        } else if (widget instanceof ToolItem item) {
            if ((item.getStyle() & (SWT.CHECK | SWT.RADIO)) != 0) {
                item.setSelection(!item.getSelection());
            }
            item.notifyListeners(SWT.Selection, new Event());
        } else if (widget instanceof TreeItem item) {
            item.getParent().setSelection(item);
            item.getParent().notifyListeners(SWT.Selection, new Event());
        } else if (widget instanceof TableItem item) {
            item.getParent().setSelection(item);
            item.getParent().notifyListeners(SWT.Selection, new Event());
        } else if (widget instanceof Control control) {
            control.notifyListeners(SWT.Selection, new Event());
        } else {
            throw new IllegalArgumentException("Widget is not clickable: " + widget.getClass().getSimpleName());
        }
    }

    private static boolean isEnabled(final Widget widget) {
        if (widget instanceof Control control) {
            return control.isEnabled();
        }
        if (widget instanceof MenuItem item) {
            return item.isEnabled();
        }
        if (widget instanceof ToolItem item) {
            return item.isEnabled();
        }
        return true;
    }

    private static int selectionIndex(final JsonNode arguments, final Widget widget) {
        final JsonNode indexNode = arguments.get("index");
        if (indexNode != null && indexNode.isIntegralNumber()) {
            return indexNode.asInt();
        }
        final String text = requiredText(arguments, "text");
        final String[] items;
        if (widget instanceof Combo combo) {
            items = combo.getItems();
        } else if (widget instanceof org.eclipse.swt.widgets.List list) {
            items = list.getItems();
        } else if (widget instanceof Tree tree) {
            final TreeItem[] treeItems = tree.getItems();
            items = new String[treeItems.length];
            for (int i = 0; i < treeItems.length; i++) {
                items[i] = treeItems[i].getText();
            }
        } else if (widget instanceof Table table) {
            final TableItem[] tableItems = table.getItems();
            items = new String[tableItems.length];
            for (int i = 0; i < tableItems.length; i++) {
                items[i] = tableItems[i].getText();
            }
        } else {
            items = null;
        }
        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                if (text.equals(items[i])) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("No item named " + text);
    }

    private static long requiredLong(final JsonNode arguments, final String field) {
        final JsonNode node = arguments.get(field);
        if (node == null || !node.isIntegralNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return node.asLong();
    }

    private static String requiredText(final JsonNode arguments, final String field) {
        final JsonNode node = arguments.get(field);
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return node.asText();
    }

    private static boolean requiredBoolean(final JsonNode arguments, final String field) {
        final JsonNode node = arguments.get(field);
        if (node == null || !node.isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return node.asBoolean();
    }

    private static ObjectNode emptySchema(final ObjectMapper mapper) {
        final ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.createObjectNode());
        schema.putArray("required");
        return schema;
    }

    private static ObjectNode idSchema(final ObjectMapper mapper, final String idDescription) {
        final ObjectNode schema = emptySchema(mapper);
        final ObjectNode properties = (ObjectNode) schema.get("properties");
        properties.putObject("id").put("type", "integer").put("description", idDescription);
        schema.putArray("required").add("id");
        return schema;
    }
}
