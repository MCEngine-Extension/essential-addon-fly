package io.github.mcengine.extension.addon.essential.fly.command;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import io.github.mcengine.extension.addon.essential.fly.item.FlyItem;
import io.github.mcengine.extension.addon.essential.fly.util.CommandUtil;
import io.github.mcengine.extension.addon.essential.fly.util.FlyDuration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Handles the {@code /fly} command.
 * <p>
 * Supported syntax:
 * <ul>
 *   <li><b>/fly</b> or <b>/fly on</b> — enable flight if {@code fly_duration > 0}; prevents duplicate activation when already flying.</li>
 *   <li><b>/fly off</b> — disable flight if active; if not active, informs the player.</li>
 *   <li><b>/fly time add &lt;player&gt; &lt;seconds&gt;</b> — admin add time (delegated to {@link CommandUtil}).</li>
 *   <li><b>/fly get time</b> — show your own remaining flight time in Y/H/M/S format.</li>
 *   <li><b>/fly get item &lt;seconds&gt;</b> — give yourself a paper voucher that adds time.</li>
 *   <li><b>/fly get item &lt;hdbId&gt; &lt;seconds&gt;</b> — give yourself a head voucher (HeadDatabase if present; else paper).</li>
 * </ul>
 * <p>
 * Notes:
 * <ul>
 *   <li>This implementation treats {@code 0} as <b>no remaining time</b> (not unlimited).</li>
 *   <li>Per-player timers are handled by {@link FlyDuration}.</li>
 *   <li>Only <b>bare</b> {@code /fly} or explicit {@code on}/{@code off} will toggle flight. Other subcommands (e.g., {@code get}) never toggle.</li>
 * </ul>
 */
public class FlyCommand {

    /** Logger for command feedback and diagnostics. */
    private final MCEngineExtensionLogger logger;

    /** Database accessor for fly durations. */
    private final FlyDB flyDB;

    /** Per-player flight/timer manager. */
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

        // /fly get time
        if (args.length == 2 && args[0].equalsIgnoreCase("get") && args[1].equalsIgnoreCase("time")) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage("Only players can query their own flight time.");
                return true;
            }
            flyDB.ensurePlayerRow(self.getUniqueId());
            int seconds = Math.max(0, flyDB.getDuration(self.getUniqueId()));
            self.sendMessage("§7Your remaining flight time: §e" + FlyDuration.formatDuration(seconds) + "§7.");
            return true;
        }

        // /fly get item <seconds>  OR  /fly get item <hdbId> <seconds>
        if (args.length >= 2 && args[0].equalsIgnoreCase("get") && args[1].equalsIgnoreCase("item")) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage("Only players can receive vouchers.");
                return true;
            }
            if (args.length == 3) {
                // paper variant
                Integer secs = parsePositiveInt(args[2]);
                if (secs == null) {
                    sender.sendMessage("§cInvalid number for seconds: '" + args[2] + "'.");
                    return true;
                }
                ItemStack voucher = FlyItem.createPaperVoucher(secs);
                self.getInventory().addItem(voucher);
                self.sendMessage("§aYou received a Fly Time Voucher: §e" + FlyDuration.formatDuration(secs) + "§a.");
                return true;
            } else if (args.length == 4) {
                // head variant (HDB when available, else paper)
                String hdbId = args[2];
                Integer secs = parsePositiveInt(args[3]);
                if (secs == null) {
                    sender.sendMessage("§cInvalid number for seconds: '" + args[3] + "'.");
                    return true;
                }
                ItemStack voucher = FlyItem.createHdbVoucher(hdbId, secs);
                self.getInventory().addItem(voucher);
                self.sendMessage("§aYou received a Fly Time Voucher: §e" + FlyDuration.formatDuration(secs) + "§a.");
                return true;
            } else {
                sender.sendMessage("§7Usage: §f/fly get item <seconds> §7or §f/fly get item <hdbId> <seconds>");
                return true;
            }
        }

        // /fly get  -> provide usage hint; DO NOT toggle
        if (args.length == 1 && args[0].equalsIgnoreCase("get")) {
            sender.sendMessage("§7Usage: §f/fly get time §7or §f/fly get item <seconds> §7or §f/fly get item <hdbId> <seconds>");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /fly.");
            return true;
        }

        // Ensure the player has a row (no-op if exists)
        flyDB.ensurePlayerRow(player.getUniqueId());

        final boolean hasNoArgs = args.length == 0; // Only bare /fly may toggle
        final boolean forceOn = args.length >= 1 && args[0].equalsIgnoreCase("on");
        final boolean forceOff = args.length >= 1 && args[0].equalsIgnoreCase("off");
        final boolean isActive = flyDuration.isActive(player.getUniqueId());

        // Prevent duplicate explicit activation
        if (forceOn && isActive) {
            player.sendMessage("§7You are already flying.");
            return true;
        }

        // Activate only for explicit 'on' OR bare '/fly' when not active
        if ((forceOn || (hasNoArgs && !isActive))) {
            int duration = flyDB.getDuration(player.getUniqueId());
            if (duration <= 0) {
                player.sendMessage("§cYou have no flight time remaining.");
                return true;
            }

            flyDuration.activate(player);
            player.sendMessage("§aFlight enabled. §7Remaining: §e" + FlyDuration.formatDuration(duration) + "§7.");
            return true;
        }

        // Explicit 'off' handling
        if (forceOff) {
            if (!isActive) {
                player.sendMessage("§cYou are not currently flying.");
                return true;
            }
            flyDuration.deactivate(player.getUniqueId(), true, true);
            return true;
        }

        // Bare /fly toggling OFF if active
        if (hasNoArgs && isActive) {
            flyDuration.deactivate(player.getUniqueId(), true, true);
            return true;
        }

        // Any other subcommand that reaches here does not toggle
        player.sendMessage("§7Unknown subcommand. §7Try: §f/fly, /fly on, /fly off, /fly get time, /fly get item <seconds>, /fly get item <hdbId> <seconds>, /fly time add <player> <seconds>");
        return true;
    }

    /** Parse positive integer (seconds). */
    private static Integer parsePositiveInt(String s) {
        if (s == null) return null;
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
