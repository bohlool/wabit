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

package ca.sqlpower.wabit.report.chart;

import java.awt.Color;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.jfree.data.general.Dataset;

import ca.sqlpower.object.CleanupExceptions;
import ca.sqlpower.object.SPObject;
import ca.sqlpower.object.SPVariableHelper;
import ca.sqlpower.sql.CachedRowSet;
import ca.sqlpower.sql.RowFilter;
import ca.sqlpower.swingui.ColourScheme;
import ca.sqlpower.util.WebColour;
import ca.sqlpower.wabit.AbstractWabitObject;
import ca.sqlpower.wabit.WabitObject;
import ca.sqlpower.wabit.rs.ResultSetEvent;
import ca.sqlpower.wabit.rs.ResultSetHandle;
import ca.sqlpower.wabit.rs.ResultSetListener;
import ca.sqlpower.wabit.rs.ResultSetProducerEvent;
import ca.sqlpower.wabit.rs.ResultSetProducerException;
import ca.sqlpower.wabit.rs.ResultSetProducerListener;
import ca.sqlpower.wabit.rs.WabitResultSetProducer;
import ca.sqlpower.wabit.rs.olap.OlapQuery;
import ca.sqlpower.wabit.rs.olap.RepeatedMember;


/**
 * Charts are mutable objects. Be careful with those when you use them in
 * reports. To do so, you would need to create a copy of that chart and 
 * inject a variables context with {@link Chart#Chart(Chart, SPObject)}
 */
public class Chart extends AbstractWabitObject {

    private static final Logger logger = Logger.getLogger(Chart.class);
    
    /**
     * The background colour for this renderer and chart background. 
     */
    private Color backgroundColour;
        
    /**
     * The Y axis label in the chart.
     */
    private String yaxisName;
    
    /**
     * The X axis label in the chart.
     */
    private String xaxisName;
    
    private boolean autoXAxisRange = true;
    private boolean autoYAxisRange = true;
    private double xAxisMaxRange = 0;
    private double yAxisMaxRange = 0;
    private double xAxisMinRange = 0;
    private double yAxisMinRange = 0;
    
    
    
    
    
    /**
     * This is the current style of chart the user has made.
     */
    private ChartType type;
    
    /**
     * The position of the legend in relation to the chart. This
     * is defaulted to below the chart.
     */
    private LegendPosition legendPosition = LegendPosition.BOTTOM;
    
    /**
     * The source of results this chart uses to create its dataset.
     */
    private WabitResultSetProducer query;

    /**
     * Keeps track of all the columns in the result set, along with the role
     * each column plays in this chart (category, series, and so on). This
     * list constitutes the child list of this WabitObject.
     */
    private final List<ChartColumn> chartColumns = 
    		Collections.synchronizedList(new ArrayList<ChartColumn>());
    
    /**
     * This list tracks all of the column identifiers currently in use in the
     * query but cannot be found in the actual query object that backs this
     * chart. The common reason for columns being missing is that the user
     * created a chart, modified the query and removed columns in use in the
     * chart, and then went to modify or use the chart.
     */
    private final List<ChartColumn> missingColumns = new ArrayList<ChartColumn>();

    /**
     * Tells if this chart reflects the current RS structure.
     */
    private boolean needsRefresh = true;

    /**
     * This is a listener placed on the ResultSetProducer to find if columns removed from
     * a query were in use in this chart.
     */
    private final ResultSetProducerListener resultSetProducerListener = new ResultSetProducerListener() {
		public void structureChanged(ResultSetProducerEvent evt) {
			needsRefresh = true;
		}
		public void executionStopped(ResultSetProducerEvent evt) {
			// not interested
		}
		public void executionStarted(ResultSetProducerEvent evt) {
			// not interested
		}
    };

    /**
     * Filter that accepts or rejects rows from the result set that underlies
     * the chart data. This is applied to the result set before creating the
     * dataset. If null, all rows of the result set will be accepted.
     */
    private RowFilter resultSetFilter;

    /**
     * The current result set (not filtered). Gets updated by refreshData(), and
     * can be retrieved by {@link #getUnfilteredResultSet()}.
     */
    private ResultSetHandle resultSetHandle;

