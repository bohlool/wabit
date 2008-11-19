/*
 * Copyright (c) 2008, SQL Power Group Inc.
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

import ca.sqlpower.wabit.Query;
import ca.sqlpower.wabit.WabitProject;
import ca.sqlpower.wabit.report.ContentBox;
import ca.sqlpower.wabit.report.Label;
import ca.sqlpower.wabit.report.Layout;
import ca.sqlpower.wabit.report.Page;
import ca.sqlpower.wabit.report.ReportContentRenderer;
import ca.sqlpower.wabit.report.ResultSetRenderer;
import ca.sqlpower.wabit.report.Label.HorizontalAlignment;
import ca.sqlpower.wabit.swingui.WabitSwingSession;
import ca.sqlpower.wabit.swingui.report.ReportLayoutPanel;

public class CreateLayoutFromQueryAction extends AbstractAction {
    
    private static final Icon ADD_LAYOUT_ICON = new ImageIcon(CreateLayoutFromQueryAction.class.getResource("/icons/layout_add.png"));

    /**
     * The project we will add the new layout to when this action is invoked.
     */
    private final WabitProject project;

    /**
     * The query we will fill the layout from when this action is invoked.
     */
    private final Query query;

    private final WabitSwingSession session;

    public CreateLayoutFromQueryAction(WabitSwingSession session, WabitProject wabitProject, Query query) {
        super("Create Layout...", ADD_LAYOUT_ICON);
        this.session = session;
        this.project = wabitProject;
        this.query = query;
    }
    
    public void actionPerformed(ActionEvent e) {
        Layout newLayout = createDefaultLayout(project, query);
        session.setEditorPanel(newLayout);
    }
    
    /**
     * Creates a new layout with standard margins, headers, and footers. The body
     * of the content is provided by the given query.
     * 
     * @param project The project that the new layout will be added to
     * @param query The query to use for the body content.
     * @return The new layout that was added to the project.
     */
    public static Layout createDefaultLayout(WabitProject project, Query query) {
        Layout l = new Layout(query.getName() + " Layout");
        Page p = l.getPage();
        final int pageBodyWidth = p.getRightMarginOffset() - p.getLeftMarginOffset();
        final int pageBodyHeight = p.getLowerMarginOffset() - p.getUpperMarginOffset();
        
        ContentBox body = new ContentBox();
        ReportContentRenderer bodyRenderer = new ResultSetRenderer(query);
        body.setContentRenderer(bodyRenderer);
        p.addContentBox(body);
        body.setWidth(pageBodyWidth);
        body.setHeight(pageBodyHeight);
        body.setX(p.getLeftMarginOffset());
        body.setY(p.getUpperMarginOffset());
        
        ContentBox header = new ContentBox();
        header.setContentRenderer(new Label(l, "Header!"));
        p.addContentBox(header);
        header.setWidth(pageBodyWidth / 2);
        header.setHeight(Page.DPI / 2); // TODO base this on the actual font metrics or something
        header.setX(p.getLeftMarginOffset());
        header.setY(p.getUpperMarginOffset() - header.getHeight());
        
        // shameless self promotion
        ContentBox shameless = new ContentBox();
        Label selfPromotionLabel = new Label(l,
                "This report was produced by SQL Power's Wabit\n" +
                "[insert branded dancing bunnies]");
        selfPromotionLabel.setHorizontalAlignment(HorizontalAlignment.RIGHT);
        shameless.setContentRenderer(selfPromotionLabel);
        p.addContentBox(shameless);
        shameless.setWidth(pageBodyWidth - header.getWidth());
        shameless.setHeight(Page.DPI / 2); // TODO base this on the actual font metrics or something
        shameless.setX(header.getX() + header.getWidth());
        shameless.setY(p.getUpperMarginOffset() - shameless.getHeight());
        
        ContentBox footer = new ContentBox();
        Label footerLabel = new Label(l, "Page ${page.number} of ${page.totalPages}");
        footerLabel.setHorizontalAlignment(HorizontalAlignment.CENTER);
        footer.setContentRenderer(footerLabel);
        // TODO add option for horizontal and vertical alignment (left, center, right, top, middle, bottom) in label
        p.addContentBox(footer);
        footer.setWidth(pageBodyWidth);
        footer.setHeight(Page.DPI / 2); // TODO base this on the actual font metrics or something
        footer.setX(p.getLeftMarginOffset());
        footer.setY(p.getLowerMarginOffset());
        
        project.addLayout(l);
        
        return l;
    }
}