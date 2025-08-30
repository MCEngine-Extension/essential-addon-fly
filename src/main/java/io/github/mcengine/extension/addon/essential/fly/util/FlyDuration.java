package io.github.mcengine.extension.addon.essential.fly.util;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active flight state for multiple players and runs
 * a 30-second ticker that decrements their remaining durations.
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
     * Start the 30-second ticker (20 ticks * 30 = 600 ticks).
     */
    public void startTicker() {
        if (tickerTask != null) return; // already running

        tickerTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (activeFlyers.isEmpty()) return;

                    Set<UUID> toDisable = new HashSet<>();

                    for (UUID uuid : activeFlyers) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p == null || !p.isOnline()) {
                            // not online ⇒ ensure not ticking down
                            toDisable.add(uuid);
                            continue;
                        }

                        int current = flyDB.getDuration(uuid);
                        if (current == 0) {
                            // unlimited
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
        tickerTask.runTaskTimer(plugin, 600L, 600L);
    }

    /**
     * Stop the ticker and disable flight for all tracked players.
     */
    public void stopTickerAndDisableAll() {
        if (tickerTask != null) {
            try {
                tickerTask.cancel();
            } catch (Throwable ignore) {}
            tickerTask = null;
        }
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

    /**
     * Get the live set of active flyers (shared with command/listener).
     *
     * @return mutable set of UUIDs representing active flyers.
     */
    public Set<UUID> getActiveFlyers() {
        return activeFlyers;
    }
}