    /**
     * Rotation amount, in degrees, of the text along the X axis. 0 means
     * horizontal; negative values mean increasing counterclockwise rotation;
     * positive values mean increasing clockwise rotation. Valid range is from
     * -180.0 to 180.0 inclusive.
     */
    private double xAxisLabelRotation;

    /**
     * Flag to indicate if meaningless but visually fun animations should be
     * applied to this chart when it is displayed.
     */
    private boolean gratuitouslyAnimated;
    
    /**
     * Keeps a ref to the source object from which to resolve
     * variables. If a chart is embeded in a report, it is necessary
     * to create a copy of the chart and then inject a different
     * context source, in that case, the content box.
     */
    private SPObject variablesContextSource = this;

    /**
     * List of currently-registered data listeners. This list contains no nulls.
     */
    private final List<ChartDataListener> dataListeners = new ArrayList<ChartDataListener>();

    /**
     * Listens to the current ResultSetHandle and updates the chart
     * as data comes in.
     */
    private final ResultSetListener resultSetListener = new ResultSetListener() {
		public void newData(ResultSetEvent evt) {
			logger.debug("Obtained new data for chart");
			syncWithRs(evt.getSourceHandle().getResultSet());
			fireDataChangedEvent();
		}
		public void executionComplete(ResultSetEvent evt) {
			logger.debug("Obtained new data for chart");
			syncWithRs(evt.getSourceHandle().getResultSet());
			fireDataChangedEvent();
		}
		public void executionStarted(ResultSetEvent evt) {
			// don't care.
		};
	};

    /**
     * Creates a new chart with a new unique ID.
     */
    public Chart() {
        this((String)null);
    }

    /**
     * Creates a new chart having the given unique ID (primarily meant for
     * reading objects from storage).
     * 
     * @param uuid
     *            The uuid the new object should have.
     */
    public Chart(String uuid) {
        super(uuid);
        setName("New chart");
    }
    
    /**
     * Creates a new chart, based on another one, but sets it's context
     * at the same time. Used to report renderers.
     * @param chartToCopy The original chart to copy.
     * @param variablesContextSource The source of variables to use.
     */
    public Chart(Chart chartToCopy, SPObject variablesContextSource) {
    	this(chartToCopy);
		this.variablesContextSource = variablesContextSource;
    }
    
    /**
     * Copy constructor.
     */
    public Chart(Chart chartToCopy) {
		super(null);
    	setName(chartToCopy.getName());
    	this.backgroundColour = chartToCopy.backgroundColour;
    	this.xaxisName = chartToCopy.xaxisName;
    	this.yaxisName = chartToCopy.yaxisName;
    	this.type = chartToCopy.type;
    	this.legendPosition = chartToCopy.legendPosition;
    	
    	for (ChartColumn column : chartToCopy.chartColumns) {
    		this.chartColumns.add(new ChartColumn(column));
    	}
    	
    	this.resultSetFilter = chartToCopy.resultSetFilter;
    	this.xAxisLabelRotation = chartToCopy.xAxisLabelRotation;
    	this.gratuitouslyAnimated = chartToCopy.gratuitouslyAnimated;
    	this.autoXAxisRange = chartToCopy.autoXAxisRange;
    	this.autoYAxisRange = chartToCopy.autoYAxisRange;
    	this.xAxisMaxRange = chartToCopy.xAxisMaxRange;
    	this.yAxisMaxRange = chartToCopy.yAxisMaxRange;
    	this.xAxisMinRange = chartToCopy.xAxisMinRange;
    	this.yAxisMinRange = chartToCopy.yAxisMinRange;
    	this.setQuery(chartToCopy.query);
	}

    public Color getBackgroundColour() {
        return backgroundColour;
    }
    
    public void setBackgroundColour(Color backgroundColour) {
    	Color oldColour = this.backgroundColour;
    	this.backgroundColour = backgroundColour;
    	firePropertyChange("backgroundColour", oldColour, backgroundColour);
    }

    /**
     * Returns the current result set of the query that supplies data to this
     * chart.
     * <p>
     * No matter how the result set was obtained, it will be filtered through
     * the current {@link #resultSetFilter} before being returned.
     * 
     * @return The current result set that should be charted, filtered through
     *         {@link #resultSetFilter}.
     *         
     * @see #getUnfilteredResultSet()
     */
    private ResultSet getResultSet(boolean async) throws SQLException {
        
        ResultSet rs = getUnfilteredResultSet(async);
        
        if (rs == null) {
        	return null;
        }
        
        if (resultSetFilter == null) {
            return rs;
        } else {
            CachedRowSet filteredRs = new CachedRowSet();
            filteredRs.populate(rs, resultSetFilter);
            return filteredRs;
        }
    }

