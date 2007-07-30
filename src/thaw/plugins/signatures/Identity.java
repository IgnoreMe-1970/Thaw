package thaw.plugins.signatures;

import java.awt.Color;

import java.sql.*;

import java.util.Vector;
import java.util.Iterator;
import java.util.List;

import frost.crypt.FrostCrypt;
import frost.util.XMLTools;
import org.w3c.dom.*;
import org.xml.sax.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;


import thaw.core.Core;
import thaw.core.Logger;
import thaw.core.I18n;
import thaw.plugins.Hsqldb;
import thaw.core.Config;



public class Identity {

	public final static int[] trustLevelInt = {
		100,
		10,
		1,
		0,
		-1,
		-10
	};

	public final static String[] trustLevelStr = {
		I18n.getMessage("thaw.plugin.signature.trustLevel.dev"),
		I18n.getMessage("thaw.plugin.signature.trustLevel.good"),
		I18n.getMessage("thaw.plugin.signature.trustLevel.observe"),
		I18n.getMessage("thaw.plugin.signature.trustLevel.check"),
		I18n.getMessage("thaw.plugin.signature.trustLevel.bad"),
		I18n.getMessage("thaw.plugin.signature.trustLevel.evil")
	};

	public final static String[] trustLevelUserStr= {
		I18n.getMessage("thaw.plugin.signature.trustLevel.good"),
		I18n.getMessage("thaw.plugin.signature.trustLevel.observe"),
		I18n.getMessage("thaw.plugin.signature.trustLevel.check"),
		I18n.getMessage("thaw.plugin.signature.trustLevel.bad"),
		I18n.getMessage("thaw.plugin.signature.trustLevel.evil")
	};

	public final static Color[] trustLevelColor = {
		Color.BLUE,
		new java.awt.Color(0, 128, 0), /* light green */
		new java.awt.Color(0, 175, 0), /* green */
		Color.BLACK,
		new java.awt.Color(175, 0, 0), /* moderatly red */
		Color.RED
	};


	private Hsqldb db;

	private int id;

	private String nick;


	/* public key (aka Y) */
	private String publicKey;

	/* private key (aka X) */
	private String privateKey;

	private boolean isDup;
	private int trustLevel;


	private String hash;

	private static FrostCrypt frostCrypt;


	private Identity() {
	}


	/**
	 * If you don't have a value, let it to null and pray it won't be used :P
	 * @param nick part *before* the @
	 */
	public Identity(Hsqldb db, int id, String nick,
			String publicKey, String privateKey,
			boolean isDup,
			int trustLevel) {

		if (nick == null || publicKey == null) {
			Logger.error(this, "missing value ?!");

			if (nick == null)
				Logger.error(this, "nick missing");
			if (publicKey == null)
				Logger.error(this, "publicKey missing");
		}

		this.db = db;
		this.id = id;
		this.nick = nick;
		this.publicKey = publicKey;
		this.privateKey = privateKey;
		this.isDup = isDup;
		this.trustLevel = trustLevel;

		//hash = Base64.encode(SHA256.digest(publicKey.getBytes("UTF-8")));
		initFrostCrypt();

		hash = frostCrypt.digest(publicKey);
	}


	private static void initFrostCrypt() {
		if (frostCrypt == null)
			frostCrypt = new FrostCrypt();
	}

	public int getId() {
		return id;
	}

