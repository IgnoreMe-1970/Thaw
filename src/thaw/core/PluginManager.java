package thaw.core;

import java.util.HashMap;
import java.util.Vector;
import java.util.Iterator;

/**
 * Manages plugins :)
 */
public class PluginManager {
	private final static String[] defaultPlugins = {"thaw.plugins.QueueWatcher"};

	private Core core = null;

	private HashMap plugins = null; // String (pluginName) -> Plugin


	/**
	 * Need a ref to the core to pass it to the plugins (and to access config)
	 */
	public PluginManager(Core core) {
		this.core = core;
		this.plugins = new HashMap();
	}


	/**
	 * Load plugin from config or from default list.
	 */
	public boolean loadPlugins() {
		Vector pluginNames;

		if(core.getConfig().getPluginNames().size() == 0) {
			/* Then we load the config with the default plugins */
			for(int i = 0 ; i < defaultPlugins.length ; i++) {
				core.getConfig().addPlugin(defaultPlugins[i]);
			}
		}

		pluginNames = core.getConfig().getPluginNames();

		for(int i=0 ; i < pluginNames.size(); i++) {
			loadPlugin((String)pluginNames.get(i));
		}

		return true;
	}

	/** 
	 * Start plugins.
	 */
	public boolean runPlugins() {
		Iterator pluginIt;
		
		try {
			pluginIt = plugins.values().iterator();
			
			while(pluginIt.hasNext()) {
				Plugin plugin = (Plugin)pluginIt.next();

				try {
					plugin.run(core);
				} catch(Exception e) {
					Logger.error(this, "Unable to run the plugin '"+plugin.getClass().getName()+"'");
				}


			}
		} catch(NullPointerException e) {
			Logger.notice(this, "No plugin to run");
			return false;
		}
		

		return true;
	}


	/**
	 * Stop all plugins.
	 */
	public boolean stopPlugins() {
		Iterator pluginIt;
		
		try {
			pluginIt = plugins.values().iterator();
			
			while(pluginIt.hasNext()) {
				Plugin plugin = (Plugin)pluginIt.next();
				
				try {
					plugin.stop();
				} catch(Exception e) {
					Logger.error(this, "Unable to run the plugin '"+plugin.getClass().getName()+"'");
				}


			}
		} catch(NullPointerException e) {
			Logger.notice(this, "No plugin to load");
			return false;
		}
		

		return true;
	}


	/**
	 * Load a given plugin (without adding it to the config or running it).
	 */
	public boolean loadPlugin(String className) {
		try {
			if(plugins.get(className) != null) {
				Logger.warning(this, "loadPlugin(): Plugin '"+className+"' already loaded");
				return false;
			}

			plugins.put(className, Class.forName(className).newInstance());
			
		} catch(Exception e) {
			Logger.warning(this, "loadPlugin('"+className+"'): Exception: "+e);
			return false;
		}

		return true;
	}


	/**
	 * Run a given plugin.
	 */
	public boolean runPlugin(String className) {
		try {
			((Plugin)plugins.get(className)).run(core);
			
		} catch(Exception e) {
			Logger.warning(this, "runPlugin('"+className+"'): Exception: "+e);
			return false;
		}

		return true;
	}
	

	/**
	 * Stop a given plugin.
	 */
	public boolean stopPlugin(String className) {
		try {
			((Plugin)plugins.get(className)).stop();
			
		} catch(Exception e) {
			Logger.warning(this, "runPlugin('"+className+"'): Exception: "+e);
			return false;
		}

		return true;
	}


	/**
	 * Unload a given plugin (without adding it to the config or running it).
	 */
	public boolean unloadPlugin(String className) {
		try {
			if(plugins.get(className) == null) {
				Logger.warning(this, "unloadPlugin(): Plugin '"+className+"' already unloaded");
				return false;
			}

			plugins.put(className, null);
			
		} catch(Exception e) {
			Logger.warning(this, "unloadPlugin('"+className+"'): Exception: "+e);
			return false;
		}

		return true;
	}

	public Plugin getPlugin(String className) {
		return (Plugin)plugins.get(className);
	}
}
