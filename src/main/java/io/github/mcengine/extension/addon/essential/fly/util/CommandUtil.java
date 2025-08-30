package io.github.mcengine.extension.addon.essential.fly.util;

import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utility methods for the Fly AddOn command layer.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Admin subcommands routing (e.g., {@code /fly time add ...}).</li>
 *   <li>Validation helpers (permissions, parsing, player lookup).</li>
 *   <li>Consistent messaging and formatted time display.</li>
 * </ul>
 */
public final class CommandUtil {

    /**
     * Permission required for admin time-grant operations.
     */
    private static final String PERM_ADD = "essential.fly.add";

    /**
     * Hidden constructor to enforce static-only usage.
     */
    private CommandUtil() {}

    /**
     * Entry point for {@code /fly time ...} subcommands.
     *
     * @param sender The command sender.
     * @param args   The raw arguments (expects "time" at index 0).
     * @param flyDB  Database accessor.
     * @return true if handled (including error/help paths).
     */
    public static boolean handleTimeSubcommand(CommandSender sender, String[] args, FlyDB flyDB) {
        // /fly time add <player> <seconds>
        if (args.length == 4 && equalsIgnoreCase(args[1], "add")) {
            return handleTimeAdd(sender, flyDB, args[2], args[3]);
        }

        // Usage help for /fly time
        sender.sendMessage("§7Usage: §f/fly time add <player> <seconds>");
        return true;
    }

    /**
     * Implements {@code /fly time add <player> <seconds>}.
     * <p>
     * Ensures permissions, validates input, upserts row, and reports updated remaining time
     * using {@link FlyDuration#formatDuration(int)}.
     *
     * @param sender     The command sender (must have {@code essential.fly.add}).
     * @param flyDB      Database accessor.
     * @param playerName Target player (must be online).
     * @param secondsStr Seconds to add (positive integer).
     * @return true if handled.
     */
    public static boolean handleTimeAdd(CommandSender sender, FlyDB flyDB, String playerName, String secondsStr) {
        if (!sender.hasPermission(PERM_ADD)) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        Player target = getOnlinePlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + playerName + "' is not online.");
            return true;
        }

        Integer addSeconds = parsePositiveInt(secondsStr);
        if (addSeconds == null) {
            sender.sendMessage("§cInvalid number for seconds: '" + secondsStr + "'.");
            return true;
        }

        flyDB.ensurePlayerRow(target.getUniqueId());
        int current = Math.max(0, flyDB.getDuration(target.getUniqueId()));
        int updated = current + addSeconds;
        flyDB.setDuration(target.getUniqueId(), updated);

        String formatted = FlyDuration.formatDuration(updated);
        sender.sendMessage("§aAdded §e" + addSeconds + "s §ato §b" + target.getName() + "§a. New remaining: §e" + formatted + "§a.");
        target.sendMessage("§aYou received §e" + addSeconds + "s §aof flight time. Remaining: §e" + formatted + "§a.");
        return true;
    }

    /**
     * Get an online player by exact name.
     *
     * @param name Player name (case-sensitive per Bukkit's exact lookup).
     * @return Player if online; otherwise null.
     */
    private static Player getOnlinePlayerExact(String name) {
        if (name == null || name.isEmpty()) return null;
        Player p = Bukkit.getPlayerExact(name);
        if (p == null || !p.isOnline()) return null;
        return p;
    }

    /**
     * Parse a strictly positive integer from string input.
     *
     * @param s The string to parse.
     * @return Integer value if valid and > 0; otherwise null.
     */
    private static Integer parsePositiveInt(String s) {
        if (s == null) return null;
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Case-insensitive equals with null safety.
     */
    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }
}
