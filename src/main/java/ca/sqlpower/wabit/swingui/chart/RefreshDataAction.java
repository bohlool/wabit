/*
 * Copyright (c) 2009, SQL Power Group Inc.
 *
 * This file is part of Wabit.
 *
 * Wabit is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wabit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.wabit.swingui.chart;

import java.awt.Component;
import java.awt.event.ActionEvent;

import javax.annotation.Nonnull;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.wabit.report.chart.Chart;

/**
 * Refreshes the data in a chart.
 */
public class RefreshDataAction extends AbstractAction {

    private static final Icon ICON =
        new ImageIcon(RefreshDataAction.class.getResource("/icons/32x32/refresh.png"));
    
    /**
     * The chart to refresh every time this action is invoked.
     */
    private final Chart chart;

    /**
     * Owning component of any dialogs created by this action.
     */
    private final Component dialogOwner;
    
    /**
     * 
     * @param chart The chart to refresh. Must not be null.
     */
    public RefreshDataAction(@Nonnull Chart chart, Component dialogOwner) {
        super("Refresh", ICON);
        this.chart = chart;
        this.dialogOwner = dialogOwner;
    }

    public void actionPerformed(ActionEvent e) {
        try {
            if (chart.getQuery() != null) {
                chart.getQuery().execute();
            }
        } catch (Exception ex) {
            SPSUtils
            .showExceptionDialogNoReport(dialogOwner, "Refresh failed", ex);
        }
    }
}
