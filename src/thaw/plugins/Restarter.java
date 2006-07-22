package thaw.plugins;

import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.JCheckBox;
import java.awt.GridLayout;

import java.util.Vector;
import java.util.Iterator;
import java.util.Observer;
import java.util.Observable;
import java.util.Random;

import thaw.i18n.I18n;
import thaw.core.*;
import thaw.fcp.*;

/**
 * This plugin, after a given time, restart all/some the failed downloads (if maxDownloads >= 0, downloads to restart are choosen randomly).
 * A not too bad example to show how to make plugins.
 */
public class Restarter implements Observer, Runnable, Plugin {

	private int interval = 180; /* in s */
	private boolean restartFatals = false;

	private int timeElapsed = 0;

	private Core core;
	private boolean running = true;

	private Thread restarter = null;

	private JPanel configPanel;

	private JLabel restartIntervalLabel;
	private JTextField restartIntervalField;

	private JCheckBox restartFatalsBox;


	public boolean run(Core core) {
		this.core = core;

		/* Reloading value from the configuration */
		try {
			if(core.getConfig().getValue("restartInterval") != null
			   && core.getConfig().getValue("restartFatals") != null) {
				interval = Integer.parseInt(core.getConfig().getValue("restartInterval"));
				restartFatals = Boolean.valueOf(core.getConfig().getValue("restartFatals")).booleanValue();
			}
		} catch(Exception e) { /* probably conversion errors */ /* Yes I know, it's dirty */
			Logger.notice(this, "Unable to read / understand value from the config. Using default values");
		}

		
		/* Adding restart config tab to the config window */
		configPanel = new JPanel();
		configPanel.setLayout(new GridLayout(15, 1));

		restartIntervalLabel = new JLabel(I18n.getMessage("thaw.plugin.restarter.interval"));
		restartIntervalField = new JTextField(Integer.toString(interval));
		
		restartFatalsBox = new JCheckBox(I18n.getMessage("thaw.plugin.restarter.restartFatals"), restartFatals);

		configPanel.add(restartIntervalLabel);
		configPanel.add(restartIntervalField);
		configPanel.add(restartFatalsBox);

		core.getConfigWindow().addTab(I18n.getMessage("thaw.plugin.restarter.configTabName"), configPanel);
		core.getConfigWindow().addObserver(this);

		running = true;
		restarter = new Thread(this);
		restarter.start();

		return true;
	}


	public boolean stop() {
		core.getConfigWindow().removeTab(configPanel);
		core.getConfigWindow().deleteObserver(this);
		running = false;

		return true;
	}

	
	public void run() {
		while(running) {
			try {

				for(timeElapsed = 0 ; timeElapsed < interval && running; timeElapsed++) {
					Thread.sleep(1000);
				}

			} catch(java.lang.InterruptedException e) {
				// We really really really don't care.			
			}

			Logger.notice(this, "Restarting [some] failed downloads");

			try {
				if(!running)
					break;
				
				int maxDownloads = core.getQueueManager().getMaxDownloads();
				int alreadyRunning = 0;
				int failed = 0;
				Vector runningQueue = core.getQueueManager().getRunningQueue();
			
				
				if(maxDownloads >= 0) {
					/* We count how many are really running
					   and we write down those which are failed */
					for(Iterator it = runningQueue.iterator();
					    it.hasNext();) {
						FCPTransferQuery query = (FCPTransferQuery)it.next();
						
						if(query.getQueryType() != 1)
							continue;

						if(query.isRunning() && !query.isFinished()) {
							alreadyRunning++;
						}
						
						if(query.isFinished() && !query.isSuccessful()
						   && (restartFatals || !query.isFatallyFailed()) ) {
							failed++;
						}
					}
					
					
					/* We choose randomly the ones to restart */
					while(alreadyRunning < maxDownloads && failed > 0) {
						int toRestart = (new Random()).nextInt(failed);
						
						Iterator it = runningQueue.iterator();
						int i = 0;

						while(it.hasNext()) {
							FCPTransferQuery query = (FCPTransferQuery)it.next();
							
							if(query.getQueryType() != 1)
								continue;

							if(query.isFinished() && !query.isSuccessful()
							   && (restartFatals || !query.isFatallyFailed())) {
								if(i == toRestart) {
									restartQuery(query);
									break;
								}

								i++;
							}
						}

						alreadyRunning++;
						failed--;
					}


				} else { /* => if maxDownloads < 0 */

					/* We restart them all */
					for(Iterator it = runningQueue.iterator();
					    it.hasNext();) {
						FCPTransferQuery query = (FCPTransferQuery)it.next();
						
						if(query.getQueryType() == 1 && query.isFinished()
						   && !query.isSuccessful()
						   && (restartFatals || !query.isFatallyFailed()))
							restartQuery(query);
					}
				}
				
				

			} catch(java.util.ConcurrentModificationException e) {
				Logger.notice(this, "Collision ! Sorry I will restart failed downloads later ...");
			} catch(Exception e) {
				Logger.error(this, "Exception : "+e);
			}
			
		}
	}


	public void restartQuery(FCPTransferQuery query) {
		query.stop(core.getQueueManager());

		if(query.getMaxAttempt() >= 0)
			query.setAttempt(0);
		
		query.start(core.getQueueManager());
	}

	public void update(Observable o, Object arg) {
		if(o == core.getConfigWindow()) {

			if(arg == core.getConfigWindow().getOkButton()){
				core.getConfig().setValue("restartInterval", restartIntervalField.getText());
				core.getConfig().setValue("restartFatals",
							  Boolean.toString(restartFatalsBox.isSelected()));

				/* Plugin will be stop() and start(), so no need to reload config */
							  
				return;
			}

			if(arg == core.getConfigWindow().getCancelButton()) {
				restartIntervalField.setText(Integer.toString(interval));
				restartFatalsBox.setSelected(restartFatals);
				
				return;
			}
		}
	}

	public String getNameForUser() {
		return I18n.getMessage("thaw.plugin.restarter.restarter");
	}

}
