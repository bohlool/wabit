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

package ca.sqlpower.wabit.swingui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.Window;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;

import org.apache.log4j.Logger;

import ca.sqlpower.sql.JDBCDataSource;
import ca.sqlpower.sql.Olap4jDataSource;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.swingui.db.DatabaseConnectionManager;
import ca.sqlpower.swingui.db.DefaultDataSourceDialogFactory;
import ca.sqlpower.swingui.db.DefaultDataSourceTypeDialogFactory;
import ca.sqlpower.wabit.WabitVersion;
import ca.sqlpower.wabit.rs.olap.OlapQuery;
import ca.sqlpower.wabit.rs.query.QueryCache;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;

/**
 * This panel will display information about the workspace. It will
 * also allow the user to add and remove data sources.
 */
public class WorkspacePanel implements WabitPanel {
	
	private static Logger logger = Logger.getLogger(WorkspacePanel.class);
	
	private static final ImageIcon SELECT_START_ICON = new ImageIcon(WorkspacePanel.class.getClassLoader().getResource("icons/wunWabit_selected.png"));
	private static final ImageIcon OVER_START_ICON = new ImageIcon(WorkspacePanel.class.getClassLoader().getResource("icons/wunWabit_over.png"));
	private static final ImageIcon DOWN_START_ICON = new ImageIcon(WorkspacePanel.class.getClassLoader().getResource("icons/wunWabit_down.png"));
	private static final ImageIcon UP_START_ICON = new ImageIcon(WorkspacePanel.class.getClassLoader().getResource("icons/wunWabit_up.png"));
	private static final Icon DB_ICON = new ImageIcon(WorkspacePanel.class.getClassLoader().getResource("icons/connection-db-16.png"));
	
	/**
	 * This layout generates the Wabit logo panel with the version number in it.
	 */
    public static class LogoLayout implements LayoutManager {

        private static final ImageIcon WABIT_HEADER = 
            new ImageIcon(WorkspacePanel.class.getClassLoader().getResource(
                    "icons/wabit_header_welcome.png"));
        
    	private final JLabel wabitLabel = new JLabel(WABIT_HEADER);
    	private final JLabel versionLabel = new JLabel("" + WabitVersion.VERSION);
    	
        private int textStartY = 130;
        private int textStartX = 400;
        
        public static JPanel generateLogoPanel() {
        	final LogoLayout logo = new LogoLayout();
            JPanel panel = new JPanel(logo);
        	
        	logo.getVersionLabel().setForeground(new Color(0x999999));
        	
        	panel.add(logo.getWabitLabel());
        	panel.add(logo.getVersionLabel());
        	panel.setOpaque(false);
        	
			return panel;
        }
        
        public void layoutContainer(Container parent) {
        	JLabel wabitLabel = (JLabel) parent.getComponent(0);
        	JLabel versionLabel = (JLabel) parent.getComponent(1);
        	
            wabitLabel.setBounds(0, 10, wabitLabel.getPreferredSize().width, wabitLabel.getPreferredSize().height);
            versionLabel.setBounds(wabitLabel.getX() + textStartX, wabitLabel.getY() + textStartY, versionLabel.getPreferredSize().width, versionLabel.getPreferredSize().height);
        }

        public Dimension minimumLayoutSize(Container parent) {
        	JLabel welcomeLabel = (JLabel) parent.getComponent(0);
        	JLabel wabitLabel = (JLabel) parent.getComponent(1);
        	
            return new Dimension(welcomeLabel.getWidth() + wabitLabel.getWidth(),
            		Math.max(welcomeLabel.getHeight(), wabitLabel.getHeight()));
        }

        public Dimension preferredLayoutSize(Container parent) {
            return minimumLayoutSize(parent);
        }

        public void removeLayoutComponent(Component comp) {
            // no-op
        }
        
        public void addLayoutComponent(String name, Component comp) {
            // no-op
        }

        public JLabel getWabitLabel() {
            return wabitLabel;
        }

        public JLabel getVersionLabel() {
            return versionLabel;
        }
    }

	/**
	 * The main panel of this workspace.
	 */
	private final JScrollPane scrollPane;
	private final WabitSwingSession session;
	private final WabitSwingSessionContext context;
	
	public WorkspacePanel(WabitSwingSession session) {
		logger.debug("Creating new workspace panel for " + session);
		this.session = session;
		context = (WabitSwingSessionContext) session.getContext();
		scrollPane = new JScrollPane(buildUI());
	}
	
