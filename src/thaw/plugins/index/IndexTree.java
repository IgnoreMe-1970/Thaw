package thaw.plugins.index;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import thaw.core.I18n;
import thaw.core.IconBox;
import thaw.core.Logger;
import thaw.core.MainWindow;
import thaw.fcp.FCPQueueManager;
import thaw.gui.JDragTree;
import thaw.plugins.Hsqldb;

/**
 * Manages the index tree and its menu (right-click).
 */
public class IndexTree extends java.util.Observable implements MouseListener, ActionListener, java.util.Observer, javax.swing.event.TreeSelectionListener {

	public final static Color SELECTION_COLOR = new Color(190, 190, 190);
	public final static Color LOADING_COLOR = new Color(230, 230, 230);
	public final static Color LOADING_SELECTION_COLOR = new Color(150, 150, 150);

	private JPanel panel;

	private JTree tree;
	private IndexCategory root;

	private JPopupMenu indexCategoryMenu;
	private Vector indexCategoryActions; /* IndexManagementHelper.MenuAction */
	// downloadIndexes
	// createIndex
	// addIndex
	// addCategory
	// renameCategory
	// deleteCategory
	// copyKeys


	private JPopupMenu indexAndFileMenu; /* hem ... and links ... */
	private Vector indexAndFileActions; /* hem ... and links ... */ /* IndexManagementHelper.MenuAction */
	private JMenu indexMenu;
	// download
	// insert
	// renameIndex
	// delete
	// change keys
	// copy public key
	// copy private key

	private JMenu fileMenu;
	// addFileAndInsert
	// addFileWithoutInserting
	// addAKey

	private JMenu linkMenu;
	// addALink

	private boolean selectionOnly;

	private IndexTreeNode selectedNode = null;

	private DefaultTreeModel treeModel;

	private FCPQueueManager queueManager;
	private IndexBrowserPanel indexBrowser;


