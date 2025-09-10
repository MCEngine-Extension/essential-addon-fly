package io.github.mcengine.extension.addon.essential.fly;

import io.github.mcengine.api.core.MCEngineCoreApi;
import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.api.essential.extension.addon.IMCEngineEssentialAddOn;
import io.github.mcengine.common.essential.MCEngineEssentialCommon;
import io.github.mcengine.extension.addon.essential.fly.command.FlyCommand;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import io.github.mcengine.extension.addon.essential.fly.database.mysql.FlyDBMySQL;
import io.github.mcengine.extension.addon.essential.fly.database.postgresql.FlyDBPostgreSQL;
import io.github.mcengine.extension.addon.essential.fly.database.sqlite.FlyDBSQLite;
import io.github.mcengine.extension.addon.essential.fly.listener.FlyListener;
import io.github.mcengine.extension.addon.essential.fly.tabcompleter.FlyTabCompleter;
import io.github.mcengine.extension.addon.essential.fly.util.ConfigUtil;
import io.github.mcengine.extension.addon.essential.fly.util.FlyDuration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Main class for the Fly extension.
 * <p>
 * Delegates config management to {@link io.github.mcengine.extension.addon.essential.fly.util.ConfigUtil}
 * and per-player flight scheduling to {@link io.github.mcengine.extension.addon.essential.fly.util.FlyDuration}.
 * Registers the {@code /fly} command and listeners.
 */
public class Fly implements IMCEngineEssentialAddOn {

    /**
     * Logger instance for the Fly extension.
     * <p>
     * Used for initialization messages and error reporting.
     */
    private MCEngineExtensionLogger logger;

    /**
     * Database accessor for Fly durations.
     */
    private FlyDB flyDB;

    /**
     * Configuration folder path for the Fly AddOn.
     * Used as the base for {@code config.yml}.
     */
    private final String folderPath = "extensions/addons/configs/MCEngineFly";

    /**
     * Per-player flight/timer manager.
     */
    private FlyDuration flyDuration;

    @Override
    public void onLoad(Plugin plugin) {
        logger = new MCEngineExtensionLogger(plugin, "AddOn", "EssentialFly");

        try {
            // Ensure config and validate license
            ConfigUtil.ensureConfig(plugin, folderPath, logger);
            String licenseType = ConfigUtil.readLicense(plugin, folderPath);
            if (!"free".equalsIgnoreCase(licenseType)) {
                logger.warning("License is not 'free'. Disabling Essential Fly AddOn.");
                return;
            }

            // Wire DB based on database.type (no direct Connection usage)
            String dbType = ConfigUtil.readDbType(plugin);
            switch (dbType == null ? "sqlite" : dbType.toLowerCase()) {
                case "mysql" -> flyDB = new FlyDBMySQL(logger);
                case "postgresql", "postgres" -> flyDB = new FlyDBPostgreSQL(logger);
                case "sqlite" -> flyDB = new FlyDBSQLite(logger);
                default -> {
                    logger.warning("Unknown database.type='" + dbType + "', defaulting to SQLite for Fly.");
                    flyDB = new FlyDBSQLite(logger);
                }
            }

            // Ensure schema
            flyDB.ensureSchema();

            // Init per-player flight manager
            flyDuration = new FlyDuration(plugin, logger, flyDB);

            // Register listeners (ensures DB row on join; cancels per-player task on leave)
            PluginManager pm = Bukkit.getPluginManager();
            pm.registerEvents(new FlyListener(logger, flyDB, flyDuration), plugin);

            // Reflectively register /fly command
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Define the /fly command
            Command flyCmd = new Command("fly") {

                /** Handles command execution for {@code /fly}. */
                private final FlyCommand handler = new FlyCommand(logger, flyDB, flyDuration);

                /** Handles tab-completion for {@code /fly}. */
                private final FlyTabCompleter completer = new FlyTabCompleter();

                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return handler.onCommand(sender, this, label, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    return completer.onTabComplete(sender, this, alias, args);
                }
            };

            flyCmd.setDescription("Toggle flight mode (duration decreases every 30s when active; 0 = no time).");
            flyCmd.setUsage("/fly [on|off] | /fly time add <player> <seconds>");

            // Dynamically register the /fly command
            commandMap.register(plugin.getName().toLowerCase(), flyCmd);

            logger.info("Enabled successfully.");
        } catch (Exception e) {
            logger.warning("Failed to initialize Fly: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisload(Plugin plugin) {
        // Stop all per-player tasks and disable flight
        if (flyDuration != null) {
            flyDuration.stopAll();
        }
    }

    @Override
    public void setId(String id) {
        MCEngineCoreApi.setId("mcengine-essential-addon-fly");
    }
}
