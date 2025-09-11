package io.github.mcengine.extension.addon.essential.fly.listener;

import io.github.mcengine.api.core.extension.logger.MCEngineExtensionLogger;
import io.github.mcengine.extension.addon.essential.fly.database.FlyDB;
import io.github.mcengine.extension.addon.essential.fly.util.FlyDuration;
import org.bukkit.Bukkit;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

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

    /** Plugin reference for scheduling tasks. */
    private final Plugin plugin;

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
     * @param plugin      Owning plugin for task scheduling.
     */
    public FlyListener(MCEngineExtensionLogger logger, FlyDB flyDB, FlyDuration flyDuration, Plugin plugin) {
        this.logger = logger;
        this.flyDB = flyDB;
        this.flyDuration = flyDuration;
        this.plugin = plugin;
    }

    /** Ensure the player has a DB row (with default 0) on join. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        try {
            flyDB.ensurePlayerRow(e.getPlayer().getUniqueId());
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
     *   <li>Right-click anywhere (air or block) with an item carrying
     *       {@code mcengine_essential:fly_time_add=1} and {@code mcengine_essential:fly_time} (seconds).</li>
     *   <li>Adds the encoded seconds to the player's remaining duration and consumes one item.</li>
     *   <li>Only processes MAIN HAND to prevent double-firing with off-hand.</li>
     * </ul>
     */
    @EventHandler // do NOT ignore cancelled; air-clicks can be cancelled upstream
    public void onRightClick(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Only process MAIN HAND to avoid double-fire with OFF_HAND
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player p = e.getPlayer();
        if (p == null) return;

        // Use the item provided by the event (the actual used hand item)
        ItemStack hand = e.getItem();
        if (hand == null || hand.getType().isAir() || !hand.hasItemMeta()) return;

        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Integer marker = KEY_MARKER == null ? null : pdc.get(KEY_MARKER, PersistentDataType.INTEGER);
        if (marker == null || marker != 1) return;

        Integer secs = KEY_SECONDS == null ? null : pdc.get(KEY_SECONDS, PersistentDataType.INTEGER);
        if (secs == null || secs <= 0) return;

        // Prevent default behavior (like placing a head) immediately on main thread
        e.setCancelled(true);

        // Snapshot data for async task
        final UUID uuid = p.getUniqueId();
        final int addSeconds = secs;

        // Do DB work asynchronously to avoid blocking the main thread
        new BukkitRunnable() {
            @Override
            public void run() {
                int updated;
                try {
                    flyDB.ensurePlayerRow(uuid);
                    int current = Math.max(0, flyDB.getDuration(uuid));
                    updated = current + addSeconds;
                    flyDB.setDuration(uuid, updated);
                } catch (Exception ex) {
                    logger.warning("Failed to redeem fly voucher (DB): " + ex.getMessage());
                    return;
                }

                // Apply inventory change and send messages back on the main thread
                final int updatedFinal = updated;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player == null || !player.isOnline()) return;

                        // Consume exactly one from MAIN HAND if still holding the voucher item
                        ItemStack currentInHand = player.getInventory().getItemInMainHand();
                        if (currentInHand != null && currentInHand.hasItemMeta()) {
                            ItemMeta cm = currentInHand.getItemMeta();
                            PersistentDataContainer cpdc = cm.getPersistentDataContainer();
                            Integer cmMarker = KEY_MARKER == null ? null : cpdc.get(KEY_MARKER, PersistentDataType.INTEGER);
                            Integer cmSecs = KEY_SECONDS == null ? null : cpdc.get(KEY_SECONDS, PersistentDataType.INTEGER);

                            // Only consume if it still looks like the same kind of voucher
                            if (cmMarker != null && cmMarker == 1 && cmSecs != null && cmSecs == addSeconds) {
                                int amount = currentInHand.getAmount();
                                if (amount <= 1) {
                                    player.getInventory().setItemInMainHand(null);
                                } else {
                                    currentInHand.setAmount(amount - 1);
                                    player.getInventory().setItemInMainHand(currentInHand);
                                }
                            }
                        }

                        player.sendMessage("§aRedeemed voucher. §7Added: §e" +
                                FlyDuration.formatDuration(addSeconds) +
                                " §7→ New remaining: §e" +
                                FlyDuration.formatDuration(updatedFinal) + "§7.");
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    /** Deactivates flight for the given player and cancels their task (with partial deduction + message). */
    private void deactivate(Player p) {
        UUID uuid = p.getUniqueId();
        flyDuration.deactivate(uuid, true, true);
    }
}