	/**
	 * @param queueManager Not used if selectionOnly is set to true
	 */
	public IndexTree(final String rootName, boolean selectionOnly,
			 final FCPQueueManager queueManager,
			 final IndexBrowserPanel indexBrowser) {
		this.queueManager = queueManager;

		this.selectionOnly = selectionOnly;

		panel = new JPanel();
		panel.setLayout(new BorderLayout(10, 10));


		root = new IndexCategory(indexBrowser.getDb(), queueManager, indexBrowser.getUnknownIndexList(), -1, null, rootName);
		root.loadChildren();

		root.addObserver(this);

		treeModel = new DefaultTreeModel(root);

		if (!selectionOnly) {
			tree = new JDragTree(treeModel);
			tree.addMouseListener(this);
		} else {
			tree = new JTree(treeModel);
			//tree.addMouseListener(this);
		}

		final IndexTreeRenderer treeRenderer = new IndexTreeRenderer();
		treeRenderer.setLeafIcon(IconBox.minIndex);

		tree.setCellRenderer(treeRenderer);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.setExpandsSelectedPaths(true);

		// Menus :

		JMenuItem item;


		indexCategoryMenu = new JPopupMenu(I18n.getMessage("thaw.plugin.index.category"));
		indexCategoryActions = new Vector();

		indexAndFileMenu = new JPopupMenu();
		indexAndFileActions = new Vector();
		indexMenu = new JMenu(I18n.getMessage("thaw.plugin.index.index"));
		fileMenu = new JMenu(I18n.getMessage("thaw.common.files"));
		linkMenu = new JMenu(I18n.getMessage("thaw.plugin.index.links"));


		// Category menu

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.downloadIndexes"));
		indexCategoryMenu.add(item);
		indexCategoryActions.add(new IndexManagementHelper.IndexDownloader(item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.addAlreadyExistingIndex"));
		indexCategoryMenu.add(item);
		indexCategoryActions.add(new IndexManagementHelper.IndexReuser(indexBrowser.getDb(), queueManager, indexBrowser.getUnknownIndexList(), this, indexBrowser.getMainWindow(), item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.addCategory"));
		indexCategoryMenu.add(item);
		indexCategoryActions.add(new IndexManagementHelper.IndexCategoryAdder(indexBrowser.getDb(), queueManager, indexBrowser.getUnknownIndexList(), this, item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.createIndex"));
		indexCategoryMenu.add(item);
		indexCategoryActions.add(new IndexManagementHelper.IndexCreator(indexBrowser.getDb(), queueManager, indexBrowser.getUnknownIndexList(), this, item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.rename"));
		indexCategoryMenu.add(item);
		indexCategoryActions.add(new IndexManagementHelper.IndexRenamer(this, item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.delete"));
		indexCategoryMenu.add(item);
		indexCategoryActions.add(new IndexManagementHelper.IndexDeleter(this, item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.copyKeys"));
		indexCategoryMenu.add(item);
		indexCategoryActions.add(new IndexManagementHelper.PublicKeyCopier(item));


		// Index menu
		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.downloadIndex"));
		indexMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.IndexDownloader(item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.insertIndex"));
		indexMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.IndexUploader(item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.rename"));
		indexMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.IndexRenamer(this, item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.exportIndex"));
		indexMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.IndexExporter(item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.importIndex"));
		indexMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.IndexImporter(item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.delete"));
		indexMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.IndexDeleter(this, item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.changeIndexKeys"));
		indexMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.IndexKeyModifier(indexBrowser.getMainWindow(), item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.copyPrivateKey"));
		indexMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.PrivateKeyCopier(item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.copyKey"));
		indexMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.PublicKeyCopier(item));


		// File menu

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.addFilesWithInserting"));
		fileMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.FileInserterAndAdder(indexBrowser.getDb(), queueManager, item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.addFilesWithoutInserting"));
		fileMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.FileAdder(indexBrowser.getDb(), queueManager, item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.addKeys"));
		fileMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.KeyAdder(indexBrowser.getDb(), indexBrowser.getMainWindow(), item));

		// Link menu
		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.addLink"));
		linkMenu.add(item);
		indexAndFileActions.add(new IndexManagementHelper.LinkAdder(indexBrowser, item));

		indexAndFileMenu.add(indexMenu);
		indexAndFileMenu.add(fileMenu);
		indexAndFileMenu.add(linkMenu);

		updateMenuState(null);

		addTreeSelectionListener(this);

		panel.add(new JScrollPane(tree), BorderLayout.CENTER);
	}



	public javax.swing.JComponent getPanel() {
		return panel;
	}

	public void addTreeSelectionListener(final javax.swing.event.TreeSelectionListener tsl) {
		tree.addTreeSelectionListener(tsl);
	}

	public void valueChanged(final javax.swing.event.TreeSelectionEvent e) {
		final TreePath path = e.getPath();

		if(path == null)
			return;

		selectedNode = (IndexTreeNode)((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();

		setChanged();
		notifyObservers(selectedNode);
	}


	public void updateMenuState(final IndexTreeNode node) {
		IndexManagementHelper.IndexAction action;

		for(final Iterator it = indexCategoryActions.iterator();
		    it.hasNext();) {
			action = (IndexManagementHelper.IndexAction)it.next();
			action.setTarget(node);
		}

		for(final Iterator it = indexAndFileActions.iterator();
		    it.hasNext();) {
			action = (IndexManagementHelper.IndexAction)it.next();
			action.setTarget(node);
		}
	}


	public JTree getTree() {
		return tree;
	}

	public IndexCategory getRoot() {
		return root;
	}

	public void mouseClicked(final MouseEvent e) {
		notifySelection(e);
	}

	public void mouseEntered(final MouseEvent e) { }
	public void mouseExited(final MouseEvent e) { }

	public void mousePressed(final MouseEvent e) {
		if (!selectionOnly)
			showPopupMenu(e);
	}

	public void mouseReleased(final MouseEvent e) {
		if (!selectionOnly)
			showPopupMenu(e);
	}

	protected void showPopupMenu(final MouseEvent e) {
		if(e.isPopupTrigger()) {
			if(selectedNode == null)
				return;

			if(selectedNode instanceof IndexCategory) {
				updateMenuState(selectedNode);
				indexCategoryMenu.show(e.getComponent(), e.getX(), e.getY());
			}

			if(selectedNode instanceof Index) {
				updateMenuState(selectedNode);
				indexAndFileMenu.show(e.getComponent(), e.getX(), e.getY());
			}
		}
	}

	public void notifySelection(final MouseEvent e) {
		final TreePath path = tree.getPathForLocation(e.getX(), e.getY());

		if(path == null)
			return;

		selectedNode = (IndexTreeNode)((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();

		if ((indexBrowser != null) && (selectedNode instanceof Index)) {
			indexBrowser.getUnknownIndexList().addLinks(((Index)selectedNode));
		}

		setChanged();
		notifyObservers(selectedNode);
	}

	public IndexTreeNode getSelectedNode() {
		final Object obj = tree.getLastSelectedPathComponent();

		if (obj == null)
			return null;

		if (obj instanceof IndexTreeNode)
			return (IndexTreeNode)obj;

		if (obj instanceof DefaultMutableTreeNode)
			return ((IndexTreeNode)(((DefaultMutableTreeNode)obj).getUserObject()));

		Logger.notice(this, "getSelectedNode(): Unknow kind of node ?!");

		return null;
	}


	public void actionPerformed(final ActionEvent e) {
		if(selectedNode == null)
			selectedNode = root;
	}

	public void save() {
		root.save();
	}


	public void update(final java.util.Observable o, final Object param) {
		if( (o instanceof Index)
		    && (param == null) ) {
			final Index index = (Index)o;

			if (treeModel != null) {
				treeModel.nodeChanged(index.getTreeNode());
				if(index.getTreeNode().getParent() != null)
					treeModel.nodeChanged(index.getTreeNode().getParent());
			}

		}
	}


	public class IndexTreeRenderer extends DefaultTreeCellRenderer {

		private static final long serialVersionUID = 1L;

		public IndexTreeRenderer() {
			super();
		}

		public java.awt.Component getTreeCellRendererComponent(final JTree tree,
								       final Object value,
								       final boolean selected,
								       final boolean expanded,
								       final boolean leaf,
								       final int row,
								       final boolean hasFocus) {
			setBackgroundNonSelectionColor(Color.WHITE);
			setBackgroundSelectionColor(IndexTree.SELECTION_COLOR);
			setFont(new Font("Dialog", Font.PLAIN, 12));

			if(value instanceof DefaultMutableTreeNode) {
				final Object o = ((DefaultMutableTreeNode)value).getUserObject();

				if(o instanceof Index) {
					final Index index = (Index)o;

					if (index.isUpdating()) {
						setBackgroundNonSelectionColor(IndexTree.LOADING_COLOR);
						setBackgroundSelectionColor(IndexTree.LOADING_SELECTION_COLOR);
					}
				}

				if (((IndexTreeNode)o).hasChanged()) {
					setFont(new Font("Dialog", Font.BOLD, 12));
				}
			}

			return super.getTreeCellRendererComponent(tree,
								  value,
								  selected,
								  expanded,
								  leaf,
								  row,
								  hasFocus);

		}
	}


	public boolean addToRoot(final IndexTreeNode node) {
		return addToIndexCategory(root, node);
	}

	public boolean addToIndexCategory(final IndexCategory target, final IndexTreeNode node) {
		if ((node instanceof Index) && alreadyExistingIndex(node.getPublicKey())) {
			Logger.notice(this, "Index already added");
			return false;
		}

		node.setParent(target);
		target.getTreeNode().insert(node.getTreeNode(), target.getTreeNode().getChildCount());
		treeModel.reload(target);

		return true;
	}


	public boolean alreadyExistingIndex(final String key) {
		int maxLength = 0;

		if ((key == null) || (key.length() <= 10))
			return false;

		if (key.length() <= 60)
			maxLength = key.length();
		else
			maxLength = 60;

		final String realKey = key.substring(0, maxLength).toLowerCase();

		try {
			final Connection c = indexBrowser.getDb().getConnection();
			PreparedStatement st;

			String query;

			query = "SELECT id FROM indexes WHERE LOWER(publicKey) LIKE ?";


			Logger.info(this, query + " : " + realKey+"%");

			st = c.prepareStatement(query);

			st.setString(1, realKey+"%");

			if (st.execute()) {
				final ResultSet results = st.getResultSet();

				if (results.next())
					return true;
			}


		} catch(final java.sql.SQLException e) {
			Logger.warning(this, "Exception while trying to check if '"+key+"' is already know: '"+e.toString()+"'");
		}

		return false;
	}



	/**
	 * @param node can be null
	 */
	public void reloadModel(final DefaultMutableTreeNode node) {
		treeModel.reload(node);
	}

	public void reloadModel() {
		treeModel.reload();
	}

}
