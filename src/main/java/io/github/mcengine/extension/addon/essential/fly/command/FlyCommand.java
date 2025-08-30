package io.github.mcengine.extension.addon.essential.fly.command;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import io.github.mcengine.extension.addon.essential.fly.util.FlyDuration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the {@code /fly} command.
 * <p>
 * Supported syntax:
 * <ul>
 *   <li><b>/fly</b> or <b>/fly on</b> — enable flight if {@code fly_duration > 0}</li>
 *   <li><b>/fly off</b> — disable flight</li>
 *   <li><b>/fly time add &lt;player&gt; &lt;seconds&gt;</b> — add seconds to a player's remaining time
 *       (requires {@code essential.fly.add})</li>
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
        // Admin subcommand: /fly time add <player> <seconds>
        if (args.length >= 1 && args[0].equalsIgnoreCase("time")) {
            return handleTimeSubcommand(sender, args);
        }

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

    /**
     * Handle the admin subcommand tree for {@code /fly time ...}.
     */
    private boolean handleTimeSubcommand(CommandSender sender, String[] args) {
        // /fly time add <player> <seconds>
        if (args.length == 4 && args[1].equalsIgnoreCase("add")) {
            if (!sender.hasPermission("essential.fly.add")) {
                sender.sendMessage("§cYou don't have permission to use this command.");
                return true;
            }

            String targetName = args[2];
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null || !target.isOnline()) {
                sender.sendMessage("§cPlayer '" + targetName + "' is not online.");
                return true;
            }

            int addSeconds;
            try {
                addSeconds = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cInvalid number for seconds: '" + args[3] + "'.");
                return true;
            }

            if (addSeconds <= 0) {
                sender.sendMessage("§cSeconds must be a positive integer.");
                return true;
            }

            // Ensure row exists, then add time
            flyDB.ensurePlayerRow(target.getUniqueId());
            int current = Math.max(0, flyDB.getDuration(target.getUniqueId()));
            int updated = current + addSeconds;
            flyDB.setDuration(target.getUniqueId(), updated);

            sender.sendMessage("§aAdded §e" + addSeconds + "s §ato §b" + target.getName() + "§a. New remaining: §e" + updated + "§as.");
            target.sendMessage("§aYou received §e" + addSeconds + "s §aof flight time. Remaining: §e" + updated + "§as.");

            return true;
        }

        // Usage help for /fly time
        sender.sendMessage("§7Usage: §f/fly time add <player> <seconds>");
        return true;
    }
}
