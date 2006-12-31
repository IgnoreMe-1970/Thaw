package thaw.plugins.index;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Iterator;
import java.util.Observable;
import java.util.Vector;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JButton;
import javax.swing.event.TableModelEvent;
import javax.swing.tree.TreePath;

import thaw.core.I18n;
import thaw.core.Logger;
import thaw.core.IconBox;
import thaw.plugins.ToolbarModifier;
import thaw.fcp.FCPQueueManager;
import thaw.plugins.Hsqldb;


public class LinkTable implements MouseListener, KeyListener, ActionListener {

	private JPanel panel;
	private JTable table;

	private LinkListModel linkListModel = null;
	private LinkList      linkList = null;

	private FCPQueueManager queueManager;

	private IndexBrowserPanel indexBrowser;

	private JPopupMenu rightClickMenu;
	private Vector rightClickActions;
	private JMenuItem gotoItem;

	private ToolbarModifier toolbarModifier;
	private Vector toolbarActions;

	private int[] selectedRows;

	public LinkTable (final FCPQueueManager queueManager, IndexBrowserPanel indexBrowser) {
		this.indexBrowser = indexBrowser;

		linkListModel = new LinkListModel();
		table = new JTable(linkListModel);
		table.setShowGrid(true);

		panel = new JPanel();
		panel.setLayout(new BorderLayout());

		panel.add(new JLabel(I18n.getMessage("thaw.plugin.index.linkList")), BorderLayout.NORTH);
		panel.add(new JScrollPane(table));

		table.addMouseListener(this);

		rightClickMenu = new JPopupMenu();
		rightClickActions = new Vector();

		toolbarModifier = new ToolbarModifier(indexBrowser.getMainWindow());
		toolbarActions = new Vector();

		JMenuItem item;
		JButton button;

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.addIndexesFromLink"));
		button = new JButton(IconBox.indexReuse);
		button.setToolTipText(I18n.getMessage("thaw.plugin.index.addIndexesFromLink"));
		toolbarActions.add(new LinkManagementHelper.IndexAdder(button, queueManager,
								       indexBrowser));

		toolbarModifier.addButtonToTheToolbar(button);
		rightClickMenu.add(item);
		rightClickActions.add(new LinkManagementHelper.IndexAdder(item, queueManager,
									  indexBrowser));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.copyKeys"));
		rightClickMenu.add(item);
		rightClickActions.add(new LinkManagementHelper.PublicKeyCopier(item));

		item = new JMenuItem(I18n.getMessage("thaw.common.remove"));
		button = new JButton(IconBox.delete);
		button.setToolTipText(I18n.getMessage("thaw.common.remove"));
		toolbarActions.add(new LinkManagementHelper.LinkRemover(button));
		toolbarModifier.addButtonToTheToolbar(button);
		rightClickMenu.add(item);
		rightClickActions.add(new LinkManagementHelper.LinkRemover(item));

		gotoItem = new JMenuItem(I18n.getMessage("thaw.plugin.index.gotoIndex"));
		rightClickMenu.add(gotoItem);
		gotoItem.addActionListener(this);

		updateRightClickMenu(null);
	}

	public ToolbarModifier getToolbarModifier() {
		return toolbarModifier;
	}

	public JPanel getPanel() {
		return panel;
	}

	protected void updateRightClickMenu(final Vector selectedLinks) {
		LinkManagementHelper.LinkAction action;

		for (final Iterator it = rightClickActions.iterator();
		     it.hasNext(); ) {
			action = (LinkManagementHelper.LinkAction)it.next();
			action.setTarget(selectedLinks);
		}

		gotoItem.setEnabled((linkList != null) && !(linkList instanceof Index));
	}

	protected void updateToolbar(final Vector selectedLinks) {
		LinkManagementHelper.LinkAction action;

		for (final Iterator it = toolbarActions.iterator();
		     it.hasNext(); ) {
			action = (LinkManagementHelper.LinkAction)it.next();
			action.setTarget(selectedLinks);
		}
	}

	protected Vector getSelectedLinks(final int[] selectedRows) {
		final Vector srcList = linkList.getLinkList();
		final Vector links = new Vector();

		for(int i = 0 ; i < selectedRows.length ; i++) {
			final Link link = (Link)srcList.get(selectedRows[i]);
			links.add(link);
		}

		return links;
	}

	public void setLinkList(final LinkList linkList) {
		if(this.linkList != null) {
			this.linkList.unloadLinks();
		}

		if(linkList != null) {
			linkList.loadLinks(null, true);
		}

		this.linkList = linkList;

		linkListModel.reloadLinkList(linkList);
	}

