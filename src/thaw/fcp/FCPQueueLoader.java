package thaw.fcp;

import java.util.Observable;
import java.util.Observer;

import thaw.core.Logger;

/**
 * Reload the queue from the queue node.
 * Send himself the ListPersistentRequests.
 * It remains active to receive and add the persistentGet/Put receive during the execution
 */
public class FCPQueueLoader implements FCPQuery, Observer {
	private FCPQueueManager queueManager;
	private String thawId;

	public FCPQueueLoader(final String thawId) {
		this.thawId = thawId;
	}

	public boolean start(final FCPQueueManager queueManager) {
		this.queueManager = queueManager;

		queueManager.getQueryManager().addObserver(this);


		final FCPListPersistentRequests listPersistent = new FCPListPersistentRequests();
		final boolean ret = listPersistent.start(queueManager);

		//if(ret)
		//	queueManager.getQueryManager().getConnection().addToWriterQueue();

		return ret;
	}


	public boolean stop(final FCPQueueManager queueManager) {
		queueManager.getQueryManager().deleteObserver(this);
		return true;
	}

	public int getQueryType() {
		return 0;
	}


	public void update(final Observable o, final Object param) {
		final FCPMessage msg = (FCPMessage)param;

		if("PersistentGet".equals( msg.getMessageName() )) {
			Logger.info(this, "Resuming from PersistentGet");

			int persistence = FCPClientGet.PERSISTENCE_FOREVER;

			if("reboot".equals( msg.getValue("PersistenceType") ))
				persistence = FCPClientGet.PERSISTENCE_UNTIL_NODE_REBOOT;
			else if ("connection".equals( msg.getValue("PersistenceType") ))
				persistence = FCPClientGet.PERSISTENCE_UNTIL_DISCONNECT;

			boolean global = true;

			if (msg.getValue("Global") != null) {
				global = Boolean.valueOf(msg.getValue("Global")).booleanValue();
			}

			String destinationDir = null;

			if(msg.getValue("Identifier").startsWith(thawId))
				destinationDir = msg.getValue("ClientToken");

			final int priority = Integer.parseInt(msg.getValue("PriorityClass"));


			final FCPClientGet clientGet = new FCPClientGet(msg.getValue("Identifier"),
									msg.getValue("URI"), // key
									priority, persistence, global,
									destinationDir, "Fetching",
									-1, queueManager);

			if(queueManager.addQueryToTheRunningQueue(clientGet, false))
				queueManager.getQueryManager().addObserver(clientGet);
			else
				Logger.info(this, "Already in the running queue");

		}


		if("PersistentPut".equals( msg.getMessageName() )) {
			Logger.info(this, "Resuming from PersistentPut");

			int persistence = FCPClientGet.PERSISTENCE_FOREVER;

			if("reboot".equals( msg.getValue("PersistenceType") ))
				persistence = FCPClientGet.PERSISTENCE_UNTIL_NODE_REBOOT;
			else if ("connection".equals( msg.getValue("PersistenceType") ))
				persistence = FCPClientGet.PERSISTENCE_UNTIL_DISCONNECT;

			boolean global = true;

			if("false".equals( msg.getValue("Global") ))
				global = false;

			final int priority = Integer.parseInt(msg.getValue("PriorityClass"));

			long fileSize = 0;

			if(msg.getValue("DataLength") != null)
				fileSize = Long.parseLong(msg.getValue("DataLength"));

			String filePath=null;

			if(msg.getValue("Identifier").startsWith(thawId))
				filePath = msg.getValue("ClientToken");

			String fileName = null;

			if ((fileName = msg.getValue("TargetFilename")) == null) {
				if (msg.getValue("Identifier").startsWith(thawId)) {
					fileName = (new java.io.File(filePath)).getName();
				} else /* this is not out insertion, and we don't have the filename
					  so we can't resume it */
				   return;
			}

			final FCPClientPut clientPut = new FCPClientPut(msg.getValue("Identifier"),
									msg.getValue("URI"), // key
									priority,
									persistence,
									global,
									filePath,
									fileName,
									"Inserting",
									0, /* progress */
									fileSize,
									queueManager);


			if(queueManager.addQueryToTheRunningQueue(clientPut, false))
				queueManager.getQueryManager().addObserver(clientPut);
			else
				Logger.info(this, "Already in the running queue");

			return;
		}

		if("EndListPersistentRequests".equals( msg.getMessageName() )) {
			Logger.info(this, "End Of ListPersistentRequests.");
			//queueManager.getQueryManager().getConnection().removeFromWriterQueue();
			queueManager.setQueueCompleted();
			return;
		}
	}
}
