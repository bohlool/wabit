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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Ellipse2D;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.StandardChartTheme;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.xy.XYDataset;
import org.olap4j.CellSet;

import ca.sqlpower.swingui.ColourScheme;
import ca.sqlpower.wabit.QueryCache;
import ca.sqlpower.wabit.olap.OlapQuery;
import ca.sqlpower.wabit.olap.QueryInitializationException;
import ca.sqlpower.wabit.report.chart.Chart;
import ca.sqlpower.wabit.report.chart.ColumnIdentifier;
import ca.sqlpower.wabit.report.chart.ColumnRole;
import ca.sqlpower.wabit.report.chart.DatasetTypes;
import ca.sqlpower.wabit.report.chart.ExistingChartTypes;
import ca.sqlpower.wabit.report.chart.LegendPosition;

/**
 * This is a collection of swing specific chart utilities. You should not
 * make an instance of this class.
 */
public class ChartSwingUtil {

    private static final Logger logger = Logger.getLogger(ChartSwingUtil.class);
    
    /**
     * This is a collection of swing specific chart utilities. You should not
     * make an instance of this class.
     */
    private ChartSwingUtil() {/* don't */}
    
    /**
     * Creates a JFreeChart based on the given query results and how the columns
     * are defined in the chart. The query given MUST be a query type that is
     * allowed in the chart, either a {@link QueryCache} or an {@link OlapQuery}
     * . Anything else will throw an exception.
     * 
     * @param columnNamesInOrder
     *            The order the columns should come in in the chart. The first
     *            column defined as a series will be the first bar or line, the
     *            second defined as a series will be the second bar or line in
     *            the chart and so on. The order of the category columns is also
     *            enforced here and will decide the order the category names are
     *            concatenated in.
     * @param columnsToDataTypes
     *            Defines which columns are series and which ones are
     *            categories.
     * @param data
     *            This must be either a {@link ResultSet} or a {@link CellSet}
     *            that contains the data that will be displayed in the chart.
     *            TODO If we change the cell set to be a cached row set this
     *            just becomes a result set.
     * @param chartType
     *            The type of chart to create from the data.
     * @param legendPosition
     *            The position where the legend will appear or NONE if it will
     *            not be displayed.
     * @param chartName
     *            The name of the chart.
     * @param yaxisName
     *            The name of the y axis.
     * @param xaxisName
     *            The name of the x axis.
     * @return A chart based on the data in the query of the given type, or null
     *         if the given chart is currently unable to produce a result set.
     */
    public static JFreeChart createChartFromQuery(Chart c) throws SQLException, QueryInitializationException, InterruptedException {
        logger.debug("Creating JFreeChart for Wabit chart " + c);
        ExistingChartTypes chartType = c.getType();
        
        if (c.getResultSet() == null) {
            logger.debug("Returning null (chart's result set was null)");
            return null;
        }
        
        if (chartType.getType().equals(DatasetTypes.CATEGORY)) {
            JFreeChart categoryChart = createCategoryChart(c);
            logger.debug("Made a new category chart: " + categoryChart);
            return categoryChart;
        } else if (chartType.getType().equals(DatasetTypes.XY)) {
            JFreeChart xyChart = createXYChart(c);
            logger.debug("Made a new XY chart: " + xyChart);
            return xyChart;
        } else {
            throw new IllegalStateException(
                    "Unknown chart dataset type " + chartType.getType());
        }
    }

