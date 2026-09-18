/*
 * Copyright 2013-2025 Docaposte
 *
 * This file is part of Modelio.
 *
 * Modelio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Modelio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Modelio.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.modelio.app.ui.preference;

import com.modeliosoft.modelio.javadesigner.annotations.objid;
import org.modelio.app.ui.plugin.AppUi;

@objid ("0401ca00-582f-4de4-b5a4-a51d39198686")
public enum ThemeEnum {
    @objid ("e750da60-72f7-434a-a0f7-729b0b775c15")
    LIGHT(AppUi.I18N.getString("Preference.ThemeLabel.light"), "org.modelio.app.theme.light"),
    @objid ("4513ea07-674a-4785-a18a-31b307cae19f")
    DARK(AppUi.I18N.getString("Preference.ThemeLabel.dark"), "org.modelio.app.theme.obscure"),
    @objid ("96b90424-cd18-46d4-a697-352bc07ce156")
    SYSTEM(AppUi.I18N.getString("Preference.ThemeLabel.system"), "org.modelio.app.theme.system");

    @objid ("93da8b49-fe7e-475a-bcb3-c8eed009ecf7")
    private String label;

    @objid ("00b74af7-4de1-4728-a8fe-b8acb1eb8224")
    private String value;

    @objid ("d2f45346-e57e-4e2d-a897-6b47278d486f")
    private ThemeEnum(String label, String value) {
        this.label = label;
        this.value =value;
    }

    @objid ("1eaf740a-e20d-48e6-8606-664bfae38172")
    public String getLabel() {
        return label;
    }

    @objid ("c6f64bd4-f99b-424c-b8d9-933a272204ba")
    public String getThemeId() {
        return value;
    }

}
