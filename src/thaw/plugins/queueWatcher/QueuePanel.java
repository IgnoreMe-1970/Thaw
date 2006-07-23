package thaw.plugins.queueWatcher;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.event.TableModelEvent;
import javax.swing.JProgressBar;
import javax.swing.JFileChooser;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Vector;
import java.util.Iterator;

import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;

import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;

import java.io.File;

import thaw.core.*;
import thaw.i18n.I18n;

import thaw.fcp.*;

public class QueuePanel implements MouseListener, ActionListener, KeyListener {
	private Core core;

	private JLabel label;

	private JTable table = null;
	private JScrollPane scrollPane = null;

	private JPanel panel;

	private QueueTableModel tableModel;
	private DetailPanel detailPanel;

	private JPopupMenu rightClickMenu;
	private JMenuItem removeItem;
	private JMenuItem cancelItem;
	private JMenuItem delayItem;
	private JMenuItem downloadItem;
	private JMenuItem forceRestartItem;
	private JMenuItem copyKeysItem;

	private int lastRowSelected = -1; /* Used for detail panel */
	private int[] selectedRows;
	private Vector queries;

	private boolean insertionQueue = false;

	public QueuePanel(Core core, DetailPanel detailPanel, boolean isForInsertionQueue) {
		insertionQueue = isForInsertionQueue;

		this.core = core;
		this.detailPanel = detailPanel;
		
		tableModel = new QueueTableModel(isForInsertionQueue);

		table = new JTable(tableModel);

		table.setShowGrid(true);
		
		if(isForInsertionQueue) {
			label = new JLabel(I18n.getMessage("thaw.common.insertions"));
		} else {
			label = new JLabel(I18n.getMessage("thaw.common.downloads"));
		}

		panel = new JPanel();
		panel.setLayout(new BorderLayout());

		panel.add(label, BorderLayout.NORTH);
		scrollPane = new JScrollPane(table);
		panel.add(scrollPane, BorderLayout.CENTER);

		table.setDefaultRenderer( table.getColumnClass(0), new ProgressRenderer(table, tableModel, isForInsertionQueue) );

		tableModel.addTableModelListener(table);
		
		rightClickMenu = new JPopupMenu();
		removeItem = new JMenuItem(I18n.getMessage("thaw.common.removeFromTheList"));
		cancelItem = new JMenuItem(I18n.getMessage("thaw.common.cancel"));
		delayItem = new JMenuItem(I18n.getMessage("thaw.common.delay"));
		downloadItem = new JMenuItem(I18n.getMessage("thaw.common.downloadLocally"));
		forceRestartItem = new JMenuItem(I18n.getMessage("thaw.common.forceRestart"));
		copyKeysItem = new JMenuItem(I18n.getMessage("thaw.common.copyKeysToClipboard"));
		
		rightClickMenu.add(removeItem);

		if( Integer.parseInt(core.getConfig().getValue("maxSimultaneousDownloads")) >= 0
		   || Integer.parseInt(core.getConfig().getValue("maxSimultaneousInsertions")) >= 0)
			rightClickMenu.add(cancelItem);

		if( Integer.parseInt(core.getConfig().getValue("maxSimultaneousDownloads")) >= 0
		   || Integer.parseInt(core.getConfig().getValue("maxSimultaneousInsertions")) >= 0)
			rightClickMenu.add(delayItem);
		
		if(!isForInsertionQueue)
			rightClickMenu.add(downloadItem);

		rightClickMenu.add(forceRestartItem);
		rightClickMenu.add(copyKeysItem);
		
		removeItem.addActionListener(this);
		cancelItem.addActionListener(this);
		copyKeysItem.addActionListener(this);
		forceRestartItem.addActionListener(this);
		delayItem.addActionListener(this);
		downloadItem.addActionListener(this);

		table.addMouseListener(this);
		table.addKeyListener(this);

		/* If a queue is already existing, we need to add it */
		addToTable(core.getQueueManager().getRunningQueue());

		Vector[] pendingQueues = core.getQueueManager().getPendingQueues();
		for(int i = 0 ; i < pendingQueues.length ; i++) {
			addToTable(pendingQueues[i]);
		}
	}


	private class ProgressRenderer extends DefaultTableCellRenderer {
		private final static long serialVersionUID = 20060709;

		private final Color SUCCESS = Color.GREEN;
		private final Color FAILURE = Color.RED;
		private final Color RUNNING = Color.ORANGE;
		private final Color PENDING = Color.WHITE;

		private QueueTableModel model = null;
		private JTable tabl = null;
		private boolean insertionQueue;

		public ProgressRenderer(JTable table, QueueTableModel model, boolean isForInsertion) {
			this.model = model;
			this.tabl = table;
			this.insertionQueue = isForInsertion;
		}

		public Component getTableCellRendererComponent(JTable table, Object value,
							       boolean isSelected, boolean hasFocus,
							       int row, int column) {

			if(value == null)
				return null;

			
			if(value instanceof Integer) {

				Integer progress = (Integer)value;
				JProgressBar bar = new JProgressBar(0, 100);

				bar.setStringPainted(true);
				bar.setBorderPainted(false);

				if(progress.intValue() >= 0) {
					bar.setValue(progress.intValue());
					bar.setString(progress.toString() + "%");
				} else {
					bar.setValue(100);
					bar.setString(I18n.getMessage("thaw.common.failed"));
				}


				return bar;
			}


			Component cell = super.getTableCellRendererComponent(table, value,
									     isSelected, hasFocus,
									     row, column);

			if(!isSelected) {
	
				FCPTransferQuery query = model.getQuery(row);
				
				if(query == null)
					return null;

				if(!query.isRunning() && !query.isFinished())
					cell.setBackground(PENDING);
				if(query.isFinished() && query.isSuccessful())
					cell.setBackground(SUCCESS);
				if(query.isFinished() && !query.isSuccessful())
					cell.setBackground(FAILURE);
				if(query.isRunning() && !query.isFinished())
					cell.setBackground(RUNNING);
			}
			

			return cell;
		}

	}