    /**
     * Creates a JFreeChart based on the given query results and how the columns
     * are defined in the chart.
     * 
     * @param c
     *            The chart from which to extract the data set and configure the
     *            JFreeChart instance
     * @return A chart based on the data in the query of the given type, or null
     *         if any of the following conditions hold:
     *         <ul>
     *          <li>The chart is currently unable to produce a result set
     *          <li>The chart does not have at least one category and one series
     *             column defined
     *         </ul>
     */
    private static JFreeChart createCategoryChart(Chart c) {
        
        if (c.getType().getType() != DatasetTypes.CATEGORY) {
            throw new IllegalStateException(
                    "Chart is not currently set up as a category chart " +
                    "(it is a " + c.getType() + ")");
        }
        
        List<ColumnIdentifier> columnNamesInOrder = c.getColumnNamesInOrder();
        LegendPosition legendPosition = c.getLegendPosition(); 
        String chartName = c.getName();
        String yaxisName = c.getYaxisName();
        String xaxisName = c.getXaxisName();

        boolean containsCategory = false;
        boolean containsSeries = false;
        for (ColumnIdentifier col : columnNamesInOrder) {
            if (col.getRoleInChart().equals(ColumnRole.CATEGORY)) {
                containsCategory = true;
            } else if (col.getRoleInChart().equals(ColumnRole.SERIES)) {
                containsSeries = true;
            }
        }
        if (!containsCategory || !containsSeries) {
            return null;
        }
        
        List<ColumnIdentifier> categoryColumns = c.findCategoryColumns();
        List<String> categoryColumnNames = new ArrayList<String>();
        for (ColumnIdentifier identifier : categoryColumns) {
            categoryColumnNames.add(identifier.getName());
        }
        
        // because of the chart type check at the beginning of this method, the
        // following cast should always work
        CategoryDataset dataset = (CategoryDataset) c.createDataset();
        
        return createCategoryChartFromDataset(dataset, c.getType(), 
                legendPosition, chartName, yaxisName, xaxisName);
    }

    /**
     * This is a helper method for creating charts and is split off as it does
     * only the chart creation and tweaking of a category chart. All of the
     * decisions for what columns are defined as what and how the data is stored
     * should be done in the method that calls this.
     * <p>
     * Given a dataset and other chart properties this will create an
     * appropriate JFreeChart.
     * 
     * @param dataset
     *            The data to create a chart from.
     * @param chartType
     *            The type of chart to create for the category dataset.
     * @param legendPosition
     *            The position where the legend should appear.
     * @param chartName
     *            The title of the chart.
     * @param yaxisName
     *            The title of the Y axis.
     * @param xaxisName
     *            The title of the X axis.
     * @return A JFreeChart that represents the dataset given.
     */
    private static JFreeChart createCategoryChartFromDataset( 
            CategoryDataset dataset, ExistingChartTypes chartType, LegendPosition legendPosition, 
            String chartName, String yaxisName, String xaxisName) {
        
        if (chartType == null || dataset == null) {
            return null;
        }
        boolean showLegend = !legendPosition.equals(LegendPosition.NONE);
        
        JFreeChart chart;
        ChartFactory.setChartTheme(StandardChartTheme.createLegacyTheme());
        BarRenderer.setDefaultBarPainter(new StandardBarPainter());
        
        if (chartType == ExistingChartTypes.BAR) {
            chart = ChartFactory.createBarChart(chartName, xaxisName, yaxisName, dataset, PlotOrientation.VERTICAL, showLegend, true, false);
        } else if (chartType == ExistingChartTypes.CATEGORY_LINE) {
            chart = ChartFactory.createLineChart(chartName, xaxisName, yaxisName, dataset, PlotOrientation.VERTICAL, showLegend, true, false);
        } else {
            throw new IllegalArgumentException("Unknown chart type " + chartType + " for a category dataset.");
        }
        if (chart == null) return null;
        
        if (legendPosition != LegendPosition.NONE) {
            chart.getLegend().setPosition(legendPosition.getRectangleEdge());
            chart.getTitle().setPadding(4,4,15,4);
        }
        
        CategoryItemRenderer renderer = chart.getCategoryPlot().getRenderer();
        int seriesSize = chart.getCategoryPlot().getDataset().getRowCount();
        for (int i = 0; i < seriesSize; i++) {
            //XXX:AS LONG AS THERE ARE ONLY 10 SERIES!!!!
            renderer.setSeriesPaint(i, ColourScheme.BREWER_SET19.get(i));
        }
        if (renderer instanceof BarRenderer) {
            BarRenderer barRenderer = (BarRenderer) renderer;
            barRenderer.setShadowVisible(false);
        }
        setTransparentChartBackground(chart);
        return chart;
    }

