package io.github.mcengine.extension.addon.essential.fly.listener;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Listener that ensures flight is deactivated on leave, so duration won't decrement offline.
 */
public class FlyListener implements Listener {

    /**
     * Logger for listener diagnostics.
     */
    private final MCEngineExtensionLogger logger;

    /**
     * Database accessor (not strictly required here, but kept for symmetry/extensions).
     */
    private final FlyDB flyDB;

    /**
     * Shared set of active flyers; players are removed here on quit/kick.
     */
    private final Set<UUID> activeFlyers;

    public FlyListener(MCEngineExtensionLogger logger, FlyDB flyDB, Set<UUID> activeFlyers) {
        this.logger = logger;
        this.flyDB = flyDB;
        this.activeFlyers = activeFlyers;
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
        try {
            p.setAllowFlight(false);
            p.setFlying(false);
        } catch (Throwable ignore) {}
        activeFlyers.remove(p.getUniqueId());
    }
}