	public String getNick() {
		return nick;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public String getPrivateKey() {
		return privateKey;
	}

	public int getTrustLevel() {
		return trustLevel;
	}

	public static int getTrustLevel(String str) {
		int i;

		for (i = 0 ; i < trustLevelStr.length ; i++) {
			if (trustLevelStr[i].equals(str))
				return trustLevelInt[i];
		}

		return 0;

	}

	public String getTrustLevelStr() {
		if (privateKey != null) {
			return I18n.getMessage("thaw.plugin.signature.trustLevel.me");
		}

		return getTrustLevelStr(trustLevel);
	}

	public static String getTrustLevelStr(int trustLevel) {
		int i;

		for (i = 0 ; i < trustLevelInt.length ; i++) {
			if (trustLevelInt[i] == trustLevel)
				return trustLevelStr[i];
		}

		return "[?]";
	}

	public boolean isDup() {
		return isDup;
	}


	public Color getTrustLevelColor() {
		int i;

		if (privateKey != null)
			return new java.awt.Color(0, 175, 0);

		for (i = 0 ; i < trustLevelInt.length ; i++) {
			if (trustLevelInt[i] == trustLevel)
				break;
		}

		if (i < trustLevelInt.length) {
			return trustLevelColor[i];
		}

		return Color.BLACK;
	}

	public void setTrustLevel(String str) {
		int i;

		for (i = 0 ; i < Identity.trustLevelStr.length ; i++) {
			if (Identity.trustLevelStr[i].equals(str))
				break;
		}

		if (i >= Identity.trustLevelStr.length) {
			Logger.error(this, "Unknown trust level: "+str);
			return;
		}

		setTrustLevel(trustLevelInt[i]);
	}


	public void setTrustLevel(int i) {
		try {
			synchronized(db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("UPDATE signatures SET trustLevel = ? WHERE id = ?");
				st.setInt(1, i);
				st.setInt(2, id);

				st.execute();
			}

			trustLevel = i;

		} catch(SQLException e) {
			Logger.error(this, "Unable to change trust level because: "+e.toString());
		}
	}


	/**
	 * will put all the other identities with the same nickname as duplicata,
	 * and will put this identity as non duplicate
	 */
	public void setOriginal() {
		try {
			synchronized(db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("UPDATE signatures SET isDup = TRUE "
									 + "WHERE LOWER(nickName) = ?");
				st.setString(1, nick.toLowerCase());

				st.execute();

				st = db.getConnection().prepareStatement("UPDATE signatures SET isDup = FALSE "
									 + "WHERE id = ?");
				st.setInt(1, id);

				st.execute();
			}
		} catch(SQLException e) {
			Logger.error(this,
				     "SQLException while setting the identity as original : "
				     +e.toString());
		}
	}


	public boolean mustBeIgnored(Config config) {
		if (privateKey != null)
			return false;

		int min = Integer.parseInt(config.getValue("minTrustLevel"));

		return (trustLevel < min);
	}


	/**
	 * if the identity doesn't exists, it will be created
	 */
	public static Identity getIdentity(Hsqldb db,
					   String nick,
					   String publicKey) {
		try {
			synchronized(db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT id, nickName, publicKey, "+
									 "privateKey, isDup, trustLevel "+
									 "FROM signatures "+
									 "WHERE publicKey = ? LIMIT 1");
				st.setString(1, publicKey);

				ResultSet set = st.executeQuery();

				if (set.next()) {
					Identity i = new Identity(db, set.getInt("id"), set.getString("nickName"),
								  set.getString("publicKey"), set.getString("privateKey"),
								  set.getBoolean("isDup"), set.getInt("trustLevel"));
					Logger.debug(i, "Identity found");
					return i;
				}

				/* else we must add it, but first we need to know if it's a dup */

				st = db.getConnection().prepareStatement("SELECT id FROM signatures "+
									 "WHERE lower(nickName) = ? LIMIT 1");
				st.setString(1, nick.toLowerCase());

				set = st.executeQuery();

				boolean isDup = set.next();

				/* and we add */

				st = db.getConnection().prepareStatement("INSERT INTO signatures "+
									 "(nickName, publicKey, privateKey, isDup, trustLevel) "+
									 "VALUES (?, ?, ?, ?, 0)");

				st.setString(1, nick);
				st.setString(2, publicKey);
				st.setNull(3, Types.VARCHAR);
				st.setBoolean(4, isDup);

				st.execute();


				/* and next we find back the id */

				st = db.getConnection().prepareStatement("SELECT id "+
									 "FROM signatures "+
									 "WHERE publicKey = ? LIMIT 1");
				st.setString(1, publicKey);

				set = st.executeQuery();

				set.next();

				int id = set.getInt("id");

				Identity i = new Identity(db, id, nick, publicKey, null, isDup, 0);
				Logger.info(i, "New identity found");
				return i;

			}
		} catch(SQLException e) {
			Logger.error(new Identity(), "Error while getting identity (2) : "+e.toString());
		}

		return null;
	}