	public void mouseClicked(final MouseEvent e) {
		Vector selection;

		if (linkList instanceof Index)
			((Index)linkList).setChanged(false);

		if (linkList == null) {
			selectedRows = null;
			return;
		}

		selectedRows = table.getSelectedRows();
		selection = getSelectedLinks(selectedRows);

		if ((e.getButton() == MouseEvent.BUTTON1) && linkList != null) {
			updateToolbar(selection);
			toolbarModifier.displayButtonsInTheToolbar();
		}

		if ((e.getButton() == MouseEvent.BUTTON3)
		   && (linkList != null)) {
			updateRightClickMenu(selection);
			rightClickMenu.show(e.getComponent(), e.getX(), e.getY());
		}
	}

	public void mouseEntered(final MouseEvent e) { }

	public void mouseExited(final MouseEvent e) { }

	public void mousePressed(final MouseEvent e) { }

	public void mouseReleased(final MouseEvent e) { }

	public void keyPressed(final KeyEvent e) { }

	public void keyReleased(final KeyEvent e) { }

	public void keyTyped(final KeyEvent e) { }

	public void actionPerformed(final ActionEvent e) {
		final Vector links;
		final String keyList = "";

		if (linkList == null) // don't forget that linkList == Index most of the time
			return;

		if (e.getSource() == gotoItem) {
			if (selectedRows.length <= 0)
				return;

			final Link link = (Link)linkList.getLinkList().get(selectedRows[0]);

			if (link.getParentId() == -1) {
				Logger.notice(this, "No parent ? Abnormal !");
				return;
			}

			Index parent;
			if (link.getParent() == null)
				parent = indexBrowser.getIndexTree().getRoot().getIndex(link.getParentId());
			else
				parent = link.getParent();

			if (parent == null) {
				Logger.notice(this, "Cannot find again the parent ?! Id: "+Integer.toString(link.getParentId()));
				return;
			}

			indexBrowser.getIndexTree().getTree().setSelectionPath(new TreePath(parent.getTreeNode().getPath()));

			indexBrowser.getTables().setList(parent);

			return;
		}
	}



	public class LinkListModel extends javax.swing.table.AbstractTableModel implements java.util.Observer {
		private static final long serialVersionUID = 1L;

		public Vector columnNames;

		public Vector links = null; /* thaw.plugins.index.Link Vector */

		public LinkList linkList;

		public LinkListModel() {
			super();

			columnNames = new Vector();

			columnNames.add(I18n.getMessage("thaw.plugin.index.index"));
			columnNames.add(I18n.getMessage("thaw.common.key"));
		}

		public void reloadLinkList(final LinkList newLinkList) {
			if ((linkList != null) && (linkList instanceof Observable)) {
				((Observable)linkList).deleteObserver(this);
			}

			if ((newLinkList != null) && (newLinkList instanceof Observable)) {
				((Observable)newLinkList).addObserver(this);
			}

			linkList = newLinkList;


			if(links != null) {
				for(final Iterator it = links.iterator();
				    it.hasNext(); ) {
					final thaw.plugins.index.Link link = (thaw.plugins.index.Link)it.next();
					link.deleteObserver(this);
				}
			}

			links = null;

			if(linkList != null) {
				links = linkList.getLinkList();
			}

			this.refresh();

		}

		public int getRowCount() {
			if (links == null)
				return 0;

			return links.size();
		}

		public int getColumnCount() {
			return columnNames.size();
		}

		public String getColumnName(final int column) {
			return (String)columnNames.get(column);
		}

		public Object getValueAt(final int row, final int column) {
			final thaw.plugins.index.Link link = (thaw.plugins.index.Link)links.get(row);

			switch(column) {
			case(0): return link.getIndexName();
			case(1): return link.getPublicKey();
			default: return null;
			}
		}

		public void refresh() {
			if(linkList != null) {
				links = linkList.getLinkList();
			}

			final TableModelEvent event = new TableModelEvent(this);
			this.refresh(event);
		}

		public void refresh(final int row) {
			final TableModelEvent event = new TableModelEvent(this, row);
			this.refresh(event);
		}

		public void refresh(final TableModelEvent e) {
			fireTableChanged(e);
		}

		public void update(final java.util.Observable o, final Object param) {
			if(param instanceof thaw.plugins.index.Link) {

				//link.deleteObserver(this);
				//link.addObserver(this);
			}

			this.refresh(); /* TODO : Do it more nicely ... :) */
		}
	}

}