    /**
     * Creates a chart based on the data in the given query.
     * 
     * @param c
     *            The chart to extract the dataset and JFreeChart settings from.
     * @return A chart based on the data in the query of the given type.
     */
    private static JFreeChart createXYChart(Chart c) {
        if (c.getType().getType() != DatasetTypes.XY) {
            throw new IllegalStateException(
                    "Chart is not currently set up as an XY chart " +
                    "(it is a " + c.getType() + ")");
        }
        
        List<ColumnIdentifier> columnNamesInOrder = c.getColumnNamesInOrder();
        LegendPosition legendPosition = c.getLegendPosition(); 
        String chartName = c.getName();
        String yaxisName = c.getYaxisName();
        String xaxisName = c.getXaxisName();
        
        final XYDataset xyCollection = (XYDataset) c.createDataset();
        
        boolean containsSeries = false;
        for (ColumnIdentifier identifier : columnNamesInOrder) {
            if (identifier.getRoleInChart().equals(ColumnRole.SERIES)) {
                containsSeries = true;
                break;
            }
        }
        if (!containsSeries) {
            return null;
        }
        
        if (xyCollection == null) {
            return null;
        }
        return createChartFromXYDataset(xyCollection, c.getType(), legendPosition, 
                chartName, yaxisName, xaxisName);
    }

    /**
     * This is a helper method for creating a line chart. This should only do
     * the chart creation and not setting up the dataset. The calling method
     * should do the logic for the dataset setup.
     * 
     * @param xyCollection
     *            The dataset to display a chart for.
     * @param chartType
     *            The chart type. This must be a valid chart type that can be
     *            created from an XY dataset. At current only line and scatter
     *            are supported
     * @param legendPosition
     *            The position of the legend.
     * @param chartName
     *            The name of the chart.
     * @param yaxisName
     *            The name of the y axis.
     * @param xaxisName
     *            The name of the x axis.
     * @return A chart of the specified chartType based on the given dataset.
     */
    private static JFreeChart createChartFromXYDataset(XYDataset xyCollection,
            ExistingChartTypes chartType, LegendPosition legendPosition, 
            String chartName, String yaxisName, String xaxisName) {
        boolean showLegend = !legendPosition.equals(LegendPosition.NONE);
        JFreeChart chart;
        ChartFactory.setChartTheme(StandardChartTheme.createLegacyTheme());
        BarRenderer.setDefaultBarPainter(new StandardBarPainter());
        if (chartType.equals(ExistingChartTypes.LINE)) {
            chart = ChartFactory.createXYLineChart(chartName, xaxisName, yaxisName, xyCollection, 
                    PlotOrientation.VERTICAL, showLegend, true, false);
        } else if (chartType.equals(ExistingChartTypes.SCATTER)) {
            chart = ChartFactory.createScatterPlot(chartName, xaxisName, yaxisName, xyCollection, 
                    PlotOrientation.VERTICAL, showLegend, true, false);
        } else {
            throw new IllegalArgumentException("Unknown chart type " + chartType + " for an XY dataset.");
        }
        if (chart == null) return null;
        
        if (legendPosition != LegendPosition.NONE) {
            chart.getLegend().setPosition(legendPosition.getRectangleEdge());
            chart.getTitle().setPadding(4,4,15,4);
        }
        final XYItemRenderer xyirenderer = chart.getXYPlot().getRenderer();
        int xyLineSeriesSize = chart.getXYPlot().getDataset().getSeriesCount();
        for (int i = 0; i < xyLineSeriesSize; i++) {
            //XXX:AS LONG AS THERE ARE ONLY 10 SERIES!!!!
            xyirenderer.setSeriesPaint(i, ColourScheme.BREWER_SET19.get(i));
            if (chartType.equals(ExistingChartTypes.SCATTER)) {
                BasicStroke circle = new BasicStroke();
                xyirenderer.setSeriesShape(i, circle.createStrokedShape(
                        new Ellipse2D.Double(-3.0, -3.0, 6.0, 6.0)));
            }
        }
        setTransparentChartBackground(chart);
        return chart;
    }
    
    private static void setTransparentChartBackground(JFreeChart chart) {
        chart.setBackgroundPaint(new Color(255,255,255,0));
        chart.getPlot().setBackgroundPaint(new Color(255,255,255,0));   
        chart.getPlot().setBackgroundAlpha(0.0f);
    }
    
}