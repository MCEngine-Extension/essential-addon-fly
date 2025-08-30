package io.github.mcengine.extension.addon.essential.fly.command;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import io.github.mcengine.extension.addon.essential.fly.util.CommandUtil;
import io.github.mcengine.extension.addon.essential.fly.util.FlyDuration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the {@code /fly} command.
 * <p>
 * Supported syntax:
 * <ul>
 *   <li><b>/fly</b> or <b>/fly on</b> — enable flight if {@code fly_duration > 0}; prevents duplicate activation when already flying.</li>
 *   <li><b>/fly off</b> — disable flight and subtract the partial elapsed time since last tick from DB; shows formatted remaining time.</li>
 *   <li><b>/fly time add &lt;player&gt; &lt;seconds&gt;</b> — admin add time (delegated to {@link CommandUtil}).</li>
 * </ul>
 * <p>
 * Notes:
 * <ul>
 *   <li>This implementation treats {@code 0} as <b>no remaining time</b> (not unlimited).</li>
 *   <li>Per-player timers are handled by {@link FlyDuration}.</li>
 * </ul>
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
        // Admin subcommands are delegated to CommandUtil
        if (args.length >= 1 && args[0].equalsIgnoreCase("time")) {
            return CommandUtil.handleTimeSubcommand(sender, args, flyDB);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /fly.");
            return true;
        }

        // Ensure the player has a row (no-op if exists)
        flyDB.ensurePlayerRow(player.getUniqueId());

        final boolean forceOn = args.length >= 1 && args[0].equalsIgnoreCase("on");
        final boolean forceOff = args.length >= 1 && args[0].equalsIgnoreCase("off");
        final boolean isActive = flyDuration.isActive(player.getUniqueId());

        // Prevent duplicate explicit activation
        if (forceOn && isActive) {
            player.sendMessage("§7You are already flying.");
            return true;
        }

        if (forceOn || (!forceOff && !isActive)) {
            // Prevent activation when player has no remaining time (0 means no time)
            int duration = flyDB.getDuration(player.getUniqueId());
            if (duration <= 0) {
                player.sendMessage("§cYou have no flight time remaining.");
                return true;
            }

            // Activate and report remaining time with formatted units
            flyDuration.activate(player);
            player.sendMessage("§aFlight enabled. §7Remaining: §e" + FlyDuration.formatDuration(duration) + "§7.");
            return true;
        }

        if (forceOff || isActive) {
            // Self-deactivation: also subtract the partial elapsed time since last tick and tell remaining
            flyDuration.deactivate(player.getUniqueId(), true, true);
            player.sendMessage("§cFlight disabled.");
            return true;
        }

        player.sendMessage("§7You are not currently flying.");
        return true;
    }
}
