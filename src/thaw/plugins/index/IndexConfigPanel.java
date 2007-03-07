package thaw.plugins.index;

import javax.swing.JPanel;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JCheckBox;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import thaw.core.*;

public class IndexConfigPanel implements ActionListener {
	private ConfigWindow configWindow;
	private Config config;

	private JPanel panel;

	private JCheckBox autorefreshActivated;
	private JTextField refreshInterval;
	private JTextField indexPerRefresh;

	private JCheckBox loadOnTheFly;


	public IndexConfigPanel(ConfigWindow configWindow, Config config) {
		this.configWindow = configWindow;
		this.config = config;

		panel = new JPanel();
		panel.setLayout(new GridLayout(15, 1));

		autorefreshActivated = new JCheckBox(I18n.getMessage("thaw.plugin.index.useAutoRefresh"));

		JLabel refreshIntervalLabel = new JLabel(I18n.getMessage("thaw.plugin.index.autoRefreshInterval"));
		refreshInterval = new JTextField("");

		JLabel indexPerRefreshLabel = new JLabel(I18n.getMessage("thaw.plugin.index.nmbIndexPerRefresh"));
		indexPerRefresh = new JTextField("");

		loadOnTheFly = new JCheckBox(I18n.getMessage("thaw.plugin.index.loadOnTheFly"));


		resetValues();

		autorefreshActivated.addActionListener(this);
		configWindow.getOkButton().addActionListener(this);
		configWindow.getCancelButton().addActionListener(this);

		panel.add(autorefreshActivated);
		panel.add(refreshIntervalLabel);
		panel.add(refreshInterval);
		panel.add(indexPerRefreshLabel);
		panel.add(indexPerRefresh);

		if (Boolean.valueOf(config.getValue("advancedMode")).booleanValue()) {
			panel.add(new JLabel(" "));
			panel.add(loadOnTheFly);
		}

		updateTextFieldState();
	}


	public void addTab() {
		configWindow.addTab(I18n.getMessage("thaw.plugin.index.indexes"),
				    thaw.core.IconBox.minIndex,
				    panel);
	}


	public void removeTab() {
		saveValues();
		configWindow.removeTab(panel);
	}


	public void updateTextFieldState() {
		refreshInterval.setEnabled(autorefreshActivated.isSelected());
		indexPerRefresh.setEnabled(autorefreshActivated.isSelected());
	}

	public void resetValues() {
		boolean activated = AutoRefresh.DEFAULT_ACTIVATED;
		int refreshIntervalInt = AutoRefresh.DEFAULT_INTERVAL;
		int nmbIndexInt = AutoRefresh.DEFAULT_INDEX_NUMBER;
		boolean loadOnTheFlyBoolean = false;

		try {
			if (config.getValue("indexAutoRefreshActivated") != null) {
				activated = Boolean.valueOf(config.getValue("indexAutoRefreshActivated")).booleanValue();
			}

			if (config.getValue("indexRefreshInterval") != null) {
				refreshIntervalInt = Integer.parseInt(config.getValue("indexRefreshInterval"));
			}

			if (config.getValue("nmbIndexesPerRefreshInterval") != null) {
				nmbIndexInt = Integer.parseInt(config.getValue("nmbIndexesPerRefreshInterval"));
			}


			if (config.getValue("loadIndexTreeOnTheFly") != null) {
				loadOnTheFlyBoolean = Boolean.valueOf(config.getValue("loadIndexTreeOnTheFly")).booleanValue();
			}

		} catch(NumberFormatException e) {
			Logger.error(this, "Error while parsing config !");
		}


		autorefreshActivated.setSelected(activated);
		refreshInterval.setText(Integer.toString(refreshIntervalInt));
		indexPerRefresh.setText(Integer.toString(nmbIndexInt));
		loadOnTheFly.setSelected(loadOnTheFlyBoolean);
	}


	public void saveValues() {
		config.setValue("indexAutoRefreshActivated",
				Boolean.toString(autorefreshActivated.isSelected()));
		config.setValue("indexRefreshInterval",
				refreshInterval.getText());
		config.setValue("nmbIndexesPerRefreshInterval",
				indexPerRefresh.getText());
		config.setValue("loadIndexTreeOnTheFly",
				Boolean.toString(loadOnTheFly.isSelected()));
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == autorefreshActivated) {
			updateTextFieldState();
		}

		if (e.getSource() == configWindow.getOkButton()) {
			saveValues();
		}

		if (e.getSource() == configWindow.getCancelButton()) {
			resetValues();
		}
	}

}
