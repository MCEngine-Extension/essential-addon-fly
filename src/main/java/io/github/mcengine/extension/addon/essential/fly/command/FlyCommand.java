package io.github.mcengine.extension.addon.essential.fly.command;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Handles the {@code /fly} command.
 * <p>
 * Toggle behavior:
 * - If turning ON: player gets flight enabled and is tracked as active (duration decrements every 30s unless it is 0).
 * - If turning OFF: flight is disabled and the player is removed from the active set (duration stops decreasing).
 */
public class FlyCommand {

    /**
     * Logger for command feedback and diagnostics.
     */
    private final MCEngineExtensionLogger logger;

    /**
     * Database accessor for fly durations.
     */
    private final FlyDB flyDB;

    /**
     * Shared set of active flyers updated by command/listener/ticker.
     */
    private final Set<UUID> activeFlyers;

    public FlyCommand(MCEngineExtensionLogger logger, FlyDB flyDB, Set<UUID> activeFlyers) {
        this.logger = logger;
        this.flyDB = flyDB;
        this.activeFlyers = activeFlyers;
    }

    /**
     * Executes the {@code /fly} command.
     *
     * @return true if handled.
     */
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /fly.");
            return true;
        }

        // Make sure the player has a row
        flyDB.ensurePlayerRow(player.getUniqueId());

        boolean forceOn = args.length >= 1 && args[0].equalsIgnoreCase("on");
        boolean forceOff = args.length >= 1 && args[0].equalsIgnoreCase("off");

        boolean currentlyActive = activeFlyers.contains(player.getUniqueId());

        if (forceOn || (!forceOff && !currentlyActive)) {
            // Turn ON
            try {
                player.setAllowFlight(true);
                player.setFlying(true);
            } catch (Throwable t) {
                logger.warning("Failed to enable flight for " + player.getName() + ": " + t.getMessage());
            }
            activeFlyers.add(player.getUniqueId());

            int duration = flyDB.getDuration(player.getUniqueId());
            if (duration == 0) {
                player.sendMessage("§aFlight enabled. §7(∞ duration)");
            } else {
                player.sendMessage("§aFlight enabled. §7Remaining: §e" + duration + "§7s");
            }
            return true;
        }

        if (forceOff || currentlyActive) {
            // Turn OFF
            try {
                player.setAllowFlight(false);
                player.setFlying(false);
            } catch (Throwable t) {
                logger.warning("Failed to disable flight for " + player.getName() + ": " + t.getMessage());
            }
            activeFlyers.remove(player.getUniqueId());
            player.sendMessage("§cFlight disabled.");
            return true;
        }

        // If we got here, user asked to turn off but they weren't active
        player.sendMessage("§7You are not currently flying.");
        return true;
    }
}
