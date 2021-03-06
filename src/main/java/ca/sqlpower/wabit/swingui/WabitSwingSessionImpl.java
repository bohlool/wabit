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
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.tree.TreePath;

import net.jcip.annotations.GuardedBy;

import org.apache.http.client.ClientProtocolException;
import org.apache.log4j.Logger;

import ca.sqlpower.dao.session.SPFontLoader;
import ca.sqlpower.enterprise.client.SPServerInfo;
import ca.sqlpower.object.AbstractPoolingSPListener;
import ca.sqlpower.object.SPChildEvent;
import ca.sqlpower.object.SPListener;
import ca.sqlpower.object.SPObject;
import ca.sqlpower.sql.DataSourceCollection;
import ca.sqlpower.sql.JDBCDataSource;
import ca.sqlpower.sql.Olap4jDataSource;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.sqlobject.SQLObject;
import ca.sqlpower.swingui.MultiDragTreeUI;
import ca.sqlpower.swingui.SPSwingWorker;
import ca.sqlpower.swingui.SwingUIUserPrompterFactory;
import ca.sqlpower.swingui.db.DatabaseConnectionManager;
import ca.sqlpower.swingui.db.DefaultDataSourceDialogFactory;
import ca.sqlpower.swingui.db.DefaultDataSourceTypeDialogFactory;
import ca.sqlpower.swingui.event.SessionLifecycleEvent;
import ca.sqlpower.swingui.event.SessionLifecycleListener;
import ca.sqlpower.util.SQLPowerUtils;
import ca.sqlpower.util.UserPrompter;
import ca.sqlpower.util.UserPrompterFactory;
import ca.sqlpower.util.UserPrompter.UserPromptOptions;
import ca.sqlpower.util.UserPrompter.UserPromptResponse;
import ca.sqlpower.util.UserPrompterFactory.UserPromptType;
import ca.sqlpower.wabit.WabitObject;
import ca.sqlpower.wabit.WabitSession;
import ca.sqlpower.wabit.WabitWorkspace;
import ca.sqlpower.wabit.dao.OpenWorkspaceXMLDAO;
import ca.sqlpower.wabit.enterprise.client.WabitClientSession;
import ca.sqlpower.wabit.rs.ResultSetProducer;
import ca.sqlpower.wabit.rs.ResultSetProducerEvent;
import ca.sqlpower.wabit.rs.ResultSetProducerListener;
import ca.sqlpower.wabit.swingui.tree.FolderNode;
import ca.sqlpower.wabit.swingui.tree.SmartTreeTransferable;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeCellEditor;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeCellRenderer;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeModel;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeModel.Olap4jTreeObject;
import edu.umd.cs.findbugs.annotations.NonNull;


/**
 * The Main Window for the Wabit Application.
 */
public class WabitSwingSessionImpl implements WabitSwingSession {
    
    private static final Icon DB_ICON = new ImageIcon(WabitSwingSessionImpl.class.getClassLoader().getResource("icons/connection-db-16.png"));
	
	private static final Logger logger = Logger.getLogger(WabitSwingSessionImpl.class);
	
	private final WabitSwingSessionContext sessionContext;
	
	private final JTree workspaceTree;
	
	private TreeTabDropTargetListener treeTabDropTargetListener = new TreeTabDropTargetListener();
	
	/**
	 * This is the droplistener on the tabbed pane which controls importing
	 * and exporting between workspaces 
	 */
	public class TreeTabDropTargetListener implements DropTargetListener {
		public void dragEnter(DropTargetDragEvent dtde) {
			//don't care
		}

		public void dragExit(DropTargetEvent dte) {
			//don't care
		}

		public void dragOver(DropTargetDragEvent dtde) {
			if (canImport(dtde.getCurrentDataFlavors(), dtde.getTransferable())) {
				dtde.acceptDrag(dtde.getDropAction());
			} else {
				dtde.rejectDrag();
			}
		}

