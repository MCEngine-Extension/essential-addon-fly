package io.github.mcengine.extension.addon.essential.fly.listener;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import io.github.mcengine.extension.addon.essential.fly.util.FlyDuration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Listener that:
 * <ul>
 *   <li>Ensures a default Fly DB record (duration 0) is present when a player joins.</li>
 *   <li>On quit/kick, disables flight, cancels that player's task, and
 *       subtracts partial elapsed time since the last tick using {@code lastTickMillis}.</li>
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

    /** Namespace keys for voucher items. */
    private static final NamespacedKey KEY_MARKER =
            NamespacedKey.fromString("mcengine_essential:fly_time_add");
    private static final NamespacedKey KEY_SECONDS =
            NamespacedKey.fromString("mcengine_essential:fly_time");

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
     *   <li>Right-click air/block with an item carrying {@code mcengine_essential:fly_time_add=1}
     *       and {@code mcengine_essential:fly_time} (seconds).</li>
     *   <li>Adds the encoded seconds to the player's remaining duration and consumes one item.</li>
     *   <li>Only processes MAIN HAND to prevent double-firing with off-hand.</li>
     * </ul>
     */
    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        // Process only MAIN HAND to avoid duplicate redemption from OFF_HAND
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player p = e.getPlayer();
        if (p == null) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || !hand.hasItemMeta()) return;

        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Check voucher marker
        Integer marker = KEY_MARKER == null ? null : pdc.get(KEY_MARKER, PersistentDataType.INTEGER);
        if (marker == null || marker != 1) return;

        // Get exact seconds from namespace
        Integer secs = KEY_SECONDS == null ? null : pdc.get(KEY_SECONDS, PersistentDataType.INTEGER);
        if (secs == null || secs <= 0) return;

        // Prevent default use (e.g., head placement) and consume our voucher
        e.setCancelled(true);

        try {
            flyDB.ensurePlayerRow(p.getUniqueId());
            int current = Math.max(0, flyDB.getDuration(p.getUniqueId()));
            int updated = current + secs;
            flyDB.setDuration(p.getUniqueId(), updated);

            // Consume one item
            int amount = hand.getAmount();
            if (amount <= 1) {
                p.getInventory().setItemInMainHand(null);
            } else {
                hand.setAmount(amount - 1);
                p.getInventory().setItemInMainHand(hand);
            }

            p.sendMessage("§aRedeemed voucher. §7Added: §e" +
                    FlyDuration.formatDuration(secs) +
                    " §7→ New remaining: §e" +
                    FlyDuration.formatDuration(updated) + "§7.");
        } catch (Exception ex) {
            logger.warning("Failed to redeem fly voucher: " + ex.getMessage());
        }
    }

    /** Deactivates flight for the given player and cancels their task (with partial deduction + message). */
    private void deactivate(Player p) {
        UUID uuid = p.getUniqueId();
        flyDuration.deactivate(uuid, true, true);
    }
}