	private JPanel buildUI() {
		JPanel panel = new FurryPanel(new MigLayout("", "[center, grow]"));
		final DatabaseConnectionManager dbConnectionManager = createDBConnectionManager(session, context.getFrame());
		
		JPanel logoPanel = LogoLayout.generateLogoPanel();
		panel.add(logoPanel, "wrap");
		panel.add(dbConnectionManager.getPanel(), "");
		
		logoPanel.getLayout().layoutContainer(logoPanel);
		
		return panel;
	}
	
	/**
	 * This is the panel which paints the hair on the back of the wabit welcome screen
	 * and the hair on the back of the WorkspacePanel.
	 */
	public static class FurryPanel extends JPanel {
		private final ImageIcon furryImage = new ImageIcon(FurryPanel.class.getClassLoader().getResource("icons/wabit_header_app_bkgd.png"));
		
		public FurryPanel(LayoutManager layout) {
			super(layout);
		}
		
		@Override
		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.drawImage(furryImage.getImage(), 0, 0, this.getWidth(), furryImage.getIconHeight(), furryImage.getImageObserver());
		}
	}

	/**
	 * This will create the DBConnectionManager for the WorkspacePanel and other panels that need
	 * to allow users to add a data source to the workspace. One other place this gets used is
	 * the NewWorkspaceScreen where the user adds the first data source to a workspace.
	 * @param session
	 * @return
	 */
	public static DatabaseConnectionManager createDBConnectionManager(final WabitSwingSession session, Window owner) {
	    final WabitSwingSessionContext context = (WabitSwingSessionContext) session.getContext();
		List<JComponent> componentList = new ArrayList<JComponent>();
        DefaultFormBuilder startPanel = new DefaultFormBuilder(new FormLayout("fill:pref", "pref, pref"));
        final JLabel startImageLabel = new JLabel(UP_START_ICON);
        startImageLabel.setFocusable(true);
        startPanel.add(startImageLabel);
        JLabel startTextLabel = new JLabel("Start");
        startTextLabel.setHorizontalAlignment(SwingConstants.CENTER);
        startPanel.nextLine();
		startPanel.add(startTextLabel);
        componentList.add(startPanel.getPanel());
        List<Class<? extends SPDataSource>> newDSTypes = new ArrayList<Class<? extends SPDataSource>>();
        newDSTypes.add(JDBCDataSource.class);
        newDSTypes.add(Olap4jDataSource.class);
		final DatabaseConnectionManager dbConnectionManager = new DatabaseConnectionManager(session.getDataSources(), 
				new DefaultDataSourceDialogFactory(), 
				new DefaultDataSourceTypeDialogFactory(session.getDataSources()),
				new ArrayList<Action>(), componentList, owner, false, newDSTypes);
		dbConnectionManager.setDbIcon(DB_ICON);
		
        startImageLabel.addMouseListener(new MouseListener() {
        	boolean inside = false;
        	boolean pressed = false;
			public void mouseReleased(MouseEvent e) {
				pressed = false;
				SPDataSource ds = dbConnectionManager.getSelectedConnection();
				if (startImageLabel.isFocusOwner()) {
					startImageLabel.setIcon(SELECT_START_ICON);
                                        if (ds == null) {
                                            JOptionPane.showMessageDialog(
                                                    context.getFrame(),
                                                    "Please select a data source before pressing 'Start'",
                                                    "Please select a data source",
                                                    JOptionPane.WARNING_MESSAGE);
                                        } else {
                                            addDataSource(session, ds);
                                        }
				} else if (inside) {
					startImageLabel.setIcon(OVER_START_ICON);
                                        if (ds == null) {
                                            JOptionPane.showMessageDialog(
                                                    context.getFrame(),
                                                    "Please select a data source before pressing 'Start'",
                                                    "Please select a data source",
                                                    JOptionPane.WARNING_MESSAGE);
                                        } else {
                                            addDataSource(session, ds);
                                        }
				} else {
					startImageLabel.setIcon(UP_START_ICON);
				}
			}

            private void addDataSource(final WabitSwingSession session,
                    SPDataSource ds) {
                if (ds instanceof JDBCDataSource) {
                    addJDBCDataSource((JDBCDataSource) ds, session);
                } else if (ds instanceof Olap4jDataSource) {
                    addOlap4jDataSource((Olap4jDataSource) ds, session);
                } else {
                    throw new IllegalArgumentException("Unknown data source of type " + ds.getClass()+ " is being added to the workspace.");
                }
            }
		
			public void mousePressed(MouseEvent e) {
				startImageLabel.requestFocusInWindow();
				pressed = true;
				startImageLabel.setIcon(DOWN_START_ICON);
			}
		
			public void mouseExited(MouseEvent e) {
				inside = false;
				if (startImageLabel.isFocusOwner()) {
					startImageLabel.setIcon(SELECT_START_ICON);
				} else if (pressed) {
					startImageLabel.setIcon(DOWN_START_ICON);
				} else {
					startImageLabel.setIcon(UP_START_ICON);
				}
			}
		
			public void mouseEntered(MouseEvent e) {
				inside = true;
				if (startImageLabel.isFocusOwner()) {
					startImageLabel.setIcon(SELECT_START_ICON);
				} else if (pressed) {
					startImageLabel.setIcon(DOWN_START_ICON);
				} else {
					startImageLabel.setIcon(OVER_START_ICON);
				}
			}
		
			public void mouseClicked(MouseEvent e) {
				// do nothing
			}
		});
        startImageLabel.addFocusListener(new FocusListener() {
		
			public void focusLost(FocusEvent e) {
				startImageLabel.setIcon(UP_START_ICON);
			}
		
			public void focusGained(FocusEvent e) {
				//do nothing.
			}
		});
		return dbConnectionManager;
	}
	
	/**
	 * This method is used in the DB connection manager to add the selected db
	 * to the workspace.
	 */
	public static void addJDBCDataSource(JDBCDataSource ds, WabitSwingSession session) {
	    final WabitSwingSessionContext context = (WabitSwingSessionContext) session.getContext();
		if (ds == null) {
			return;
		}
		Connection con = null;
		try {
			con = ds.createConnection();
		} catch (SQLException e) {
			SPSUtils.showExceptionDialogNoReport(context.getFrame(), "Could not create a connection to " + ds.getName() + ". Please check the connection information.", e);
			return;
		} finally {
			if (con != null) {
				try {
					con.close();
				} catch (Exception e) {
					//squish exception to show any other exception while testing the connection.
				}
			}
		}
		if (!session.getWorkspace().dsAlreadyAdded(ds)) {
			session.getWorkspace().addDataSource(ds);
		}
		QueryCache query = new QueryCache(session.getContext());
		query.setName("New " + ds.getName() + " query");
		query.setDataSource(ds);
		session.getWorkspace().addQuery(query, session);
	}

    /**
     * This method is used in the DB connection manager to add the selected olap
     * connection to the workspace.
     */
	public static void addOlap4jDataSource(Olap4jDataSource ds, WabitSwingSession session) {
	    final WabitSwingSessionContext context = (WabitSwingSessionContext) session.getContext();
	    if (ds == null) {
            return;
        }
        
        // This part tests the underlying JDBC connection for the
        // mondrian in-process driver. It does not apply to XMLA
        if (ds.getType().equals(Olap4jDataSource.Type.IN_PROCESS)) {
            Connection con = null;
            try {
                con = ds.getDataSource().createConnection();
            } catch (SQLException e) {
                SPSUtils.showExceptionDialogNoReport(context.getFrame(), "Could not create a connection to " + ds.getName() + ". Please check the connection information.", e);
                return;
            } finally {
                if (con != null) {
                    try {
                        con.close();
                    } catch (Exception e) {
                        //squish exception to show any other exception while testing the connection.
                    }
                }
            }
        }
        
        if (!session.getWorkspace().dsAlreadyAdded(ds)) {
            session.getWorkspace().addDataSource(ds);
        }
        
        OlapQuery newQuery = new OlapQuery(session.getContext());
        newQuery.setOlapDataSource(ds);
        newQuery.setName("New " + ds.getName() + " query");
        session.getWorkspace().addOlapQuery(newQuery);
        //TODO Add a query builder for OLAP connections and make it the focused component.
    }
	
	public boolean applyChanges() {
		return true;
	}

	public void discardChanges() {
		//no changes to discard
	}

	public JComponent getPanel() {
		return scrollPane;
	}

	public boolean hasUnsavedChanges() {
		return false;
	}

	public String getTitle() {
		return "Workspace - " + session.getWorkspace().getName();
	}
	
	public JComponent getSourceComponent() {
	    return null;
	}

    public JToolBar getToolbar() {
        return null;
    }
}
