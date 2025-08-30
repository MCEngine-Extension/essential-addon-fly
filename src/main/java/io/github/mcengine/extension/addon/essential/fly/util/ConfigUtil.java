package io.github.mcengine.extension.addon.essential.fly.util;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * Utility functions for configuration tasks in the Fly AddOn.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Ensure {@code config.yml} exists under the Fly folder with {@code license: free} by default.</li>
 *   <li>Read the license value.</li>
 *   <li>Read the database type from the root plugin config ({@code database.type}).</li>
 * </ul>
 */
public final class ConfigUtil {

    /**
     * Hidden constructor to enforce static-only usage.
     */
    private ConfigUtil() {}

    /**
     * Ensure {@code config.yml} exists with a default {@code license: free}.
     *
     * @param plugin     The Bukkit plugin.
     * @param folderPath The folder path inside the plugin data folder.
     * @param logger     Logger for warnings.
     * @throws IOException If saving config fails.
     */
    public static void ensureConfig(Plugin plugin, String folderPath, MCEngineExtensionLogger logger) throws IOException {
        File dir = new File(plugin.getDataFolder(), folderPath);
        if (!dir.exists() && !dir.mkdirs()) {
            logger.warning("Could not create Fly config directory: " + dir.getAbsolutePath());
        }
        File configFile = new File(dir, "config.yml");
        if (!configFile.exists()) {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("license", "free");
            cfg.save(configFile);
        }
    }

    /**
     * Read the license string from the Fly config file.
     *
     * @param plugin     The Bukkit plugin.
     * @param folderPath The Fly folder path inside the plugin data folder.
     * @return The license string (defaults to {@code "free"} if not found).
     */
    public static String readLicense(Plugin plugin, String folderPath) {
        File configFile = new File(plugin.getDataFolder(), folderPath + "/config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        return config.getString("license", "free");
    }

    /**
     * Read the database type from the root plugin config: {@code database.type}.
     *
     * @param plugin The Bukkit plugin.
     * @return One of {@code sqlite}, {@code mysql}, {@code postgresql} (defaults to {@code sqlite} on errors).
     */
    public static String readDbType(Plugin plugin) {
        try {
            return plugin.getConfig().getString("database.type", "sqlite");
        } catch (Throwable t) {
            return "sqlite";
        }
    }
}
