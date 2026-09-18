/*
 * Copyright 2013-2025 Docaposte
 *
 * This file is part of Modelio.
 */
package org.modelio.diagram.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.draw2d.geometry.Point;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.RequestConstants;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.requests.ChangeBoundsRequest;

/** In-process clipboard for diagram edit parts. */
final class DiagramElementClipboard {
    private static final List<EditPart> contents = new ArrayList<>();

    private DiagramElementClipboard() {
    }

    static void copy(GraphicalViewer viewer) {
        contents.clear();
        for (Object selected : viewer.getSelectedEditParts()) {
            if (selected instanceof GraphicalEditPart && ((EditPart) selected).isSelectable()) {
                contents.add((EditPart) selected);
            }
        }
    }

    static boolean canPaste() {
        return !contents.isEmpty();
    }

    static void paste(GraphicalViewer viewer) {
        if (contents.isEmpty() || viewer.getContents() == null) {
            return;
        }

        final ChangeBoundsRequest request = new ChangeBoundsRequest(RequestConstants.REQ_CLONE);
        request.setEditParts(new ArrayList<>(contents));
        request.setMoveDelta(new Point(20, 20));
        request.setLocation(((GraphicalEditPart) contents.get(0)).getFigure().getBounds().getCenter().getCopy());

        final Command command = viewer.getContents().getCommand(request);
        if (command != null && command.canExecute()) {
            viewer.getEditDomain().getCommandStack().execute(command);
            viewer.getControl().setFocus();
        }
    }
}