	/**
	 * won't create
	 */
	public static Identity getIdentity(Hsqldb db,
					   int id) {
		Identity i = null;

		try {
			synchronized(db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT id, nickName, publicKey, "+
									 "privateKey, isDup, trustLevel "+
									 "FROM signatures "+
									 "WHERE id = ? LIMIT 1");
				st.setInt(1, id);

				ResultSet set = st.executeQuery();

				if (!set.next())
					return null;

				i = new Identity(db, id, set.getString("nickName"),
						 set.getString("publicKey"), set.getString("privateKey"),
						 set.getBoolean("isDup"), set.getInt("trustLevel"));
			}
		} catch(SQLException e) {
			Logger.error(new Identity(), "Error while getting identity (1) : "+e.toString());
		}

		return i;
	}


	/**
	 * Generate a new identity
	 * you have to insert() it after
	 * @param db just here to fill in the class
	 */
	public static Identity generate(Hsqldb db, String nick) {
		Logger.info(null, "thaw.plugins.signatures.Identity : Generating new identity ...");

		//DSAPrivateKey privateKey = new DSAPrivateKey(Global.DSAgroupBigA, Core.getRandom());
		//DSAPublicKey publicKey = new DSAPublicKey(Global.DSAgroupBigA, privateKey);

		initFrostCrypt();

		String[] keys = frostCrypt.generateKeys();

		Identity identity = new Identity(db, -1, nick,
						 keys[1], /* public */
						 keys[0], /* private */
						 false,
						 10);


		Logger.info(identity, "done");

		return identity;
	}


	/**
	 * id won't be set
	 */
	public void insert() {
		try {
			synchronized(db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT id FROM signatures "+
									 "WHERE publicKey = ? LIMIT 1");
				st.setString(1, publicKey);
				st.execute();

				ResultSet set = st.executeQuery();

				if (set.next()) {
					int id = set.getInt("id");

					st = db.getConnection().prepareStatement("UPDATE signatures SET "+
										 "privateKey = ?, trustLevel = ? "+
										 "WHERE id = ?");
					st.setString(1, privateKey);
					st.setInt(2, trustLevel);
					st.setInt(3, id);

					st.execute();
				} else {

					st = db.getConnection().prepareStatement("INSERT INTO signatures "+
										 "(nickName, publicKey, privateKey, "+
										 "isDup, trustLevel) "+
										 "VALUES (?, ?, ?, ?, ?)");
					st.setString(1, nick);
					st.setString(2, publicKey);
					st.setString(3, privateKey);
					st.setBoolean(4, isDup);
					st.setInt(5, trustLevel);

					st.execute();
				}
			}
		} catch(SQLException e) {
			Logger.error(this, "Exception while adding the identity to the bdd: "+e.toString());
		}
	}


	public void delete() {
		try {
			synchronized(db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("DELETE FROM signatures "+
									 "WHERE id = ?");
				st.setInt(1, id);

				st.execute();
			}
		} catch(SQLException e) {
			Logger.warning(this, "Exception while deleting the identity from the bdd: "+e.toString());
			new thaw.gui.WarningWindow((thaw.core.MainWindow)null,
						   I18n.getMessage("thaw.plugin.signature.delete.cant"));
		}

	}


	public String sign(String text) {
		initFrostCrypt();

		return frostCrypt.detachedSign(text, privateKey);
	}


	public static String sign(String text, String privateKey) {
		initFrostCrypt();

		return frostCrypt.detachedSign(text, privateKey);
	}


	public boolean check(String text, String sig) {
		try {
			initFrostCrypt();
			return frostCrypt.detachedVerify(text, publicKey, sig);
		} catch(Exception e) {
			Logger.notice(this, "signature check failed because: "+e.toString());
			return false;
		}
	}


	public static boolean check(String text, /* signed text */
				    String sig,
				    String publicKey) /* y */ {
		initFrostCrypt();
		return frostCrypt.detachedVerify(text, publicKey, sig);
	}


	public String toString() {
		String n = nick;

		if (n.indexOf('@') >= 0)
			n.replaceAll("@", "_");

		return n+"@"+hash;
	}


	public static Vector getIdentities(Hsqldb db, String cond) {
		try {
			synchronized(db.dbLock) {
				Vector v = new Vector();

				PreparedStatement st;

				if (cond != null)
					st = db.getConnection().prepareStatement("SELECT id, nickName, publicKey, "+
										 "privateKey, isDup, trustLevel "+
										 "FROM signatures "+
										 "WHERE "+cond + " "+
										 "ORDER BY nickName");
				else
					st = db.getConnection().prepareStatement("SELECT id, nickName, publicKey, "+
										 "privateKey, isDup, trustLevel "+
										 "FROM signatures ORDER BY nickName");

				ResultSet set = st.executeQuery();

				while(set.next()) {
					v.add(new Identity(db,
							   set.getInt("id"),
							   set.getString("nickName"),
							   set.getString("publicKey"),
							   set.getString("privateKey"),
							   set.getBoolean("isDup"),
							   set.getInt("trustLevel")));
				}

				return v;
			}
		} catch(SQLException e) {
			Logger.error(new Identity(), "Error while getting identities (1): "+e.toString());
		}

		return null;
	}


	public static Vector getYourIdentities(Hsqldb db) {
		return getIdentities(db, "privateKey IS NOT NULL");
	}


	public static Vector getOtherIdentities(Hsqldb db) {
		return getIdentities(db, "privateKey IS NULL");
	}


	public Element makeCDATA(Document doc, String tagName, String content) {
		if (content == null || tagName == null)
			return null;

		CDATASection cdata;
		Element current;

		current = doc.createElement(tagName);
		cdata = doc.createCDATASection(content);
		current.appendChild(cdata);

		return current;
	}


	public boolean exportIdentity(File file) {

		Document doc = XMLTools.createDomDocument();

		Element root = doc.createElement("FrostLocalIdentities");
		Element identityEl = doc.createElement("MyIdentity");

		identityEl.appendChild(makeCDATA(doc, "name", toString()));
		identityEl.appendChild(makeCDATA(doc, "key", publicKey));
		identityEl.appendChild(makeCDATA(doc, "privKey", privateKey));

		root.appendChild(identityEl);
		doc.appendChild(root);

		return XMLTools.writeXmlFile(doc, file.getPath());
	}

	public static Identity importIdentity(Hsqldb db, File file) {
		try {
			Document doc = null;
			try {
				doc = XMLTools.parseXmlFile(file, false);
			} catch(Exception ex) {  // xml format error
				Logger.warning(ex, "Invalid Xml");
				return null;
			}

			if( doc == null ) {
				Logger.warning(null,
					       "Error: couldn't parse XML Document - " +
					       "File name: '" + file.getName() + "'");
				return null;
			}

			Element rootEl = doc.getDocumentElement();

			List l = XMLTools.getChildElementsByTagName(rootEl, "MyIdentity");

			if (l == null) {
				Logger.error(null, "No identity to import");
				return null;
			}

			for (Iterator it = l.iterator();
			     it.hasNext();) {
				Element identityEl = (Element)it.next();

				String[] split = XMLTools.getChildElementsCDATAValue(identityEl, "name").split("@");
				String nick = split[0];
				String publicKey = XMLTools.getChildElementsCDATAValue(identityEl, "key");
				String privateKey = XMLTools.getChildElementsCDATAValue(identityEl, "privKey");


				Identity identity = new Identity(db, -1, nick,
								 publicKey, privateKey, false,
								 10);
				identity.insert();
			}

		} catch(Exception e) {
			/* XMLTools throws runtime exception sometimes ... */
			Logger.error(e, "Unable to parse XML message because : "+e.toString());
			e.printStackTrace();
			return null;
		}

		return null;
	}
}

