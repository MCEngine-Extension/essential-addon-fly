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
 * - If turning ON: player gets flight enabled and a per-player 30s task starts (unless duration is 0 = unlimited,
 *   which still starts the task but never decrements).
 * - If turning OFF: flight is disabled and the player's task is cancelled.
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
            flyDuration.activate(player);
            int duration = flyDB.getDuration(player.getUniqueId());
            if (duration == 0) {
                player.sendMessage("§aFlight enabled. §7(∞ duration)");
            } else {
                player.sendMessage("§aFlight enabled. §7Remaining: §e" + duration + "§7s");
            }
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