        public boolean canImport(DataFlavor[] transferFlavors, Transferable transferable) {
        	//This variable is required because we need to know if we have something 
        	//from the left tree to prevent accidental imports and weird behavior
        	boolean isFromLeftTree = false;
        	//XXX this is weird code, wanted to do an instanceof check to see if the
        	//XXX transferable is a SmartTreeTransferable but that didn't work
        	boolean containsFileList = false;
        	for (DataFlavor dataFlavor : transferFlavors) {
        		if (dataFlavor.equals(SmartTreeTransferable.WABIT_OBJECT_FLAVOUR_TO_EXPORT)) {
        			isFromLeftTree = true;
        		}
                if (dataFlavor.equals(DataFlavor.javaFileListFlavor) ) {
                	containsFileList = true;
                }
            }
            return (containsFileList && !isFromLeftTree);
        }

        @SuppressWarnings("unchecked")
        public void drop(DropTargetDropEvent dtde) {
        	Transferable transferable = dtde.getTransferable();
        	DataFlavor[] dataFlavors = transferable.getTransferDataFlavors();
			if (!canImport(dataFlavors, transferable)) {
				dtde.rejectDrop();
				return;
			}
        	dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
        	DataFlavor dataFlavor = DataFlavor.javaFileListFlavor;
        	List<File> transferData;
        	try {
        		transferData = (List<File>) transferable.getTransferData(dataFlavor);
        	} catch (Exception e1) {
        		throw new RuntimeException(e1);
        	}
        	for (File file : transferData) {
        		FileInputStream input;
        		try {
        			input = new FileInputStream(file);
        		} catch (FileNotFoundException e) {
        			throw new RuntimeException(e);
        		}
        		OpenWorkspaceXMLDAO open = new OpenWorkspaceXMLDAO(getContext(), input, file.length());
        		open.importWorkspaces(WabitSwingSessionImpl.this);

        	}
        }

        public void dropActionChanged(DropTargetDragEvent dtde) {
        	//don't care
		}
	}
	
    /**
     * The cell renderer for this session's workspace tree.
     */
    private final WorkspaceTreeCellRenderer renderer = new WorkspaceTreeCellRenderer();

	private final List<SessionLifecycleListener<WabitSession>> lifecycleListeners =
		new ArrayList<SessionLifecycleListener<WabitSession>>();

	/**
	 * This DB connection manager will allow editing the db connections in the
	 * pl.ini file. This DB connection manager can be used anywhere needed in 
	 * wabit. 
	 */
	private final DatabaseConnectionManager dbConnectionManager;
	
	/**
	 * A {@link UserPrompterFactory} that will create a dialog for users to choose an existing
	 * DB or create a new one if they load a workspace with a DB not in their pl.ini.
	 */
	private final UserPrompterFactory upfMissingLoadedDB;
	
	/**
	 * All of the session specific operations should be delegated to this session.
	 * This class is mainly used to tie Swing objects to a core session.
	 */
	private final WabitSession delegateSession;
	
	/**
	 * Gets set to true whenever a change (child added/removed or property
	 * changed) is observed in any of the objects under (and including) the
	 * workspace.
	 */
	private boolean unsavedChangesExist = false;

    /**
     * This timer updates the tree cell renderer's busy badge frame on a
     * periodic basis. It's created in the session's constructor and shut down
     * when the session closes.
     */
	private final Timer busyBadgeTimer;

    /**
     * Event handler that watches every WabitObject in this session's workspace
     * for property changes, and handles them appropriately (usually by setting
     * the session's dirty flag).
     * 
     * <p>
     * This object is also resposible for listening added/removed instances
     * of {@link ResultSetProducer} and bind a listener to it so we can update
     * the throbber icon in the tree.
     */
	private class WorkspaceWatcher extends AbstractPoolingSPListener {

	    private final Set<String> ignorablePropertyNames = new HashSet<String>();
	    {
	        ignorablePropertyNames.add("running");
	        ignorablePropertyNames.add("editorPanelModel");
            ignorablePropertyNames.add("zoomLevel");
            ignorablePropertyNames.add("unfilteredResultSet");
	    }
	    