    /**
     * Returns the current result set of the query that supplies data to this
     * chart. 
     * <p>
     * The result set returned will contain all the rows supplied by the current
     * query. Specifically, it will not be subject to the
     * {@link #resultSetFilter}. This is useful in user interfaces, which can
     * show all the rows and visually indicate which ones are being used in the
     * chart and which are not.
     * 
     * @return The unfiltered version of the current result set.
     * 
     * @see #getResultSet()
     */
    public ResultSet getUnfilteredResultSet() {
    	return this.getUnfilteredResultSet(true);
    }
    public ResultSet getUnfilteredResultSet(boolean async) {
    	if (needsRefresh || resultSetHandle == null) {
    		refresh(async);
    	}
    	if (resultSetHandle == null) {
    		return null;
    	} else {
    		return resultSetHandle.getResultSet();
    	}
    }
    
    private void syncWithRs(ResultSet rs) {
    	try {
        	synchronized (chartColumns) {
        		
        		// synchronize chart columns with new result set
        		List<ChartColumn> oldCols = new ArrayList<ChartColumn>(chartColumns);
        		List<ChartColumn> newCols = new ArrayList<ChartColumn>();
        		if (rs != null) {
        			ResultSetMetaData rsmd = rs.getMetaData();
        			// The meta data object might be null because of streaming queries.
        			if (rsmd != null) {
        				for (int i = 1; i <= rsmd.getColumnCount(); i++) {
        					
        					// Get the column alias first, as this would "uniquely" 
        					// identify the columns within the result set.
        					// However, if no alias is used, try to get it from the column name.
        					// If both are null, we're screwed in any case, and it'll re-create the chart column each time.
        					String columnName = rsmd.getColumnLabel(i);
        					if (columnName == null) {
        						columnName = rsmd.getColumnName(i);
        					}
        					
        					ChartColumn existing = findByName(oldCols, columnName);
        					if (existing != null) {
        						newCols.add(existing);
        					} else {
        						// try to find it back in those we temporarily removed.
        						ChartColumn backup = findByName(missingColumns, columnName);
        						if (backup != null) {
        							newCols.add(backup);
        							missingColumns.remove(backup);
        						} else {
        							int columnType = rsmd.getColumnType(i);
        							newCols.add(new ChartColumn(columnName, columnType));
        						}
        					}
        				}
        			}
        		}
        		// this part fires childRemoved and childAdded events
        		for (int i =  chartColumns.size() - 1; i >= 0; i--) {
        			ChartColumn col = chartColumns.get(i);
        			if (!newCols.contains(col)) {
        				removeColumnIdentifier(col);
        				missingColumns.add(col);
        			}
        		}
        		for (ChartColumn col : newCols) {
        			if (!chartColumns.contains(col)) {
        				addChartColumn(col);
        			}
        		}
			}
        } catch (SQLException e) {
        	throw new RuntimeException(e);
        }
	}

	public static ChartColumn findByName(List<ChartColumn> cols, String name) {
        for (ChartColumn col : cols) {
            if (col.getName() != null && col.getName().equalsIgnoreCase(name)) {
                return col;
            }
        }
        return null;
    }

	/**
	 * Finds the index of a {@link ChartColumn} from the {@link List} of
	 * children based on the given name to search for.
	 * 
	 * @param name
	 *            The name of the {@link ChartColumn} to look for.
	 * @return The index of the found {@link ChartColumn}, or -1 if it is not
	 *         found.
	 */
	public int findColumnIndexByName(String name) {
		for (int i = 0; i < chartColumns.size(); i++) {
			if (chartColumns.get(i).getName() != null && 
					chartColumns.get(i).getName().equalsIgnoreCase(name)) {
				return i;
			}
		}
		return -1;
	}

    /**
     * For internal use only (while refreshing the column identifier list).
     * Removes the given column identifier from the column names list. If there
     * is no such column, no action is taken. Fires a childRemoved event if a
     * column was actually removed.
     */
    private void removeColumnIdentifier(ChartColumn col) {
        int index = chartColumns.indexOf(col);
        boolean removed = chartColumns.remove(col);
        if (removed) {
            fireChildRemoved(ChartColumn.class, col, index);
        }
    }
    