	public void reloadSelections() {
		selectedRows = table.getSelectedRows();
	}

	/**
	 * return a vector made of FCPTransferQueries.
	 * Doesn't refresh the selection !
	 */
	public Vector getSelectedQueries() {
		Vector queries = new Vector();
		Vector initialQueries = tableModel.getQueries();

		if(selectedRows == null)
			return queries;

		/* Create a separate vector to avoid collisions */
		for(int i = 0 ; i < selectedRows.length; i++) {
			queries.add(initialQueries.get(selectedRows[i]));
		}
		
		return queries;
	}


	public void resetTable() {
		tableModel.resetTable();
	}


	public void addToTable(FCPTransferQuery query) {
		if( (insertionQueue && query.getQueryType() == 2)
		    || (!insertionQueue && query.getQueryType() == 1)) {
			tableModel.addQuery(query);
		}
	}

	/**
	 * @param queries Vector of FCPTransferQuery only
	 */
	public synchronized void addToTable(Vector queries) {
		try {
			for(Iterator queryIt = queries.iterator();
			    queryIt.hasNext();) {
				
				FCPTransferQuery query = (FCPTransferQuery)queryIt.next();
				
				addToTable(query);
			}

		} catch(java.util.ConcurrentModificationException e) {
			Logger.notice(this, "Collision.");
		}
	}


	public void refresh() {
		int selected = table.getSelectedRow();
		/*
		if(lastRowSelected != selected) {
			lastRowSelected = selected;
			
			if(selected != -1)
				detailPanel.setQuery(tableModel.getQuery(selected));
		}
		*/
		lastRowSelected = selected;
			
		if(selected != -1)
			detailPanel.setQuery(tableModel.getQuery(selected));
	}

	public JPanel getPanel() {
		return panel;
	}


	public JTable getTable() {
		return table;
	}


	private class ActionReplier implements Runnable, ClipboardOwner {
		ActionEvent e;
		Vector queries;

		public ActionReplier(ActionEvent e, Vector queries) {
			this.e = e;
			this.queries = queries;
		}

		public void run() {
			Toolkit tk = Toolkit.getDefaultToolkit();
			String keys = "";
			File dir = null;

			if(e.getSource() == downloadItem) {
				FileChooser fileChooser = new FileChooser();
				fileChooser.setTitle(I18n.getMessage("thaw.common.downloadLocally"));
				fileChooser.setDirectoryOnly(true);
				fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
				dir = fileChooser.askOneFile();
			}
			
			
			for(Iterator queryIt = queries.iterator() ; queryIt.hasNext() ;) {
				FCPTransferQuery query = (FCPTransferQuery)queryIt.next();

				if(query == null)
					continue;
		
				if(e.getSource() == removeItem) {

					if(query.stop(core.getQueueManager())) {
						core.getQueueManager().remove(query);
						tableModel.removeQuery(query);
					}
				}

				if(e.getSource() == cancelItem) {
					query.stop(core.getQueueManager());
				}

				if(e.getSource() == delayItem) {
					if(query.isRunning() && !query.isFinished()) {
						query.pause(core.getQueueManager());
						core.getQueueManager().moveFromRunningToPendingQueue(query);
					}
				}

				if(e.getSource() == forceRestartItem) {
					query.stop(core.getQueueManager());

					if(query.getMaxAttempt() >= 0)
						query.setAttempt(0);

					query.start(core.getQueueManager());					
				}

				if(e.getSource() == copyKeysItem) {
					if(query.getFileKey() != null
					   && !query.getFileKey().equals(""))
						keys = keys + query.getFileKey() + "\n";
				}
				
				if(e.getSource() == downloadItem
				   && dir != null) {
					query.saveFileTo(dir.getPath());
				}
				
			} /* for i in selectedRows */
			
			
			
			if(e.getSource() == copyKeysItem) {
				StringSelection st = new StringSelection(keys);
				Clipboard cp = tk.getSystemClipboard();
				cp.setContents(st, this);
			}
			
		}

		public void lostOwnership(Clipboard clipboard, java.awt.datatransfer.Transferable contents) {
			/* we dont care */
		}
		
	}

	/**
	 * Manage it on a different thread to avoid UI freeze.
	 */
	public void actionPerformed(ActionEvent e) {
		Thread action = new Thread(new ActionReplier(e, getSelectedQueries()));

		action.start();
	}

	public void mouseClicked(MouseEvent e) {
		if(e.getButton() == MouseEvent.BUTTON3) {
			reloadSelections();
			queries = tableModel.getQueries();
			rightClickMenu.show(e.getComponent(), e.getX(), e.getY());
		}

		if(e.getButton() == MouseEvent.BUTTON1) {
			refresh();
		}
	}

	public void mouseEntered(MouseEvent e) {

	}

	public void mouseExited(MouseEvent e) {

	}

	public void mousePressed(MouseEvent e) {

	}

	public void mouseReleased(MouseEvent e) {

	}

	public void keyPressed(KeyEvent e) { }

	public void keyReleased(KeyEvent e) { refresh(); }
	public void keyTyped(KeyEvent e) { }
}