        public WorkspaceWatcher(WabitWorkspace workspace) {
        	SQLPowerUtils.listenToHierarchy(workspace, this);
        }
	    
        @Override
        public void childAddedImpl(SPChildEvent e) {
        	SQLPowerUtils.listenToHierarchy(e.getChild(), this);
            unsavedChangesExist = true;
            
            // Add a RS producer listener if necessary so we can
            // show a throbber in the tree or not
            if (e.getChild() instanceof ResultSetProducer) {
            	((ResultSetProducer)e.getChild())
            			.addResultSetProducerListener(rsProducerListener);
            }
        }

        @Override
        public void childRemovedImpl(SPChildEvent e) {
        	SQLPowerUtils.unlistenToHierarchy(e.getChild(), this);
            unsavedChangesExist = true;
            
            // Add the RS producer listener
            if (e.getChild() instanceof ResultSetProducer) {
            	((ResultSetProducer)e.getChild())
            			.removeResultSetProducerListener(rsProducerListener);
            }
        }

        @Override
        public void propertyChangeImpl(PropertyChangeEvent evt) {
            if (!ignorablePropertyNames.contains(evt.getPropertyName())) {
                SQLPowerUtils.logPropertyChange(
                        logger, "Marking workspace dirty because of property change", evt);
                unsavedChangesExist = true;
            }
        }
	    
	}
	
	@GuardedBy("itself")
    private final List<ActiveRsProducer> activeRsProducers =
        Collections.synchronizedList(new ArrayList<ActiveRsProducer>());
	
	private class ActiveRsProducer {
		private final ResultSetProducer rsProducer;
        
        ActiveRsProducer(@NonNull ResultSetProducer rsProducer) {
			if (rsProducer == null) {
                throw new NullPointerException("Null worker!");
            }
			if (!(rsProducer instanceof SPObject)) {
				throw new AssertionError("Program error. Was expecting an instance of SPObject.");
			}
			this.rsProducer = rsProducer;
            
        }

        /**
         * Repaints the responsible object's node in the workspace tree. This
         * method has no effect if there is no responsible object defined for
         * this worker, or the tree node for the responsible object is not
         * currently visible.
         */
        public void repaintInTree() {
        	TreePath pathToResponsibleObject =
                	workspaceTreeModel.createTreePathForObject((SPObject)this.rsProducer);
            if (pathToResponsibleObject != null) {
                Rectangle pathBounds = workspaceTree.getPathBounds(pathToResponsibleObject);
                if (pathBounds != null) {
                    workspaceTree.repaint(pathBounds);
                }
            }
        }
        
        
    }
	
	private ResultSetProducerListener rsProducerListener = new ResultSetProducerListener() {
		
		public void structureChanged(ResultSetProducerEvent evt) {
			// Not interested in this
		}
		public void executionStopped(ResultSetProducerEvent evt) {
			for (ActiveRsProducer activeRs : activeRsProducers) {
				if (activeRs == evt.getSource()) {
					WabitSwingSessionImpl.this.activeRsProducers.remove(activeRs);
					// Trigger the repaint a last time so it shows it as stopped.
					activeRs.repaintInTree();
					break;
				}
			}
		}
		public void executionStarted(ResultSetProducerEvent evt) {
			WabitSwingSessionImpl.this.activeRsProducers.add(new ActiveRsProducer(evt.getSource()));
		}
	};

	/**
	 * The model behind the workspace tree on the left side of Wabit.
	 */
	private final WorkspaceTreeModel workspaceTreeModel;
	
    /**
     * This is the most recent URI this session was saved to or the URI
     * this session was loaded from if it has not been saved. This will
     * be null for new sessions that have not been saved.
     */
    private URI currentURI = null;
    
    /**
     * Thread-safe list of all currently-registered background tasks.
     */
    @GuardedBy("itself")
    private final List<ActiveWorker> activeWorkers =
        Collections.synchronizedList(new ArrayList<ActiveWorker>());

