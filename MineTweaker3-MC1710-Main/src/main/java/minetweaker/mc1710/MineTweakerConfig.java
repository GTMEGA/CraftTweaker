package minetweaker.mc1710;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class MineTweakerConfig {
	private MineTweakerConfig() { }
	public static boolean handleDesktopPackets = false, handleLoadScripts = true, sendLoadScripts = true,
			sendScriptsOnReloading = true, loadScriptsBeforeConnection = false, antiStuck = true;
	static File file;

	public static void load() {
		Configuration config = new Configuration(file, "1");
		config.load();
		handleDesktopPackets = config.getBoolean("handleDesktopPackets",
				"network", false, "Handle OpenBrowser and CopyClipboard packets? Insecure.");
		handleLoadScripts = config.getBoolean("handleLoadScripts", "network", true,
				"Proccess LoadScripts packet on client? Insecure, takes time, but update scripts on the fly...");

		sendLoadScripts = config.getBoolean("sendLoadScripts",
				"network", true, "Send scripts to players on logging in");
		sendScriptsOnReloading = config.getBoolean("sendScriptsOnReloading",
				"network", true, "Send scripts to players on command \"mt reload\"");

		loadScriptsBeforeConnection = config.getBoolean("loadScriptsBeforeConnection",
				"core", false, "Load scripts before connect to server? Save time and traffic. Requires server with disabled sendLoadScripts.");
		antiStuck = config.getBoolean("antiStuck",
				"core", false, "Do not reload scripts when relogging, saves time. Usable when playing on one server/singleplayer...");
		config.save();
	}
}
