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

package ca.sqlpower.wabit.swingui.action;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.wabit.report.chart.Chart;
import ca.sqlpower.wabit.report.chart.ChartType;
import ca.sqlpower.wabit.report.chart.ChartUtil;
import ca.sqlpower.wabit.rs.WabitResultSetProducer;
import ca.sqlpower.wabit.swingui.WabitSwingSession;
import ca.sqlpower.wabit.swingui.WabitSwingSessionImpl;

/**
 * An action creates a new Chart and adds it to the workspace. The new chart can
 * optionally have its data provider set to a predetermined value.
 */
public class NewChartAction extends AbstractAction {
    
	private static final Icon NEW_CHART_ICON = new ImageIcon(
	        WabitSwingSessionImpl.class.getClassLoader().getResource("icons/chart-16.png"));
	
    private final WabitSwingSession session;

    private final WabitResultSetProducer dataProvider;

    /**
     * Creates an action which, when invoked, creates new chart with the given
     * default query, and adds it to the workspace.
     * 
     * @param session
     *            The session whose workspace the new chart will belong to.
     * @param dataProvider
     *            The ResultSetProducer that will provide data to the new
     *            chart. Null means not to set an initial data provider.
     */
    public NewChartAction(WabitSwingSession session, WabitResultSetProducer dataProvider) {
        super("New Chart", NEW_CHART_ICON);
        this.session = session;
        this.dataProvider = dataProvider;
    }

    /**
     * Creates an action which, when invoked, creates new chart with no default
     * query, and adds it to the workspace.
     * 
     * @param session
     */
    public NewChartAction(WabitSwingSession session) {
        this(session, null);
    }

    public void actionPerformed(ActionEvent e) {
        
    	Chart chart = new Chart();
        chart.setType(ChartType.BAR);
        
        if (dataProvider != null) {
            try {
                chart.setName(dataProvider.getName() + " Chart");
                chart.setQuery(dataProvider);
                ChartUtil.setDefaults(chart);
            } catch (Exception ex) {
                SPSUtils.showExceptionDialogNoReport(
                        session.getTree(), "Failed to create chart", ex);
                return;
            }
        } else {
            chart.setName("New Chart");
        }
        
        session.getWorkspace().addChart(chart);
    }

}