    /**
     * A collection of information about and operations on a currently-executing
     * background task that exists within this session.
     * 
     * @see {@link WabitSwingSessionImpl#registerSwingWorker(SPSwingWorker)}
     */
    private class ActiveWorker {
        private final SPSwingWorker worker;
        private final TreePath pathToResponsibleObject;
        
        ActiveWorker(@NonNull SPSwingWorker worker) {
            if (worker == null) {
                throw new NullPointerException("Null worker!");
            }
            this.worker = worker;
            if (worker.getResponsibleObject() instanceof WabitObject) {
                WabitObject responsibleObject = (WabitObject) worker.getResponsibleObject();
                pathToResponsibleObject =
                    workspaceTreeModel.createTreePathForObject(responsibleObject);
                
            } else {
                pathToResponsibleObject = null;
            }
        }

        /**
         * Repaints the responsible object's node in the workspace tree. This
         * method has no effect if there is no responsible object defined for
         * this worker, or the tree node for the responsible object is not
         * currently visible.
         */
        public void repaintInTree() {
            if (pathToResponsibleObject != null) {
                Rectangle pathBounds = workspaceTree.getPathBounds(pathToResponsibleObject);
                if (pathBounds != null) {
                    workspaceTree.repaint(pathBounds);
                }
            }
        }
        
        /**
         * Returns the worker.
         * 
         * @return The worker (never null)
         */
        @NonNull
        public SPSwingWorker getWorker() {
            return worker;
        }
        
    }
    /**
     * This listener is attached to the active session's workspace and will
     * update the current editor displayed when the object being edited changes
     * in the workspace.
     */
    private final SPListener workspaceEditorModelListener = new AbstractPoolingSPListener() {
        public void propertyChangeImpl(PropertyChangeEvent evt) {
            if (sessionContext.isLoading()) return;
            if (evt.getPropertyName().equals("editorPanelModel")) {
                sessionContext.setActiveSession(WabitSwingSessionImpl.this);
                if (!sessionContext.setEditorPanel()) {
                    getWorkspace().setEditorPanelModel((WabitObject) evt.getOldValue());
                    return;
                }
                if (evt.getNewValue() != null) {
                    final TreePath createTreePathForObject = 
                        getWorkspaceTreeModel().createTreePathForObject((SPObject) evt.getNewValue());
                    logger.debug("Tree path being set to " + createTreePathForObject 
                            + " as editor panel being set to " + ((SPObject) evt.getNewValue()).getName());
                    getTree().setSelectionPath(createTreePathForObject);
                }
            }
        }
    };
    
