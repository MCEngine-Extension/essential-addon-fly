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
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main class for the Fly extension.
 * <p>
 * Creates a default config (with {@code license: free}) if missing, validates the license,
 * wires a database accessor based on {@code database.type}, registers the {@code /fly} command,
 * and manages a 30s ticker that decrements active players' durations.
 * <p>
 * Convention: a duration of {@code 0} means unlimited flight time (no decrement).
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
     * Set of players that currently have flight toggled on by this AddOn.
     * <p>
     * Players in this set will have their remaining duration decremented
     * every 30 seconds (unless their duration is 0 = unlimited).
     */
    private final Set<UUID> activeFlyers = ConcurrentHashMap.newKeySet();

    /**
     * Reference to the repeating 30-second task that decrements durations.
     */
    private BukkitRunnable tickerTask;

    @Override
    public void onLoad(Plugin plugin) {
        logger = new MCEngineExtensionLogger(plugin, "AddOn", "EssentialFly");

        try {
            // Ensure config.yml exists (with license: free)
            ensureConfig(plugin);

            // Load and validate license
            File configFile = new File(plugin.getDataFolder(), folderPath + "/config.yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String licenseType = config.getString("license", "free");
            if (!"free".equalsIgnoreCase(licenseType)) {
                logger.warning("License is not 'free'. Disabling Essential Fly AddOn.");
                return;
            }

            // Pick DB implementation from main config: database.type = sqlite|mysql|postgresql
            Connection conn = MCEngineEssentialCommon.getApi().getDBConnection();
            String dbType;
            try {
                dbType = plugin.getConfig().getString("database.type", "sqlite");
            } catch (Throwable t) {
                dbType = "sqlite";
            }
            switch ((dbType == null ? "sqlite" : dbType.toLowerCase())) {
                case "mysql" -> flyDB = new FlyDBMySQL(conn, logger);
                case "postgresql", "postgres" -> flyDB = new FlyDBPostgreSQL(conn, logger);
                case "sqlite" -> flyDB = new FlyDBSQLite(conn, logger);
                default -> {
                    logger.warning("Unknown database.type='" + dbType + "', defaulting to SQLite for Fly.");
                    flyDB = new FlyDBSQLite(conn, logger);
                }
            }

            // Ensure DB schema
            flyDB.ensureSchema();

            // Register listeners
            PluginManager pm = Bukkit.getPluginManager();
            pm.registerEvents(new FlyListener(logger, flyDB, activeFlyers), plugin);

            // Reflectively register /fly command
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            Command flyCmd = new Command("fly") {

                /** Command handler for /fly. */
                private final FlyCommand handler = new FlyCommand(logger, flyDB, activeFlyers);

                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return handler.onCommand(sender, this, label, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    if (args.length == 1) {
                        return Arrays.asList("on", "off");
                    }
                    return Collections.emptyList();
                }
            };
            flyCmd.setDescription("Toggle flight mode for yourself (duration decreases every 30s when active).");
            flyCmd.setUsage("/fly [on|off]");
            commandMap.register(plugin.getName().toLowerCase(), flyCmd);

            // Start 30-second decrement ticker (runs sync)
            tickerTask = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        if (activeFlyers.isEmpty()) return;

                        List<UUID> toDisable = new ArrayList<>();

                        for (UUID uuid : activeFlyers) {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p == null || !p.isOnline()) {
                                // Safety: if not online, force-disable so duration won't tick
                                toDisable.add(uuid);
                                continue;
                            }

                            int current = flyDB.getDuration(uuid);
                            if (current == 0) {
                                // Unlimited: keep flying without decrement
                                continue;
                            }

                            int remaining = flyDB.decrementDuration(uuid, 30);
                            if (remaining <= 0) {
                                toDisable.add(uuid);
                                try {
                                    p.setAllowFlight(false);
                                    p.setFlying(false);
                                } catch (Throwable ignore) {}
                                p.sendMessage("§cYour flight time has expired.");
                            }
                        }

                        // Clean up any who need disabling
                        if (!toDisable.isEmpty()) {
                            for (UUID uuid : toDisable) {
                                activeFlyers.remove(uuid);
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Fly ticker encountered an error: " + e.getMessage());
                    }
                }
            };
            // 30 seconds = 20 ticks * 30 = 600
            tickerTask.runTaskTimer(plugin, 600L, 600L);

            logger.info("Enabled successfully.");
        } catch (Exception e) {
            logger.warning("Failed to initialize Fly: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDisload(Plugin plugin) {
        // Stop ticker
        if (tickerTask != null) {
            try {
                tickerTask.cancel();
            } catch (Throwable ignore) {}
        }
        // Disable flight for all tracked players
        for (UUID uuid : new HashSet<>(activeFlyers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                try {
                    p.setAllowFlight(false);
                    p.setFlying(false);
                } catch (Throwable ignore) {}
            }
        }
        activeFlyers.clear();
    }

    @Override
    public void setId(String id) {
        MCEngineCoreApi.setId("mcengine-essential-addon-fly");
    }

    /**
     * Ensure {@code config.yml} exists with a default {@code license: free}.
     */
    private void ensureConfig(Plugin plugin) throws IOException {
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
}
