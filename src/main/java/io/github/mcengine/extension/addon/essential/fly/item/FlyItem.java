package io.github.mcengine.extension.addon.essential.fly.item;

import io.github.mcengine.extension.addon.essential.fly.util.FlyDuration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory and utilities for "Fly Time" consumable items.
 * <p>
 * Items carry two PDC entries under the {@code mcengine_essential} namespace:
 * <ul>
 *   <li>{@code fly_time_add} — a marker (int=1) indicating the item grants fly time when used.</li>
 *   <li>{@code fly_time} — the amount of time (in seconds, int) to grant on use.</li>
 * </ul>
 * <p>
 * Notes:
 * <ul>
 *   <li>Uses {@link NamespacedKey#fromString(String)} with string namespaces (no Plugin instance needed).</li>
 *   <li>HeadDatabase is optional; when present, items can be created as a player head with a given HDB id.</li>
 * </ul>
 */
public final class FlyItem {

    /** Namespace + keys (string-based). */
    public static final NamespacedKey KEY_MARKER = NamespacedKey.fromString("mcengine_essential:fly_time_add");
    public static final NamespacedKey KEY_SECONDS = NamespacedKey.fromString("mcengine_essential:fly_time");

    /** Hidden ctor. */
    private FlyItem() {}

    /**
     * Create a Paper {@link Material#PAPER} voucher that adds the given seconds when consumed.
     *
     * @param seconds positive number of seconds to grant
     * @return item stack with PDC marker and amount encoded
     */
    public static ItemStack createPaperVoucher(int seconds) {
        ItemStack stack = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§eFly Time Voucher");
            List<String> lore = new ArrayList<>();
            lore.add("§7Right-click to add:");
            lore.add("§b" + FlyDuration.formatDuration(Math.max(0, seconds)));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (KEY_MARKER != null) pdc.set(KEY_MARKER, PersistentDataType.INTEGER, 1);
            if (KEY_SECONDS != null) pdc.set(KEY_SECONDS, PersistentDataType.INTEGER, Math.max(0, seconds));

            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Create a HeadDatabase-based head voucher if HeadDatabase is available.
     * Falls back to {@link #createPaperVoucher(int)} if API is missing or fails.
     *
     * @param hdbId   HeadDatabase id string (e.g., "12345" or "MHF_..."), passed to {@code HeadDatabaseAPI#getItemHead(String)}
     * @param seconds positive number of seconds to grant
     * @return item stack (head when possible, otherwise paper)
     */
    public static ItemStack createHdbVoucher(String hdbId, int seconds) {
        try {
            // Check if the plugin is present first
            if (Bukkit.getPluginManager().getPlugin("HeadDatabase") == null) {
                return createPaperVoucher(seconds);
            }

            // Load API without compile-time dependency: me.arcaniax.hdb.api.HeadDatabaseAPI
            Class<?> apiCls = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            Constructor<?> ctor = apiCls.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object api = ctor.newInstance();
            Method getItemHead = apiCls.getMethod("getItemHead", String.class);
            Object headObj = getItemHead.invoke(api, hdbId);
            if (!(headObj instanceof ItemStack headStack)) {
                return createPaperVoucher(seconds);
            }

            ItemMeta meta = headStack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§eFly Time Voucher (Head)");
                List<String> lore = new ArrayList<>();
                lore.add("§7Right-click to add:");
                lore.add("§b" + FlyDuration.formatDuration(Math.max(0, seconds)));
                lore.add("§7HDB: §f" + hdbId);
                meta.setLore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                if (KEY_MARKER != null) pdc.set(KEY_MARKER, PersistentDataType.INTEGER, 1);
                if (KEY_SECONDS != null) pdc.set(KEY_SECONDS, PersistentDataType.INTEGER, Math.max(0, seconds));

                headStack.setItemMeta(meta);
            }
            return headStack;
        } catch (Throwable t) {
            // Any failure ⇒ fallback to paper voucher
            return createPaperVoucher(seconds);
        }
    }

    /**
     * Helper to read the encoded seconds from an item.
     *
     * @param stack item possibly containing voucher PDC
     * @return seconds (>= 0) when present; otherwise null
     */
    public static Integer readSeconds(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Integer marker = KEY_MARKER == null ? null : pdc.get(KEY_MARKER, PersistentDataType.INTEGER);
        if (marker == null || marker != 1) return null;

        Integer secs = KEY_SECONDS == null ? null : pdc.get(KEY_SECONDS, PersistentDataType.INTEGER);
        if (secs == null || secs < 0) return null;
        return secs;
    }
}