    /**
     * This listener will listen for the context to finish loading and update
     * the selected element in the tree to be the same as the current editor.
     * This is done to select the correct object in the tree as it cannot be
     * done during loading. Doing this on sessions that are already loaded when
     * other sessions are loading should not hurt anything.
     */
    private final PropertyChangeListener loadingContextListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals("loading") && ((Integer) evt.getNewValue()) <= 0
                    && getWorkspace().getEditorPanelModel() != null) {
                final TreePath createTreePathForObject = 
                    getWorkspaceTreeModel().createTreePathForObject(getWorkspace().getEditorPanelModel());
                getTree().setSelectionPath(createTreePathForObject);
            }
        };
    };

	

    /**
     * Creates a new session that belongs to the given context and delegates
     * some of its work to the given delegate session.
     * <p>
     * To create an instance of this class, use
     * {@link WabitSwingSessionContextImpl#createSession()}.
     * 
     * @param context The context this session belongs to.
     * @param delegateSession The session to delegate some WabitSession operations to.
     */
	protected WabitSwingSessionImpl(WabitSwingSessionContext context, WabitSession delegateSession) {
	    this.delegateSession = delegateSession;
	    
		sessionContext = context;
		
		// XXX leaking a reference to partially-constructed session!
		delegateSession.getWorkspace().setSession(this);
		
		new WorkspaceWatcher(delegateSession.getWorkspace());
		
		workspaceTreeModel = new WorkspaceTreeModel(delegateSession.getWorkspace());
		workspaceTree = new JTree(workspaceTreeModel);
		workspaceTree.setRootVisible(false);
		workspaceTree.setToggleClickCount(0);
		Object root = workspaceTreeModel.getRoot();
		workspaceTree.expandPath(new TreePath(root));
		for (int i = 0; i < workspaceTreeModel.getChildCount(root); i++) {
			Object treeFolder = workspaceTreeModel.getChild(root, i);
			workspaceTree.expandPath(new TreePath(new Object[] { root, treeFolder} ));
		}
		workspaceTree.setDropTarget(new DropTarget(workspaceTree, treeTabDropTargetListener));
		dbConnectionManager = createDbConnectionManager();
        
        upfMissingLoadedDB = new SwingUIUserPrompterFactory(sessionContext.getFrame());

        workspaceTree.setUI(new MultiDragTreeUI());
        workspaceTree.setBackground(Color.WHITE); //this tree looks terrible in linux, just trying to make it slightly nicer
//		workspaceTree.updateUI(); //this seems to make the tree look nice on linux, don't know why but not for lack of trying
        //the updateUI() line doesn't seem to keep multidrag working on this tree however it does work on the cube chooser tree for some reason
		workspaceTree.setShowsRootHandles(true);
		DragSource ds = new DragSource();
        ds.createDefaultDragGestureRecognizer(workspaceTree, DnDConstants.ACTION_COPY, new DragGestureListener(){

			public void dragGestureRecognized(DragGestureEvent dge) {
	            dge.getSourceAsDragGestureRecognizer().setSourceActions(DnDConstants.ACTION_COPY);
	            JTree t = (JTree) dge.getComponent();
	            
	            //These are the three possible object types in the tree
	            List<WabitObject> wabitObjectsToExport = new ArrayList<WabitObject>();
	            List<SQLObject> sqlObjectsToDrag = new ArrayList<SQLObject>(); 
	            List<Olap4jTreeObject> olapObjectsToDrag = new ArrayList<Olap4jTreeObject>();
	            
	            for (TreePath path : t.getSelectionPaths()) {
	            	Object lastPathComponent = path.getLastPathComponent();
					if (lastPathComponent instanceof FolderNode) continue;
					if (lastPathComponent instanceof WabitObject) {
						wabitObjectsToExport.add((WabitObject) lastPathComponent);
					} else if (lastPathComponent instanceof SQLObject) {
						sqlObjectsToDrag.add((SQLObject) lastPathComponent);
					} else if (lastPathComponent instanceof Olap4jTreeObject) {
						olapObjectsToDrag.add((Olap4jTreeObject) lastPathComponent);
					}
	            	
	            }
	            
				if (wabitObjectsToExport.size() == 0 && sqlObjectsToDrag.size() == 0 &&
						olapObjectsToDrag.size() == 0) return;
				
				List<Object> objectsToTransfer = new ArrayList<Object>(); 
				
				if (wabitObjectsToExport.size() != 0) {
					objectsToTransfer.addAll(wabitObjectsToExport);
				} 
				
				if (sqlObjectsToDrag.size() != 0) {
					objectsToTransfer.addAll(sqlObjectsToDrag);
				}
				
				if (olapObjectsToDrag.size() != 0) {
					for (Olap4jTreeObject treeObject : olapObjectsToDrag) {
						objectsToTransfer.add(treeObject);
					}
				}
				
				dge.getDragSource().startDrag(dge, null, 
						new SmartTreeTransferable(objectsToTransfer, getContext()), 
						new DragSourceAdapter() {//just need a default adapter
				}
				);
			}
        	
        });

        workspaceTree.setCellRenderer(renderer);
        workspaceTree.setCellEditor(new WorkspaceTreeCellEditor(workspaceTree, renderer));
        workspaceTree.addMouseListener(new WorkspaceTreeListener(this));
        workspaceTree.setEditable(true);
        
        //Sets the editor panel if it is not set when creating the UI 
        //components. This may need to move elsewhere.
        if (getWorkspace().getEditorPanelModel() == null) {
        	getWorkspace().setEditorPanelModel(getWorkspace());
        }
        getWorkspace().addSPListener(workspaceEditorModelListener);
        getContext().addPropertyChangeListener(loadingContextListener);
        
        busyBadgeTimer = new Timer(100, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
            	SwingUtilities.invokeLater(new Runnable() {
					public void run() {						
						renderer.nextBusyBadgeFrame();
						synchronized (activeWorkers) {
							for (ActiveWorker worker : activeWorkers) {
								worker.repaintInTree();
							}
						}
						synchronized (activeRsProducers) {
							for (ActiveRsProducer activeRs : activeRsProducers) {
								activeRs.repaintInTree();
							}
						}
					}
				});
            }
        });
        busyBadgeTimer.start();
	}

	/**
	 * Constructor subroutine that builds the DB connection manager for this session.
	 */
    private DatabaseConnectionManager createDbConnectionManager() {
        List<Class<? extends SPDataSource>> newDSTypes = new ArrayList<Class<? extends SPDataSource>>();
        newDSTypes.add(JDBCDataSource.class);
        newDSTypes.add(Olap4jDataSource.class);
        DatabaseConnectionManager dbcm = new DatabaseConnectionManager(getDataSources(), 
                new DefaultDataSourceDialogFactory(), 
                new DefaultDataSourceTypeDialogFactory(getDataSources()),
                new ArrayList<Action>(), new ArrayList<JComponent>(), sessionContext.getFrame(), true, newDSTypes);
        dbcm.setDbIcon(DB_ICON);
        return dbcm;
    }
	
    public DataSourceCollection<SPDataSource> getDataSources() {
        return delegateSession.getDataSources();
    }

    public JTree getTree() {
    	return workspaceTree;
    }

	public void addSessionLifecycleListener(SessionLifecycleListener<WabitSession> l) {
		lifecycleListeners.add(l);
	}

	public void removeSessionLifecycleListener(SessionLifecycleListener<WabitSession> l) {
		lifecycleListeners.remove(l);
	}
	
	public boolean close() {
	    if (!delegateSession.close()) {
	        return false;
	    }
	    getWorkspace().removeSPListener(workspaceEditorModelListener);
	    getContext().removePropertyChangeListener(loadingContextListener);
	    for (ActiveWorker worker : activeWorkers) {
	        worker.getWorker().kill();
	    }
	    for (ActiveRsProducer activeRs : this.activeRsProducers) {
	    	activeRs.rsProducer.cancel();
	    }
	    busyBadgeTimer.stop();

	    SessionLifecycleEvent<WabitSession> lifecycleEvent =
	        new SessionLifecycleEvent<WabitSession>(this);
	    for (int i = lifecycleListeners.size() - 1; i >= 0; i--) {
	        lifecycleListeners.get(i).sessionClosing(lifecycleEvent);
	    }

	    return true;
	}

    public WabitWorkspace getWorkspace() {
        return delegateSession.getWorkspace();
    }
    
	public WabitSwingSessionContext getContext() {
		return sessionContext;
	}
	
    /**
     * Returns a {@link DatabaseConnectionManager} that allows modifying
     * the data source collection of the given session.
     */
    public DatabaseConnectionManager getDbConnectionManager() {
        return dbConnectionManager;
    }
	
	public UserPrompter createUserPrompter(String question, UserPromptType responseType, UserPromptOptions optionType, UserPromptResponse defaultResponseType, Object defaultResponse, String ...buttonNames) {
		return upfMissingLoadedDB.createUserPrompter(question, responseType, optionType, defaultResponseType, defaultResponse, buttonNames);
	}

	public void addPropertyChangeListener(PropertyChangeListener l) {
        delegateSession.addPropertyChangeListener(l);
	}

	public void removePropertyChangeListener(PropertyChangeListener l) {
	    delegateSession.removePropertyChangeListener(l);
	}
	
    public WorkspaceTreeModel getWorkspaceTreeModel() {
        return workspaceTreeModel;
    }


	public UserPrompter createDatabaseUserPrompter(String question,
			List<Class<? extends SPDataSource>> dsTypes,
			UserPromptOptions optionType,
			UserPromptResponse defaultResponseType, Object defaultResponse,
			DataSourceCollection<SPDataSource> dsCollection,
			String... buttonNames) {
		return upfMissingLoadedDB.createDatabaseUserPrompter(question, dsTypes, optionType, defaultResponseType,
				defaultResponse, dsCollection, buttonNames);
	}

	public URI getCurrentURI() {
	    return currentURI;
	}
	
	/**
	 * Updates the current URI this session's workspace was last loaded from or saved to.
	 * Also clears the unsaved changes state.
	 */
	public void setCurrentURI(URI uri) {
	    currentURI = uri;
	    unsavedChangesExist = false;
	}
	
	public File getCurrentURIAsFile() {
	    if (getCurrentURI() != null && "file".equals(getCurrentURI().getScheme())) {
	        return new File(getCurrentURI());
	    } else {
	        return null;
	    }
	}

	public boolean hasUnsavedChanges() {
		if (delegateSession!=null &&
				delegateSession.isEnterpriseServerSession()) {
			return false;
		}
        return unsavedChangesExist;
    }

    /* docs inherited from interface */
    public void registerSwingWorker(@NonNull SPSwingWorker worker) {
        activeWorkers.add(new ActiveWorker(worker));
    }

    /* docs inherited from interface */
    public void removeSwingWorker(@NonNull final SPSwingWorker worker) {
        synchronized (activeWorkers) {
            for (Iterator<ActiveWorker> it = activeWorkers.iterator(); it.hasNext(); ) {
                ActiveWorker aw = it.next();
            	if (aw.getWorker().equals(worker)) {
                	it.remove();
            	}
            }
        }
    }

    public void runInBackground(final Runnable runner) {
        SPSwingWorker worker = new SPSwingWorker(this) {
            
            @Override
            public void doStuff() throws Exception {
                runner.run();
            }

            @Override
            public void cleanup() throws Exception {
                //do nothing
            }
            
        };
        new Thread(worker).start();
    }

    public void runInForeground(Runnable runner) {
        if (SwingUtilities.isEventDispatchThread()) {
            runner.run();
        } else {
            SwingUtilities.invokeLater(runner);
        }
    }
    
    public boolean isForegroundThread() {
    	return SwingUtilities.isEventDispatchThread();
    }

	public boolean isEnterpriseServerSession() {
		return this.delegateSession.isEnterpriseServerSession();
	}
	
	public SPServerInfo getEnterpriseServerInfos() {
		if (this.isEnterpriseServerSession()) {
			return ((WabitClientSession)this.delegateSession).getWorkspaceLocation().getServiceInfo();
		}
		return null;
	}

	public WabitWorkspace getSystemWorkspace() {
		return this.delegateSession.getSystemWorkspace();
	}
    
    // XXX EVIL BADNESS
    public void refresh() {
        if (delegateSession instanceof WabitClientSession) {
            WabitClientSession wss = (WabitClientSession) delegateSession;
            if (close()) {
                WabitClientSession.openServerSession(
                        getContext(),
                        wss.getWorkspaceLocation());
            }
        } else {
            JOptionPane.showMessageDialog(getContext().getFrame(),
                    "Refresh is currently only supported for server sessions");
        }
    }
    
    // XXX MORE EVIL BADNESS
    public void delete() throws ClientProtocolException, URISyntaxException, IOException {
    	if (delegateSession instanceof WabitClientSession) {
            WabitClientSession wss = (WabitClientSession) delegateSession;
            wss.deleteServerWorkspace();
            close();
        } else {
            JOptionPane.showMessageDialog(getContext().getFrame(),
                    "Delete is currently only supported for server sessions");
        }
    }

	public SPFontLoader getFontLoader() {
		return delegateSession.getFontLoader();
	}
}
