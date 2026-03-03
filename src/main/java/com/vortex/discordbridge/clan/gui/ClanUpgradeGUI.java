package com.vortex.discordbridge.clan.gui;

import com.vortex.discordbridge.DiscordBridge;
import com.vortex.discordbridge.clan.Clan;
import com.vortex.discordbridge.clan.ClanUpgrade;
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

public class ClanUpgradeGUI implements Listener {

    private final DiscordBridge plugin;
    private static final String TITULO = "§6§lMejoras del Clan";

    public ClanUpgradeGUI(DiscordBridge plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void abrir(Player player, Clan clan) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITULO).decoration(TextDecoration.ITALIC, false));

        var cfg = plugin.getClanManager().getClanConfig();

        // Slot 10 — Zona grande
        int nivelZona = clan.banner != null ? clan.banner.nivelZona : 0;
        var precios = cfg.getDoubleList("clan.estandarte.mejoras.zona-grande.precios");
        String zonaDesc = nivelZona >= 2 ? "§aMAX" :
                "§7Siguiente nivel: §e" + plugin.getClanManager()
                        .formatear(precios.get(nivelZona));
        inv.setItem(10, crearItem(Material.GRASS_BLOCK,
                "§e§lZona de Protección",
                List.of(
                        "§7Nivel actual: §6" + nivelZona + "§7/§62",
                        "§7Radio actual: §e" + (clan.banner != null ? clan.banner.radio : 10) + " bloques",
                        zonaDesc
                ), nivelZona >= 2
        ));

        // Slot 12 — Slots de miembros
        double precioSlot = cfg.getDouble("clan.precio-slot-miembro", 500.0) *
                Math.pow(cfg.getDouble("clan.precio-slot-multiplicador", 1.5),
                        clan.slotsComprados);
        inv.setItem(12, crearItem(Material.PLAYER_HEAD,
                "§e§lSlot de Miembro",
                List.of(
                        "§7Máximo actual: §6" + clan.maxMiembros,
                        "§7Precio siguiente slot: §e" +
                                plugin.getClanManager().formatear(precioSlot)
                ), false
        ));

        // Slot 14 — Mejora vidas auxiliares
        boolean tieneVidas = clan.banner != null && clan.banner.tieneVidasAuxiliares;
        double precioMejoraVidas = cfg.getDouble("clan.vidas-auxiliares.precio-mejora", 3000.0);
        double precioPorVida = cfg.getDouble("clan.vidas-auxiliares.precio-por-vida", 500.0);
        inv.setItem(14, crearItem(Material.HEART_OF_THE_SEA,
                "§e§lDesbloquear Vidas Auxiliares",
                List.of(
                        tieneVidas ? "§aDesbloqueado" : "§cNo desbloqueado",
                        "§7Precio mejora: §e" + plugin.getClanManager().formatear(precioMejoraVidas),
                        "§7Precio por vida: §e" + plugin.getClanManager().formatear(precioPorVida),
                        "§7Stock actual: §e" + clan.vidasAuxiliares
                ), tieneVidas
        ));

        // Slot 16 — Comprar vida auxiliar (solo si tiene mejora)
        if (tieneVidas) {
            inv.setItem(16, crearItem(Material.NETHER_STAR,
                    "§e§lComprar Vida Auxiliar",
                    List.of(
                            "§7Stock actual: §e" + clan.vidasAuxiliares,
                            "§7Precio: §e" + plugin.getClanManager().formatear(precioPorVida),
                            "§eClick §7para comprar 1"
                    ), false
            ));
        }

        // Slot 28 — Explosiones
        boolean tieneExp = clan.banner != null && clan.banner.tieneExplosiones;
        double precioExp = cfg.getDouble("clan.estandarte.mejoras.explosiones.precio", 2000.0);
        inv.setItem(28, crearItem(Material.TNT,
                "§e§lProtección Explosiones",
                List.of(
                        tieneExp ? "§aDesbloqueado" : "§cNo desbloqueado",
                        tieneExp ? "" : "§7Precio: §e" + plugin.getClanManager().formatear(precioExp)
                ), tieneExp
        ));

        // Slot 30 — PvP
        boolean tienePvp = clan.banner != null && clan.banner.tienePvp;
        double precioPvp = cfg.getDouble("clan.estandarte.mejoras.pvp.precio", 1500.0);
        inv.setItem(30, crearItem(Material.DIAMOND_SWORD,
                "§e§lProtección PvP",
                List.of(
                        tienePvp ? "§aDesbloqueado" : "§cNo desbloqueado",
                        tienePvp ? "" : "§7Precio: §e" + plugin.getClanManager().formatear(precioPvp)
                ), tienePvp
        ));

        // Slot 32 — Interacciones
        boolean tieneInt = clan.banner != null && clan.banner.tieneInteracciones;
        double precioInt = cfg.getDouble("clan.estandarte.mejoras.interacciones.precio", 1500.0);
        inv.setItem(32, crearItem(Material.CHEST,
                "§e§lProtección Interacciones",
                List.of(
                        tieneInt ? "§aDesbloqueado" : "§cNo desbloqueado",
                        tieneInt ? "" : "§7Precio: §e" + plugin.getClanManager().formatear(precioInt)
                ), tieneInt
        ));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(
                Component.text(TITULO).decoration(TextDecoration.ITALIC, false))) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        Clan clan = plugin.getClanManager().getClanDeJugador(player.getUniqueId());
        if (clan == null) return;

        var cm = plugin.getClanManager();

        switch (event.getSlot()) {
            case 10 -> cm.comprarMejoraZona(player, clan);
            case 12 -> cm.comprarSlotMiembro(player, clan);
            case 14 -> cm.comprarMejora(player, clan, ClanUpgrade.VIDAS_AUXILIARES);
            case 16 -> cm.comprarVidasAuxiliares(player, clan, 1);
            case 28 -> cm.comprarMejora(player, clan, ClanUpgrade.EXPLOSIONES);
            case 30 -> cm.comprarMejora(player, clan, ClanUpgrade.PVP);
            case 32 -> cm.comprarMejora(player, clan, ClanUpgrade.INTERACCIONES);
        }

        // Refrescar GUI
        Bukkit.getScheduler().runTaskLater(plugin, () -> abrir(player, clan), 1L);
    }

    private ItemStack crearItem(Material mat, String nombre,
                                List<String> lore, boolean comprado) {
        ItemStack item = new ItemStack(comprado ? Material.LIME_STAINED_GLASS_PANE : mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(nombre)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(l ->
                Component.text(l).decoration(TextDecoration.ITALIC, false)
        ).toList());
        item.setItemMeta(meta);
        return item;
    }
}