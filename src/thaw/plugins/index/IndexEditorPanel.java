package thaw.plugins;

import javax.swing.JPanel;
import javax.swing.JSplitPane;

import javax.swing.tree.DefaultMutableTreeNode;

import javax.swing.JToolBar;
import javax.swing.JButton;

import javax.swing.JFileChooser;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import thaw.core.*;
import thaw.fcp.*;

import thaw.plugins.index.*;


public class IndexEditorPanel implements java.util.Observer, javax.swing.event.TreeSelectionListener, ActionListener {
	public final static int DEFAULT_INSERTION_PRIORITY = 4;

	private IndexTree indexTree;

	private JSplitPane split;

	private JPanel listAndDetails;
	private FileTable fileTable;
	private FileDetailsEditor fileDetails;

	private JToolBar toolBar;
	private JButton addButton;
	private JButton insertAndAddButton;
	
	private FileList fileList = null;
	
	private Hsqldb db;
	private FCPQueueManager queueManager;


	public IndexEditorPanel(Hsqldb db, FCPQueueManager queueManager) {
		this.db = db;
		this.queueManager = queueManager;

		indexTree = new IndexTree(I18n.getMessage("thaw.plugin.index.yourIndexes"), true, queueManager, db);

		listAndDetails = new JPanel();
		listAndDetails.setLayout(new BorderLayout(10, 10));

		fileTable = new FileTable(true, queueManager);
		fileDetails = new FileDetailsEditor(true);

		toolBar = new JToolBar();
		toolBar.setFloatable(false);

		addButton = new JButton(IconBox.addToIndexAction);
		addButton.setToolTipText(I18n.getMessage("thaw.plugin.index.addFilesWithoutInserting"));
		insertAndAddButton = new JButton(IconBox.insertAndAddToIndexAction);
		insertAndAddButton.setToolTipText(I18n.getMessage("thaw.plugin.index.addFilesWithInserting"));

		addButton.addActionListener(this);
		insertAndAddButton.addActionListener(this);

		buttonsEnabled(false);

		toolBar.add(addButton);
		toolBar.add(insertAndAddButton);

		listAndDetails.add(toolBar, BorderLayout.NORTH);
		listAndDetails.add(fileTable.getPanel(), BorderLayout.CENTER);
		listAndDetails.add(fileDetails.getPanel(), BorderLayout.SOUTH);

		split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				       indexTree.getPanel(),
				       listAndDetails);

		indexTree.addTreeSelectionListener(this);

	}

	public JSplitPane getPanel() {
		return split;
	}

	public void save() {
		indexTree.save();
	}

	public void buttonsEnabled(boolean a) {
		addButton.setEnabled(a);
		insertAndAddButton.setEnabled(a);
	}

	public void setFileList(FileList l) {
		buttonsEnabled(l != null && l instanceof Index);

		this.fileList = l;
		fileTable.setFileList(l);		
	}

	public void valueChanged(javax.swing.event.TreeSelectionEvent e) {
		javax.swing.tree.TreePath path = e.getPath();
		
		if(path == null) {
			Logger.notice(this, "Path null ?");
			setFileList(null);
			return;
		}
		
		IndexTreeNode node = (IndexTreeNode)((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();

		if(node == null) {
			Logger.notice(this, "Node null ?");
			setFileList(null);
			return;
		}

		if(node instanceof FileList) {			
			setFileList((FileList)node);
			return;
		}
		
		setFileList(null);
	}


	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == addButton
		   || e.getSource() == insertAndAddButton) {
			FileChooser fileChooser = new FileChooser();
			
			if(e.getSource() == addButton)
				fileChooser.setTitle(I18n.getMessage("thaw.plugin.index.addFilesWithInserting"));
			if(e.getSource() == insertAndAddButton)
				fileChooser.setTitle(I18n.getMessage("thaw.plugin.index.addFilesWithoutInserting"));

			fileChooser.setDirectoryOnly(false);
			fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
			
			java.io.File[] files = fileChooser.askManyFiles();

			if(files == null) {
				Logger.info(this, "add[andInsert]Button : Cancelled");
				return;
			}

			String category = FileCategory.promptForACategory();

			for(int i = 0 ; i < files.length ; i++) {
				FCPTransferQuery insertion = null;

				if(e.getSource() == insertAndAddButton) {
					insertion = new FCPClientPut(files[i], 0, 0, null,
								     null, DEFAULT_INSERTION_PRIORITY,
								     true, 0, false);
					((FCPClientPut)insertion).addObserver(this);
					queueManager.addQueryToThePendingQueue(insertion);
				} else {
					insertion = new FCPClientPut(files[i], 0, 0, null,
								     null, DEFAULT_INSERTION_PRIORITY,
								     true, 2, true); /* getCHKOnly */
					insertion.start(queueManager);
				}


				thaw.plugins.index.File file = new thaw.plugins.index.File(db, files[i].getPath(),
											   category, (Index)fileList,
											   insertion);
				
				((Index)fileList).addFile(file);
			}
		}
	}


	public void update(java.util.Observable o, Object param) {
		if(o instanceof FCPClientPut) {
			FCPClientPut clientPut = (FCPClientPut)o;
			if(clientPut.isFinished()) {
				queueManager.remove(clientPut);
				
			}
		}
	}
}