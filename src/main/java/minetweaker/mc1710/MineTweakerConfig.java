package minetweaker.mc1710;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class MineTweakerConfig {
	private MineTweakerConfig() { }

	/**
	 * Handle OpenBrowser and CopyClipboard packets? Insecure.
	 */
	public static final boolean handleDesktopPackets = false;

	/**
	 * Process LoadScripts packet on client? Insecure, takes time, but update scripts on the fly...
	 */
    public static final boolean handleLoadScripts = true;

	/**
	 * Send scripts to players on logging in
	 */
    public static final boolean sendLoadScripts = true;

	/**
	 * Send scripts to players on command "mt reload"
	 */
    public static final boolean sendScriptsOnReloading = true;

	/**
	 * Load scripts before connect to server? Save time and traffic. Requires server with disabled sendLoadScripts.
	 */
    public static final boolean loadScriptsBeforeConnection = false;

	/**
	 * Do not reload scripts when relogging, saves time. Usable when playing on one server/singleplayer...
	 */
    public static final boolean antiStuck = false;
}
