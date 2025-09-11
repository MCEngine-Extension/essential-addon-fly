package io.github.mcengine.extension.addon.essential.fly.listener;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import io.github.mcengine.extension.addon.essential.fly.item.FlyItem;
import io.github.mcengine.extension.addon.essential.fly.util.FlyDuration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Listener that:
 * <ul>
 *   <li>Ensures a default Fly DB record (duration 0) is present when a player joins.</li>
 *   <li>On quit/kick, disables flight, cancels that player's task, and
 *       <b>subtracts partial elapsed time</b> since the last tick using {@code lastTickMillis}.</li>
 *   <li>Detects right-click with a Fly Time voucher and grants the encoded time (consumes one item).</li>
 * </ul>
 */
public class FlyListener implements Listener {

    /** Logger for listener diagnostics. */
    private final MCEngineExtensionLogger logger;

    /** Database accessor for ensuring player rows exist. */
    private final FlyDB flyDB;

    /** Per-player flight/timer manager. */
    private final FlyDuration flyDuration;

    /**
     * Create a listener for Fly events.
     *
     * @param logger      Logger for diagnostics.
     * @param flyDB       Database accessor.
     * @param flyDuration Per-player scheduler manager.
     */
    public FlyListener(MCEngineExtensionLogger logger, FlyDB flyDB, FlyDuration flyDuration) {
        this.logger = logger;
        this.flyDB = flyDB;
        this.flyDuration = flyDuration;
    }

    /** Ensure the player has a DB row (with default 0) on join. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        try {
            Player p = e.getPlayer();
            flyDB.ensurePlayerRow(p.getUniqueId());
        } catch (Exception ex) {
            logger.warning("Failed to ensure fly row on join: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        deactivate(e.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent e) {
        deactivate(e.getPlayer());
    }

    /**
     * Voucher consumption:
     * <ul>
     *   <li>Right-click air/block with an item carrying {@code mcengine_essential:fly_time_add=1} and {@code mcengine_essential:fly_time}.</li>
     *   <li>Adds the encoded seconds to the player's remaining duration and consumes one item.</li>
     * </ul>
     */
    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (p == null) return;

        // Prefer main hand; Paper will also fire for off-hand depending on version, but this is sufficient for common use.
        if (e.getItem() == null) return;

        Integer secs = FlyItem.readSeconds(e.getItem());
        if (secs == null) return; // not a voucher

        // Prevent default use (e.g., head placement) and consume our voucher
        e.setCancelled(true);

        // Safety: ensure row, then add time
        try {
            flyDB.ensurePlayerRow(p.getUniqueId());
            int current = Math.max(0, flyDB.getDuration(p.getUniqueId()));
            int updated = current + Math.max(0, secs);
            flyDB.setDuration(p.getUniqueId(), updated);

            // Consume exactly one item from the stack
            int amount = e.getItem().getAmount();
            if (amount <= 1) {
                // remove from hand
                if (p.getInventory().getItemInMainHand().equals(e.getItem())) {
                    p.getInventory().setItemInMainHand(null);
                } else if (p.getInventory().getItemInOffHand().equals(e.getItem())) {
                    p.getInventory().setItemInOffHand(null);
                } else {
                    // fallback, reduce anyway
                    e.getItem().setAmount(0);
                }
            } else {
                e.getItem().setAmount(amount - 1);
            }

            p.sendMessage("§aRedeemed voucher. §7Added: §e" + FlyDuration.formatDuration(secs) +
                    " §7→ New remaining: §e" + FlyDuration.formatDuration(updated) + "§7.");
        } catch (Exception ex) {
            logger.warning("Failed to redeem fly voucher: " + ex.getMessage());
        }
    }

    /** Deactivates flight for the given player and cancels their task (with partial deduction + message). */
    private void deactivate(Player p) {
        UUID uuid = p.getUniqueId();
        // Disable flight and cancel that player's task; count partial elapsed time and inform player
        flyDuration.deactivate(uuid, true, true);
    }
}
