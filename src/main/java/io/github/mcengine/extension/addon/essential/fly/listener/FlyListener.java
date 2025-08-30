package io.github.mcengine.extension.addon.essential.fly.listener;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.util.FlyDuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Listener that ensures each player's task is cancelled and flight is disabled when they leave,
 * so duration won't decrement offline.
 */
public class FlyListener implements Listener {

    /**
     * Logger for listener diagnostics.
     */
    private final MCEngineExtensionLogger logger;

    /**
     * Per-player flight/timer manager.
     */
    private final FlyDuration flyDuration;

    public FlyListener(MCEngineExtensionLogger logger, FlyDuration flyDuration) {
        this.logger = logger;
        this.flyDuration = flyDuration;
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
