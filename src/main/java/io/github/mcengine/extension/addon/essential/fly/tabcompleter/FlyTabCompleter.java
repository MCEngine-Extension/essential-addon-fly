package io.github.mcengine.extension.addon.essential.fly.tabcompleter;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tab completer for the {@code /fly} command.
 * <p>
 * Supports:
 * <ul>
 *   <li>{@code /fly} → {@code on}, {@code off}, {@code get}, {@code time} (if permitted)</li>
 *   <li>{@code /fly get} → {@code time}</li>
 *   <li>{@code /fly time} → {@code add} (if permitted)</li>
 *   <li>{@code /fly time add <player> <seconds>} → online player names and common second values</li>
 * </ul>
 */
public class FlyTabCompleter implements TabCompleter {

    /**
     * Provides tab-completion for the {@code /fly} command.
     *
     * @param sender  The command sender.
     * @param command The command object.
     * @param alias   The alias used.
     * @param args    The command arguments.
     * @return A list of completion strings.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final boolean canAdmin = sender.hasPermission("essential.fly.add");

        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("on", "off", "get"));
            if (canAdmin) base.add("time");
            return prefixFilter(base, args[0]);
        }

        // /fly get ...
        if (args.length == 2 && args[0].equalsIgnoreCase("get")) {
            return prefixFilter(Collections.singletonList("time"), args[1]);
        }

        // /fly time ...
        if (args.length >= 2 && args[0].equalsIgnoreCase("time")) {
            if (!canAdmin) return Collections.emptyList();

            if (args.length == 2) {
                return prefixFilter(Collections.singletonList("add"), args[1]);
            }

            // /fly time add <player> <seconds>
            if (args.length == 3 && args[1].equalsIgnoreCase("add")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return prefixFilter(names, args[2]);
            }

            if (args.length == 4 && args[1].equalsIgnoreCase("add")) {
                // Suggest some common second values
                List<String> seconds = Arrays.asList("60", "120", "300", "600", "1800", "3600");
                return prefixFilter(seconds, args[3]);
            }
        }

        return Collections.emptyList();
    }

    /**
     * Utility: filter a list of options by the given (lowercased) prefix.
     */
    private List<String> prefixFilter(List<String> options, String userInput) {
        String prefix = userInput == null ? "" : userInput.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(prefix)) {
                out.add(opt);
            }
        }
        return out;
    }
}
