package io.github.mcengine.extension.addon.essential.fly.listener;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import io.github.mcengine.extension.addon.essential.fly.util.FlyDuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Listener that:
 * <ul>
 *   <li>Ensures a default Fly DB record (duration 0) is present when a player joins.</li>
 *   <li>Disables flight and cancels that player's task on quit/kick to prevent offline decrement.</li>
 * </ul>
 */
public class FlyListener implements Listener {

    /**
     * Logger for listener diagnostics.
     */
    private final MCEngineExtensionLogger logger;

    /**
     * Database accessor for ensuring player rows exist.
     */
    private final FlyDB flyDB;

    /**
     * Per-player flight/timer manager.
     */
    private final FlyDuration flyDuration;

    /**
     * Create a listener for Fly events.
     *
     * @param logger      Logger for diagnostics.
     * @param flyDB       Database accessor.
     * @param flyDuration Per-player scheduler manager.
     */
    public FlyListener(MCEngineExtensionLogger logger, FlyDB flyDB, FlyDuration flyDuration) {
        this.logger = logger;
        this.flyDB = flyDB;
        this.flyDuration = flyDuration;
    }

    /**
     * Ensure the player has a DB row (with default 0) on join.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        try {
            Player p = e.getPlayer();
            flyDB.ensurePlayerRow(p.getUniqueId());
        } catch (Exception ex) {
            logger.warning("Failed to ensure fly row on join: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        deactivate(e.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent e) {
        deactivate(e.getPlayer());
    }

    private void deactivate(Player p) {
        UUID uuid = p.getUniqueId();
        // Disable flight and cancel that player's task
        flyDuration.deactivate(uuid, true);
    }
}
