package com.vortex.discordbridge.clan.gui;

import com.vortex.discordbridge.DiscordBridge;
import com.vortex.discordbridge.clan.Clan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class ClanPrefixGUI implements Listener {

    private final DiscordBridge plugin;
    private static final String TITULO = "§6§lPrefijos del Clan";

    public ClanPrefixGUI(DiscordBridge plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void abrir(Player player, Clan clan) {
        var prefijos = plugin.getClanManager().getClanConfig().getMapList("clan.prefijos");
        int size = Math.max(9, ((prefijos.size() / 9) + 1) * 9);
        Inventory inv = Bukkit.createInventory(null, size,
                Component.text(TITULO).decoration(TextDecoration.ITALIC, false));

        for (int i = 0; i < prefijos.size(); i++) {
            Map<?, ?> p = prefijos.get(i);
            String id = (String) p.get("id");
            String display = (String) p.get("display");
            double precio = ((Number) p.get("precio")).doubleValue();
            boolean esActual = id.equals(clan.prefijoid);

            ItemStack item = new ItemStack(esActual ?
                    Material.LIME_BANNER : Material.WHITE_BANNER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(display + " §7— " + id)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(esActual ? "§aActivo" : "§7Precio: §e" +
                                    plugin.getClanManager().formatear(precio))
                            .decoration(TextDecoration.ITALIC, false)
            ));
            // Guardar ID del prefijo en NBT del item
            var nbt = meta.getPersistentDataContainer();
            nbt.set(new org.bukkit.NamespacedKey(plugin, "prefijo_id"),
                    org.bukkit.persistence.PersistentDataType.STRING, id);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(
                Component.text(TITULO).decoration(TextDecoration.ITALIC, false))) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;

        var nbt = event.getCurrentItem().getItemMeta().getPersistentDataContainer();
        var key = new org.bukkit.NamespacedKey(plugin, "prefijo_id");
        if (!nbt.has(key, org.bukkit.persistence.PersistentDataType.STRING)) return;

        String prefijoId = nbt.get(key, org.bukkit.persistence.PersistentDataType.STRING);
        Clan clan = plugin.getClanManager().getClanDeJugador(player.getUniqueId());
        if (clan == null) return;

        boolean comprado = plugin.getClanManager().comprarPrefijo(player, clan, prefijoId);
        if (comprado) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> abrir(player, clan), 1L);
        }
    }
}