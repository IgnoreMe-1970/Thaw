package thaw.plugins;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.GridLayout;
import java.util.Vector;
import java.util.Iterator;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;


import thaw.core.*;
import thaw.plugins.queueWatcher.*;

import thaw.fcp.*;

public class QueueWatcher extends ToolbarModifier implements thaw.core.Plugin, PropertyChangeListener, ChangeListener {
	private Core core;

	//private JPanel mainPanel;
	private JSplitPane mainPanel;

	private QueuePanel[] queuePanels = new QueuePanel[2];
	private DetailPanel detailPanel;
	private DragAndDropManager dnd;

	private JSplitPane split;

	private final static int DIVIDER_LOCATION = 310;
	private long lastChange = 0;
	private boolean folded = false;

	private boolean advancedMode = false;

	private java.awt.Container panelAdded;

	public QueueWatcher() {

	}


	public boolean run(Core core) {
		this.core = core;

		Logger.info(this, "Starting plugin \"QueueWatcher\" ...");

		this.detailPanel = new DetailPanel(core);

		queuePanels[0] = new QueuePanel(core, detailPanel, false); /* download */
		queuePanels[1] = new QueuePanel(core, detailPanel, true); /* upload */

		split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
				       queuePanels[0].getPanel(),
				       queuePanels[1].getPanel());


		this.advancedMode = Boolean.valueOf(core.getConfig().getValue("advancedMode")).booleanValue();

		if(advancedMode) {
			mainPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, detailPanel.getPanel(), split);

			if(core.getConfig().getValue("detailPanelFolded") == null
			   || ((new Boolean(core.getConfig().getValue("detailPanelFolded"))).booleanValue()) == true) {
				folded = true;
				detailPanel.getPanel().setVisible(false);
				mainPanel.setDividerLocation(1);
			} else {
				folded = false;
				detailPanel.getPanel().setVisible(true);
				mainPanel.setDividerLocation(DIVIDER_LOCATION);
			}

			mainPanel.addPropertyChangeListener(this);
			mainPanel.setOneTouchExpandable(true);

			panelAdded = mainPanel;
		} else {
			panelAdded = split;
		}

		setMainWindow(core.getMainWindow());
		core.getMainWindow().getTabbedPane().addChangeListener(this);
		core.getMainWindow().addTab(I18n.getMessage("thaw.common.status"),
					    IconBox.minQueue,
					    panelAdded);

		if (core.getConfig().getValue("queuePanelSplitLocation") == null) {
			split.setDividerLocation((MainWindow.DEFAULT_SIZE_Y - 150)/2); /* approximation */
		} else {
			split.setDividerLocation(Integer.parseInt(core.getConfig().getValue("queuePanelSplitLocation")));
		}

		split.setResizeWeight(0.5);

		dnd = new DragAndDropManager(core, queuePanels);

		stateChanged(null);

		return true;
	}


	public boolean stop() {
		Logger.info(this, "Stopping plugin \"QueueWatcher\" ...");

		core.getConfig().setValue("queuePanelSplitLocation",
					  Integer.toString(split.getDividerLocation()));

		this.core.getConfig().setValue("detailPanelFolded", ((new Boolean(this.folded)).toString()));
		this.core.getMainWindow().removeTab(this.panelAdded);

		return true;
	}


	public void addMenuItemToTheDownloadTable(javax.swing.JMenuItem item) {
		queuePanels[0].addMenuItem(item);
	}

	public void addMenuItemToTheInsertionTable(javax.swing.JMenuItem item) {
		queuePanels[1].addMenuItem(item);
	}

	public String getNameForUser() {
		return I18n.getMessage("thaw.common.status");
	}


	protected void addToPanels(Vector queries) {

		for(Iterator it = queries.iterator();
		    it.hasNext();) {

			FCPTransferQuery query = (FCPTransferQuery)it.next();

			if(query.getQueryType() == 1)
				this.queuePanels[0].addToTable(query);

			if(query.getQueryType() == 2)
				this.queuePanels[1].addToTable(query);

		}

	}

	/**
	 * Called when the split bar position changes.
	 */
	public void propertyChange(PropertyChangeEvent evt) {

		if("dividerLocation".equals( evt.getPropertyName() )) {

			if(System.currentTimeMillis() - this.lastChange < 500) {
				this.lastChange = System.currentTimeMillis();
				return;
			}

			this.lastChange = System.currentTimeMillis();

			this.folded = !this.folded;

			if(this.folded) {
				this.detailPanel.getPanel().setVisible(false);
				this.mainPanel.setDividerLocation(1);
			} else {
				this.detailPanel.getPanel().setVisible(true);
				this.mainPanel.setDividerLocation(DIVIDER_LOCATION);
			}

		}

	}

	/**
	 * Called when the JTabbedPane changed (ie change in the selected tab, etc)
	 * @param e can be null.
	 */
	public void stateChanged(ChangeEvent e) {
		int tabId;

		tabId = core.getMainWindow().getTabbedPane().indexOfTab(I18n.getMessage("thaw.common.status"));

		if (tabId < 0) {
			Logger.warning(this, "Unable to find the tab !");
			return;
		}

		if (core.getMainWindow().getTabbedPane().getSelectedIndex() == tabId) {
			displayButtonsInTheToolbar();
		} else {
			hideButtonsInTheToolbar();
		}
	}
}
