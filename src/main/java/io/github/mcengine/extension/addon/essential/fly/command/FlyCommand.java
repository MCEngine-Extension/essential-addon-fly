package io.github.mcengine.extension.addon.essential.fly.command;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import io.github.mcengine.extension.addon.essential.fly.util.FlyDuration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the {@code /fly} command.
 * <p>
 * Toggle behavior:
 * <ul>
 *   <li><b>ON</b>: Requires player to have {@code fly_duration > 0}. If the duration is {@code 0}, activation is denied.</li>
 *   <li><b>OFF</b>: Disables flight and cancels the player's per-player task immediately.</li>
 * </ul>
 * Note: This implementation treats {@code 0} as <b>no remaining time</b> (not unlimited).
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
     * Per-player flight/timer manager.
     */
    private final FlyDuration flyDuration;

    public FlyCommand(MCEngineExtensionLogger logger, FlyDB flyDB, FlyDuration flyDuration) {
        this.logger = logger;
        this.flyDB = flyDB;
        this.flyDuration = flyDuration;
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

        // Ensure the player has a row (no-op if exists)
        flyDB.ensurePlayerRow(player.getUniqueId());

        boolean forceOn = args.length >= 1 && args[0].equalsIgnoreCase("on");
        boolean forceOff = args.length >= 1 && args[0].equalsIgnoreCase("off");

        boolean isActive = flyDuration.isActive(player.getUniqueId());

        if (forceOn || (!forceOff && !isActive)) {
            // Prevent activation when player has no remaining time (0 means no time)
            int duration = flyDB.getDuration(player.getUniqueId());
            if (duration <= 0) {
                player.sendMessage("§cYou have no flight time remaining.");
                return true;
            }

            // Activate and report remaining time
            flyDuration.activate(player);
            player.sendMessage("§aFlight enabled. §7Remaining: §e" + duration + "§7s");
            return true;
        }

        if (forceOff || isActive) {
            flyDuration.deactivate(player.getUniqueId(), true);
            player.sendMessage("§cFlight disabled.");
            return true;
        }

        player.sendMessage("§7You are not currently flying.");
        return true;
    }
}