    /**
     * Creates an independent JFreeChart dataset based on the current data available
     * in this chart's underlying query. The type of dataset returned depends on the
     * current chart type setting.
     * 
     * @return A JFreeChart dataset; either XYDataSet or CategoryDataSet.
     * @see #setQuery()
     * @see #setType(ChartType)
     */
    public Dataset createDataset() {
    	return this.createDataset(true);
    }
    public Dataset createDataset(boolean async) {
        try {
            ResultSet rs = getResultSet(async);
            if (rs == null) {
                logger.debug("Returning null data set because getResultSet() returned null");
                return null;
            }
            
            // Need to update chart columns from the new ResultSet before using them.
            syncWithRs(rs);

            switch (type.getDatasetType()) {
            case CATEGORY:
                return DatasetUtil.createCategoryDataset(
                        chartColumns, rs,
                        findRoleColumns(ColumnRole.CATEGORY));
            case XY:
                return DatasetUtil.createSeriesCollection(
                        chartColumns, rs);
            default :
                throw new IllegalStateException("Unknown chart type " + type);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // --------------- WabitObject implementation ------------------

    @Override
    public void setParent(SPObject parent) {
    	super.setParent(parent);
    }

    public boolean allowsChildren() {
        return true;
    }

    public int childPositionOffset(Class<? extends SPObject> childType) {
        return 0;
    }

    /**
     * Alias for {@link #getColumns()}. Provided to satisfy {@link WabitObject} interface.
     */
    public List<? extends WabitObject> getChildren() {
        // TODO when we reinstate missing identifiers, make them children too
        return getColumns();
    }

    public List<WabitObject> getDependencies() {
        if (query == null) return Collections.emptyList();
        return Collections.singletonList((WabitObject) query);
    }
    
    public void removeDependency(SPObject dependency) {
        if (dependency.equals(query)) {
            setQuery(null);
        }
        for (WabitObject child : getChildren()) {
            child.removeDependency(dependency);
        }
    }

    // ============= END of WabitObject implementation
    
    /**
     * Returns the currently-selected chart type.
     */
    public ChartType getType() {
        return type;
    }

    /**
     * Selects a new chart type for this chart.
     */
    public void setType(ChartType newType) {
        ChartType oldType = this.type;
        this.type = newType;
        firePropertyChange("type", oldType, newType);
    }
    
    public LegendPosition getLegendPosition() {
        return legendPosition;
    }
    
    public void setLegendPosition(LegendPosition selectedLegendPosition) {
        LegendPosition oldValue = this.legendPosition;
        this.legendPosition = selectedLegendPosition;
        firePropertyChange("legendPosition", oldValue, selectedLegendPosition);
    }

    public void setYaxisName(String yaxisName) {
        String oldValue = this.yaxisName;
        this.yaxisName = yaxisName;
        firePropertyChange("yaxisName", oldValue, yaxisName);
    }

    public String getYaxisName() {
        return yaxisName;
    }

    public void setXaxisName(String xaxisName) {
        String oldValue = this.xaxisName;
        this.xaxisName = xaxisName;
        firePropertyChange("xaxisName", oldValue, xaxisName);
    }

    public String getXaxisName() {
        return xaxisName;
    }

    public List<String> getSeriesColours() {
        List<String> colourList = new ArrayList<String>();
        for(WebColour wb : ColourScheme.BREWER_SET19) {
            colourList.add(wb.toString());
        }
        return colourList;
    }

    /**
     * Replaces this chart's source of result sets with the given result set producer,
     * firing a property change event. The current result set filter will also be set
     * to an OlapRowFilter or null (depending on newQuery's type) as a side effect of
     * calling this method.
     * <p>
     * You'll probably want to call {@link #refreshData()} after defining a new query.
     * 
     * TODO nothing in here throws SQLException, remove it.
     * 
     * @param newQuery
     * @throws IllegalArgumentException if the query is not of a supported type.
     */
    public void setQuery(@Nullable WabitResultSetProducer newQuery) {
        
    	logger.debug("Setting chart's query to " + newQuery);
    	
        // remove old listeners
        if (query != null) {
            query.removeResultSetProducerListener(resultSetProducerListener);
        }

        WabitResultSetProducer oldQuery = query;
        query = newQuery;

        if (newQuery instanceof OlapQuery) {
            setResultSetFilter(new OlapRowFilter());
        } else {
            setResultSetFilter(null);
        }
        
        // attach new listeners
        if (query != null) {
            query.addResultSetProducerListener(resultSetProducerListener);
        }
        
        firePropertyChange("query", oldQuery, newQuery);        	
    }

    private void setResultSetFilter(RowFilter resultSetFilter) {
        this.resultSetFilter = resultSetFilter;
    }
    
    public RowFilter getResultSetFilter() {
        return resultSetFilter;
    }

    /**
     * Returns the current query that this chart gets its datasets from. Can be
     * null.
     */
    public WabitResultSetProducer getQuery() {
        return query;
    }

    /**
     * Returns an unmodifiable view of the result set columns this chart knows
     * about, along with information about their role in the chart.
     * 
     * @return the current column list. It is not modifiable by you, but
     *         it may appear to change.
     */
    public List<ChartColumn> getColumns() {
        return Collections.unmodifiableList(chartColumns);
    }

    /**
     * For internal use only (while reading a Workspace file or refreshing the
     * column list when a new result set comes in). Adds the given column
     * identifier to the end of the column names list. Fires a childAdded event
     * once the new column identifier has been added.
     * 
     * @param newColumnIdentifier
     *            The new column identifier to add. Must not be null.
     */
    public void addChartColumn(@Nonnull ChartColumn newColumnIdentifier) {
        addChartColumn(newColumnIdentifier, chartColumns.size());
    }

    /**
     * For internal use only (while reading a Workspace file or refreshing the
     * column list when a new result set comes in). Adds the given column
     * identifier to the end of the column names list. Fires a childAdded event
     * once the new column identifier has been added.
     * 
     * @param newColumnIdentifier
     *            The new column identifier to add. Must not be null.
     * @param index
     *            The index to add the chart column at. Cannot be greater than
     *            the current number of identifiers in the chart.
     */
    private void addChartColumn(@Nonnull ChartColumn newColumnIdentifier, int index) {
        if (newColumnIdentifier == null) {
            throw new NullPointerException("Null column identifier");
        }
        chartColumns.add(newColumnIdentifier);
        newColumnIdentifier.setParent(this);
        fireChildAdded(ChartColumn.class, newColumnIdentifier, index);
    }

    /**
     * Returns a list of the identifiers for all columns labeled as a given
     * role in a bar chart. If there are no such columns, an empty list
     * will be returned. If multiple columns are selected, the values in each
     * column will be appended to each other to create the value's name. The
     * returned column identifiers will be ordered the same as in the
     * columnNamesInOrder list, which gives users the ability to define the
     * column name order.
     */
    public List<ChartColumn> findRoleColumns(ColumnRole role) {
        List<ChartColumn> categoryColumnNames = new ArrayList<ChartColumn>();
        for (ChartColumn identifier : chartColumns) {
            if (identifier.getRoleInChart().equals(role)) {
                categoryColumnNames.add(identifier);
            }
        }
        return categoryColumnNames;
    }

    /**
     * A result set filter that hides the appropriate rows from being charted in
     * an MDX-derived result set.
     */
    private final class OlapRowFilter implements RowFilter {

        public boolean acceptsRow(Object[] row) throws SQLException {
            
            // it would be nice to cache this, but we'd need a notification mechanism
            // for flushing the cache every time the dependant data changes
            List<ChartColumn> categoryColumns = findRoleColumns(ColumnRole.CATEGORY);
            if (categoryColumns.isEmpty()) {
                return true;
            }
            
            int nullCategories = 0;
            int repeatedMembers = 0;
            
            for (ChartColumn catCol : categoryColumns) {
                int idx = resultSetHandle.getResultSet().findColumn(catCol.getName());
                Object val = row[idx - 1];
                
                if (val == null) {
                    nullCategories++;
                } else if (val instanceof RepeatedMember) {
                    repeatedMembers++;
                }
            }
            
            return nullCategories + repeatedMembers < categoryColumns.size();
        }
        
    }

    /**
     * Returns the desired rotation for the X-axis category/item labels. 0 means
     * horizontal; -90 means read bottom to top; 90 means read top to bottom.
     */
    public double getXAxisLabelRotation() {
        return xAxisLabelRotation;
    }

    /**
     * Sets the desired rotation for the X-axis category/item labels. 0 means
     * horizontal; -90 means read bottom to top; 90 means read top to bottom.
     * <p>
     * Fires a property change if the new value differs from the existing value.
     * 
     * @param xAxisLabelRotation
     *            The desired rotation. Must be between -90 and 90 inclusive.
     */
    public void setXAxisLabelRotation(double xAxisLabelRotation) {
        if (xAxisLabelRotation < -90.0 || xAxisLabelRotation > 90.0) {
            throw new IllegalArgumentException(
                    "Rotation " + xAxisLabelRotation +
                    " is outside the range [-90.0 .. 90.0]");
        }
        double oldValue = this.xAxisLabelRotation;
        this.xAxisLabelRotation = xAxisLabelRotation;
        firePropertyChange("XAxisLabelRotation", oldValue, xAxisLabelRotation);
    }

    public void setGratuitouslyAnimated(boolean gratuitouslyAnimated) {
        boolean oldValue = this.gratuitouslyAnimated;
        this.gratuitouslyAnimated = gratuitouslyAnimated;
        firePropertyChange("gratuitouslyAnimated", oldValue, gratuitouslyAnimated);
    }

    public boolean isGratuitouslyAnimated() {
        return gratuitouslyAnimated;
    }

    @Override
    /*
     * removing a child to a chart should only be done in special cases such as
     * through an undo manager or synchronizing with a server.
     */
    protected boolean removeChildImpl(SPObject child) {
        if (getColumns().contains(child)) {
            removeColumnIdentifier((ChartColumn) child);
            return true;
        }
        return false;
    }
    
    @Override
    /*
     * Adding a child to a chart should only be done in special cases such as
     * through an undo manager or synchronizing with a server.
     */
    protected void addChildImpl(SPObject child, int index) {
        addChartColumn((ChartColumn) child, index);
    }
    
    /**
     * Registers the given listener to receive an event every time the dataset
     * returned by {@link #createDataset()} might be different. These events
     * typically happen whenever the chart's result set provider changes, as
     * well as when a streaming query delivers a new row of data.
     * 
     * @param l the listener to add. Must not be null.
     */
    public void addChartDataListener(@Nonnull ChartDataListener l) {
        if (l == null) {
            throw new NullPointerException("Null listener");
        }
        dataListeners.add(l);
    }

    /**
     * Removes the given listener from the list of parties interested in data
     * change events. If the given listener was registered multiple times, this
     * call only removes one of the registrations. If the given listener is not
     * currently registered, this method has no effect.
     * 
     * @param l
     *            the listener to remove. Null is silently ignored.
     */
    public void removeChartDataListener(@Nullable ChartDataListener l) {
        dataListeners.remove(l);
    }
    
    /**
     * Delivers a data change notification to all registered listeners.
     */
    private void fireDataChangedEvent() {
        final ChartDataChangedEvent evt = new ChartDataChangedEvent(this);
		synchronized (dataListeners) {
			for (int i = dataListeners.size() - 1; i >= 0; i--) {
				dataListeners.get(i).chartDataChanged(evt);
			}
		}
    }
    
    @Override
    public CleanupExceptions cleanup() {
        
        if (query != null) {
    		query.removeResultSetProducerListener(resultSetProducerListener);
    	}
        
        if (resultSetHandle != null) {
    		resultSetHandle.removeResultSetListener(resultSetListener);
    		resultSetHandle.cancel();
    	}
        
        return new CleanupExceptions();
    }
    
    
    public List<Class<? extends SPObject>> getAllowedChildTypes() {
    	List<Class<? extends SPObject>> types = new ArrayList<Class<? extends SPObject>>();
    	types.add(ChartColumn.class);
    	return types;
    }
    
    public void refresh(boolean async) {

    	logger.debug("Refreshing chart");
    	
    	if (resultSetHandle != null) {
            resultSetHandle.removeResultSetListener(resultSetListener);
            resultSetHandle.cancel();
        }
    	
    	try {
        	if (query != null) {
        		if (Chart.this.variablesContextSource == null) {
        			throw new AssertionError("Program error. Chart objects need a variables context defined.");
        		}
        		this.resultSetHandle = 
        				query.execute(
            					new SPVariableHelper(Chart.this.variablesContextSource), 
            					Chart.this.resultSetListener,
            					async);
        		
        		// Need to disable the refresh flag before getting result set
        		// or else infinite recursion could occur.
        		needsRefresh = false;
        		ResultSet rs = getResultSet(async);
        		if (rs != null) {
        			syncWithRs(rs);
        		}
        	}
        } catch (ResultSetProducerException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
        	throw new RuntimeException(e);
		}
    }
    
    /**
     * Tells if this chart is a streaming chart or not.
     */
    public boolean isStreaming() {
    	if (this.query != null &&
    			this.query.isStreaming()) {
    		return true;
    	} else {
    		return false;
    	}
    }
    
    /**
     * Tells this chart that it should refresh the underlying data
     * next time it tries to render itself or it generates a dataset.
     */
    public void refreshLater() {
    	this.needsRefresh = true;
    }
    
    public void refresh() {
    	refresh(true);
    }
    
    /**
     * Resets the missing identifiers list. This list contains all of the
     * column identifiers that are defined in the chart but do not exist in the
     * query that is being used to get values for this chart. The reason why
     * some of the columns are missing is usually due to the query being modified.
     */
    public void clearMissingIdentifiers() {
        missingColumns.clear();
    }

    /**
     * Returns an unmodifiable view of the missing identifiers list. The missing
     * identifiers are the parts of the chart's underlying data set which the
     * chart refers to but can no longer find (probably because the data set's
     * structure has changed since the chart was originally configured).
     * 
     * @return the current missing identifier list. It is not modifiable by you,
     *         but it may appear to change.
     */
    public List<ChartColumn> getMissingIdentifiers() {
        return Collections.unmodifiableList(missingColumns);
    }

    /**
     * Adds the given column identifier to the end of the missing identifiers list.
     * This method is only of practical use for code that's restoring the state
     * of a chart that's being read from a file.
     * 
     * @param ci The column identifier to add. Must not be null.
     */
    public void addMissingIdentifier(@Nonnull ChartColumn ci) {
        if (ci == null) {
            throw new NullPointerException("null column identifier");
        }
        missingColumns.add(ci);
        ci.setParent(this);
        fireChildAdded(ChartColumn.class, ci, missingColumns.indexOf(ci));
    }

	public boolean isAutoXAxisRange() {
		return autoXAxisRange;
	}

	public void setAutoXAxisRange(boolean autoXAxisRange) {
		boolean oldValue = this.autoXAxisRange;
		this.autoXAxisRange = autoXAxisRange;
		firePropertyChange("autoXAxisRange", oldValue, this.autoXAxisRange);
	}

	public boolean isAutoYAxisRange() {
		return autoYAxisRange;
	}

	public void setAutoYAxisRange(boolean autoYAxisRange) {
		boolean oldValue = this.autoYAxisRange;
		this.autoYAxisRange = autoYAxisRange;
		firePropertyChange("autoYAxisRange", oldValue, this.autoYAxisRange);
	}

	public double getXAxisMaxRange() {
		return xAxisMaxRange;
	}

	public void setXAxisMaxRange(double xAxisMaxRange) {
		double oldValue = this.xAxisMaxRange;
		this.xAxisMaxRange = xAxisMaxRange;
		firePropertyChange("XAxisMaxRange", oldValue, this.xAxisMaxRange);
	}

	public double getYAxisMaxRange() {
		return yAxisMaxRange;
	}

	public void setYAxisMaxRange(double yAxisMaxRange) {
		double oldValue = this.yAxisMaxRange;
		this.yAxisMaxRange = yAxisMaxRange;
		firePropertyChange("YAxisMaxRange", oldValue, this.yAxisMaxRange);
	}

	public double getXAxisMinRange() {
		return xAxisMinRange;
	}

	public void setXAxisMinRange(double xAxisMinRange) {
		double oldValue = this.xAxisMinRange;
		this.xAxisMinRange = xAxisMinRange;
		firePropertyChange("XAxisMinRange", oldValue, this.xAxisMinRange);
	}

	public double getYAxisMinRange() {
		return yAxisMinRange;
	}

	public void setYAxisMinRange(double yAxisMinRange) {
		double oldValue = this.yAxisMinRange;
		this.yAxisMinRange = yAxisMinRange;
		firePropertyChange("YAxisMinRange", oldValue, this.yAxisMinRange);
	}   
}
