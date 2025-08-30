package io.github.mcengine.extension.addon.essential.fly.util;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player flight state and spawns an individual 30-second repeating task per active player.
 * <p>
 * Convention: a duration of {@code 0} means unlimited and is never decremented.
 */
public class FlyDuration {

    /**
     * Owning plugin reference (for scheduling tasks).
     */
    private final Plugin plugin;

    /**
     * Logger for diagnostics and warnings.
     */
    private final MCEngineExtensionLogger logger;

    /**
     * Database accessor for reading/updating durations.
     */
    private final FlyDB flyDB;

    /**
     * Map of player UUIDs to their active repeating task.
     * <p>
     * Presence in this map means the player is considered "active" for flight.
     */
    private final Map<UUID, BukkitTask> tasksByPlayer = new ConcurrentHashMap<>();

    /**
     * Construct a new {@link FlyDuration} manager.
     *
     * @param plugin The Bukkit plugin instance.
     * @param logger Logger to use.
     * @param flyDB  Database accessor for durations.
     */
    public FlyDuration(Plugin plugin, MCEngineExtensionLogger logger, FlyDB flyDB) {
        this.plugin = plugin;
        this.logger = logger;
        this.flyDB = flyDB;
    }

    /**
     * Activate flight for a player and start (or keep) their individual 30s task.
     * If the player is offline, this will do nothing.
     *
     * @param player The online player to activate.
     */
    public void activate(Player player) {
        if (player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();

        // Enable flight on player
        try {
            player.setAllowFlight(true);
            player.setFlying(true);
        } catch (Throwable t) {
            logger.warning("Failed to enable flight for " + player.getName() + ": " + t.getMessage());
        }

        // If already active, don't double-schedule
        if (tasksByPlayer.containsKey(uuid)) return;

        // Schedule a per-player repeating task (every 30s)
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) {
                        // Player went offline: cancel their task and ensure no decrement happens offline
                        deactivate(uuid, false);
                        return;
                    }

                    int current = flyDB.getDuration(uuid);
                    if (current == 0) {
                        // Unlimited: keep them flying; no decrement
                        return;
                    }

                    int remaining = flyDB.decrementDuration(uuid, 30);
                    if (remaining <= 0) {
                        // Expired: disable flight and stop this task
                        try {
                            p.setAllowFlight(false);
                            p.setFlying(false);
                        } catch (Throwable ignore) {}
                        p.sendMessage("§cYour flight time has expired.");
                        deactivate(uuid, false);
                    }
                } catch (Exception e) {
                    logger.warning("Per-player fly task error for " + uuid + ": " + e.getMessage());
                }
            }
        }.runTaskTimer(plugin, 600L, 600L);

        tasksByPlayer.put(uuid, task);
    }

    /**
     * Deactivate flight for a player and cancel their individual task.
     *
     * @param uuid          The player's UUID.
     * @param disableFlight Whether to actively disable flight flags on the player.
     */
    public void deactivate(UUID uuid, boolean disableFlight) {
        // Cancel task if present
        BukkitTask task = tasksByPlayer.remove(uuid);
        if (task != null) {
            try {
                task.cancel();
            } catch (Throwable ignore) {}
        }

        if (disableFlight) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                try {
                    p.setAllowFlight(false);
                    p.setFlying(false);
                } catch (Throwable ignore) {}
            }
        }
    }

    /**
     * Check if a player currently has an active per-player task (i.e., is flying via this AddOn).
     *
     * @param uuid The player's UUID.
     * @return true if the player is active.
     */
    public boolean isActive(UUID uuid) {
        return tasksByPlayer.containsKey(uuid);
    }

    /**
     * Stop all per-player tasks and disable flight for anyone tracked.
     */
    public void stopAll() {
        for (Map.Entry<UUID, BukkitTask> e : tasksByPlayer.entrySet()) {
            try {
                e.getValue().cancel();
            } catch (Throwable ignore) {}
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                try {
                    p.setAllowFlight(false);
                    p.setFlying(false);
                } catch (Throwable ignore) {}
            }
        }
        tasksByPlayer.clear();
    }
}
