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
 * Convention: a duration of {@code 0} means no remaining time and activation is denied.
 * <p>
 * Behavior:
 * <ul>
 *   <li>Prevents duplicate activation by ignoring re-activation attempts at the scheduler layer.</li>
 *   <li>On self-deactivation, subtracts the partial elapsed time since the last 30s tick and informs the player.</li>
 *   <li>Whenever time is reduced (each 30s tick or partial on self-deactivate), sends remaining time formatted as year/hour/minute/second.</li>
 *   <li><b>FIX</b>: Always sends the remaining time message on self-deactivation, even if no partial seconds passed since the last tick.</li>
 * </ul>
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
     * Map of player UUIDs to the last time (in millis) we accounted for duration.
     * <p>
     * Updated on activation, every 30s tick, and used to compute partial elapsed seconds on self-deactivation.
     */
    private final Map<UUID, Long> lastTickMillis = new ConcurrentHashMap<>();

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

        // Record "now" as the last accounted moment
        lastTickMillis.put(uuid, System.currentTimeMillis());

        // Schedule a per-player repeating task (every 30s)
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) {
                        // Player went offline: cancel their task and ensure no decrement happens offline
                        deactivate(uuid, false, false);
                        return;
                    }

                    int current = flyDB.getDuration(uuid);
                    if (current <= 0) {
                        // Nothing left; ensure disabled and stop
                        try {
                            p.setAllowFlight(false);
                            p.setFlying(false);
                        } catch (Throwable ignore) {}
                        p.sendMessage("§cYour flight time has expired.");
                        deactivate(uuid, false, false);
                        return;
                    }

                    // Regular 30-second decrement
                    int remaining = flyDB.decrementDuration(uuid, 30);
                    // Update last accounted time to now (align to this run)
                    lastTickMillis.put(uuid, System.currentTimeMillis());

                    // Inform player of remaining time in formatted units
                    if (remaining > 0) {
                        p.sendMessage("§7Remaining: §e" + formatDuration(remaining) + "§7.");
                    } else {
                        try {
                            p.setAllowFlight(false);
                            p.setFlying(false);
                        } catch (Throwable ignore) {}
                        p.sendMessage("§cYour flight time has expired.");
                        deactivate(uuid, false, false);
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
        deactivate(uuid, disableFlight, false);
    }

    /**
     * Deactivate flight for a player and cancel their individual task.
     *
     * @param uuid          The player's UUID.
     * @param disableFlight Whether to actively disable flight flags on the player.
     * @param countPartial  When true, also subtract the partial elapsed seconds since the last tick and
     *                      <b>always</b> inform the player of the remaining time (even if partial is 0).
     */
    public void deactivate(UUID uuid, boolean disableFlight, boolean countPartial) {
        // Cancel task if present
        BukkitTask task = tasksByPlayer.remove(uuid);
        if (task != null) {
            try {
                task.cancel();
            } catch (Throwable ignore) {}
        }

        boolean informed = false;
        int remainingAfter = -1;

        // Optionally subtract partial elapsed time since last tick
        if (countPartial) {
            Long last = lastTickMillis.get(uuid);
            if (last != null) {
                long now = System.currentTimeMillis();
                long deltaMs = Math.max(0L, now - last);
                int partialSeconds = (int) Math.floor(deltaMs / 1000.0);

                if (partialSeconds > 0) {
                    remainingAfter = flyDB.decrementDuration(uuid, partialSeconds);
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        if (remainingAfter > 0) {
                            p.sendMessage("§7Remaining: §e" + formatDuration(remainingAfter) + "§7.");
                        } else {
                            p.sendMessage("§cYour flight time has expired.");
                        }
                        informed = true;
                    }
                }
            }
        }

        // If we didn't inform yet (e.g., partialSeconds == 0), still send remaining time on self-deactivate
        if (countPartial && !informed) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                if (remainingAfter < 0) {
                    remainingAfter = Math.max(0, flyDB.getDuration(uuid));
                }
                if (remainingAfter > 0) {
                    p.sendMessage("§7Remaining: §e" + formatDuration(remainingAfter) + "§7.");
                } else {
                    p.sendMessage("§cYour flight time has expired.");
                }
            }
        }

        // Clear lastTick marker
        lastTickMillis.remove(uuid);

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
        lastTickMillis.clear();
    }

    /**
     * Format a duration (in seconds) as "Xy Yh Zm Ws".
     *
     * @param totalSeconds total seconds remaining.
     * @return formatted string containing years, hours, minutes, and seconds.
     */
    public static String formatDuration(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        final int SEC_PER_MIN = 60;
        final int SEC_PER_HOUR = 60 * SEC_PER_MIN;
        final int SEC_PER_YEAR = 365 * 24 * SEC_PER_HOUR;

        int years = totalSeconds / SEC_PER_YEAR;
        int rem = totalSeconds % SEC_PER_YEAR;

        int hours = rem / SEC_PER_HOUR;
        rem %= SEC_PER_HOUR;

        int minutes = rem / SEC_PER_MIN;
        int seconds = rem % SEC_PER_MIN;

        return years + "y " + hours + "h " + minutes + "m " + seconds + "s";
        // Note: Spec requested year/hour/minute/second. Days are folded into hours for simplicity.
    }
}
