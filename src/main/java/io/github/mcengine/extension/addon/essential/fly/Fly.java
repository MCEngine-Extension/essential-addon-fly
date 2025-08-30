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
import io.github.mcengine.extension.addon.essential.fly.util.ConfigUtil;
import io.github.mcengine.extension.addon.essential.fly.util.FlyDuration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Main class for the Fly extension.
 * <p>
 * Delegates config management to {@link ConfigUtil} and flight/ticker management
 * to {@link FlyDuration}. Registers the {@code /fly} command and listeners.
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
     * Flight/ticker manager that tracks multiple players
     * and decrements their durations every 30 seconds.
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

            // Wire DB based on database.type
            Connection conn = MCEngineEssentialCommon.getApi().getDBConnection();
            String dbType = ConfigUtil.readDbType(plugin);
            switch (dbType == null ? "sqlite" : dbType.toLowerCase()) {
                case "mysql" -> flyDB = new FlyDBMySQL(conn, logger);
                case "postgresql", "postgres" -> flyDB = new FlyDBPostgreSQL(conn, logger);
                case "sqlite" -> flyDB = new FlyDBSQLite(conn, logger);
                default -> {
                    logger.warning("Unknown database.type='" + dbType + "', defaulting to SQLite for Fly.");
                    flyDB = new FlyDBSQLite(conn, logger);
                }
            }

            // Ensure schema
            flyDB.ensureSchema();

            // Init flight manager (supports multiple players) and start 30s ticker
            flyDuration = new FlyDuration(plugin, logger, flyDB);
            flyDuration.startTicker();

            // Register listeners
            PluginManager pm = Bukkit.getPluginManager();
            pm.registerEvents(new FlyListener(logger, flyDB, flyDuration.getActiveFlyers()), plugin);

            // Reflectively register /fly command
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            Command flyCmd = new Command("fly") {

                /** Handles command execution for {@code /fly}. */
                private final FlyCommand handler = new FlyCommand(logger, flyDB, flyDuration.getActiveFlyers());

                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return handler.onCommand(sender, this, label, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    if (args.length == 1) return Arrays.asList("on", "off");
                    return Collections.emptyList();
                }
            };
            flyCmd.setDescription("Toggle flight mode (duration decreases every 30s when active; 0 = unlimited).");
            flyCmd.setUsage("/fly [on|off]");
            commandMap.register(plugin.getName().toLowerCase(), flyCmd);

            logger.info("Enabled successfully.");
        } catch (Exception e) {
            logger.warning("Failed to initialize Fly: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisload(Plugin plugin) {
        // Stop ticker and clean up players
        if (flyDuration != null) {
            flyDuration.stopTickerAndDisableAll();
        }
    }

    @Override
    public void setId(String id) {
        MCEngineCoreApi.setId("mcengine-essential-addon-fly");
    }
}
