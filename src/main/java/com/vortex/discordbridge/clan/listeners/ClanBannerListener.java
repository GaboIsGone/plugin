package com.vortex.discordbridge.clan.listeners;

import com.vortex.discordbridge.DiscordBridge;
import com.vortex.discordbridge.clan.Clan;
import com.vortex.discordbridge.clan.ClanBanner;
import com.vortex.discordbridge.clan.ClanMember;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class ClanBannerListener implements Listener {

    private final DiscordBridge plugin;

    private final com.vortex.discordbridge.protection.ProtectionBorderManager borderManager =
            new com.vortex.discordbridge.protection.ProtectionBorderManager();
    private static final int DISTANCIA_AVISO = 7;

    private final java.util.Map<java.util.UUID, java.util.Set<String>> zonasClanActuales =
            new java.util.HashMap<>();

    public ClanBannerListener(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    // ─── COLOCAR ESTANDARTE ───────────────────────────────────────────────
    @EventHandler
    public void onBannerPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!esBannerItem(block.getType())) return;
        if (!tieneNBTClan(event.getItemInHand())) return;

        Clan clan = plugin.getClanManager().getClanDeJugador(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cNo perteneces a ningún clan.");
            event.setCancelled(true);
            return;
        }

        ClanMember miembro = clan.getMiembro(player.getUniqueId());
        if (!miembro.esLider() && !miembro.esColider()) {
            player.sendMessage("§cSolo el líder o co-líder puede colocar el estandarte.");
            event.setCancelled(true);
            return;
        }

        if (clan.banner != null && clan.banner.colocado) {
            player.sendMessage("§cEl clan ya tiene un estandarte colocado. Rómpelo primero.");
            event.setCancelled(true);
            return;
        }

        String world = block.getWorld().getName();
        int x = block.getX(), y = block.getY(), z = block.getZ();

        // intentarColocarBanner devuelve null si OK, o el mensaje de error
        String error = plugin.getClanManager().intentarColocarBanner(clan, world, x, y, z);
        if (error != null) {
            player.sendMessage(error);
            event.setCancelled(true);
            return;
        }

        player.sendMessage("§a§l[Clan] §eEstandarte del clan §6" + clan.nombre +
                " §ecolocado. Radio: §6" + clan.banner.radio + " §ebloques.");
    }

    // ─── ROMPER ESTANDARTE ────────────────────────────────────────────────
    @EventHandler
    public void onBannerBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!esBanner(block)) return;

        String world = block.getWorld().getName();
        Clan clan = plugin.getClanManager()
                .getClanBannerEnPosicion(world, block.getX(), block.getY(), block.getZ());
        if (clan == null) return;

        ClanMember miembro = clan.getMiembro(player.getUniqueId());
        boolean esAdmin = player.hasPermission("discordbridge.clan.admin");

        if (!esAdmin && (miembro == null || !miembro.esLider())) {
            event.setCancelled(true);
            player.sendMessage("§cSolo el líder puede romper el estandarte del clan.");
            return;
        }

        // Devolver item de estandarte
        event.setDropItems(false);
        player.getInventory().addItem(crearItemEstandarte());
        clan.banner.colocado = false;
        plugin.getClanManager().guardarBannerPublico(clan);
        player.sendMessage("§a§l[Clan] §eEstandarte recogido.");
    }

    // ─── ROMPER BLOQUES EN ZONA ───────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player.hasPermission("discordbridge.clan.admin")) return;

        Clan clan = plugin.getClanManager().getClanEnBanner(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ()
        );
        if (clan == null || clan.banner == null) return;

        // No volver a procesar el estandarte (ya lo maneja el listener de arriba)
        if (clan.banner.x == block.getX() && clan.banner.y == block.getY() &&
                clan.banner.z == block.getZ()) return;

        ClanBanner.EstadoProteccion estado = clan.getEstadoProteccion(plugin.getVidasManager());

        if (!clan.banner.protegeContraBloques(estado)) return;
        if (clan.tieneMiembro(player.getUniqueId())) return;

        event.setCancelled(true);
        player.sendMessage("§cEsta zona está protegida por el clan §6" + clan.nombre + "§c.");
    }

    // ─── INTERACCIONES ────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (player.hasPermission("discordbridge.clan.admin")) return;

        Block block = event.getClickedBlock();
        var mat = block.getType();

        boolean esInteraccion =
                mat.name().contains("CHEST") || mat.name().contains("BARREL") ||
                        mat.name().contains("FURNACE") || mat.name().contains("DOOR") ||
                        mat.name().contains("GATE") || mat.name().contains("TRAPDOOR") ||
                        mat.name().contains("BUTTON") || mat.name().contains("HOPPER") ||
                        mat.name().contains("DROPPER") || mat.name().contains("DISPENSER") ||
                        mat == Material.CRAFTING_TABLE || mat == Material.ENCHANTING_TABLE;

        if (!esInteraccion) return;

        // Click en el estandarte — mostrar info
        if (esBanner(block)) {
            Clan clan = plugin.getClanManager().getClanBannerEnPosicion(
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ()
            );
            if (clan != null) {
                mostrarInfoClan(player, clan);
                event.setCancelled(true);
                return;
            }
        }

        Clan clan = plugin.getClanManager().getClanEnBanner(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ()
        );
        if (clan == null || clan.banner == null) return;

        ClanBanner.EstadoProteccion estado = clan.getEstadoProteccion(plugin.getVidasManager());
        if (!clan.banner.protegeContraInteracciones(estado)) return;
        if (clan.tieneMiembro(player.getUniqueId())) return;

        event.setCancelled(true);
        player.sendMessage("§cEsta zona está protegida por el clan §6" + clan.nombre + "§c.");
    }

    // ─── PVP ──────────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (attacker.hasPermission("discordbridge.clan.admin")) return;

        var loc = victim.getLocation();
        Clan clan = plugin.getClanManager().getClanEnBanner(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
        );
        if (clan == null || clan.banner == null) return;

        ClanBanner.EstadoProteccion estado = clan.getEstadoProteccion(plugin.getVidasManager());
        if (!clan.banner.protegeContraPvP(estado)) return;

        event.setCancelled(true);
        attacker.sendMessage("§cNo puedes atacar jugadores en la zona del clan §6" +
                clan.nombre + "§c.");
    }

    // ─── EXPLOSIONES ──────────────────────────────────────────────────────
    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            Clan clan = plugin.getClanManager().getClanEnBanner(
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ()
            );
            if (clan == null || clan.banner == null) return false;
            ClanBanner.EstadoProteccion estado = clan.getEstadoProteccion(plugin.getVidasManager());
            return clan.banner.protegeContraExplosiones(estado);
        });
    }

    @EventHandler
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            Clan clan = plugin.getClanManager().getClanEnBanner(
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ()
            );
            if (clan == null || clan.banner == null) return false;
            ClanBanner.EstadoProteccion estado = clan.getEstadoProteccion(plugin.getVidasManager());
            return clan.banner.protegeContraExplosiones(estado);
        });
    }

    // ─── FLUIDOS ──────────────────────────────────────────────────────────
    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("discordbridge.clan.admin")) return;

        Block block = event.getBlockClicked().getRelative(event.getBlockFace());
        Clan clan = plugin.getClanManager().getClanEnBanner(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ()
        );
        if (clan == null || clan.banner == null) return;
        if (clan.tieneMiembro(player.getUniqueId())) return;

        ClanBanner.EstadoProteccion estado = clan.getEstadoProteccion(plugin.getVidasManager());
        if (clan.banner.protegeContraBloques(estado)) {
            event.setCancelled(true);
            player.sendMessage("§cNo puedes colocar fluidos en la zona del clan §6" +
                    clan.nombre + "§c.");
        }
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        Block to = event.getToBlock();
        Clan clan = plugin.getClanManager().getClanEnBanner(
                to.getWorld().getName(), to.getX(), to.getY(), to.getZ()
        );
        if (clan == null || clan.banner == null) return;
        ClanBanner.EstadoProteccion estado = clan.getEstadoProteccion(plugin.getVidasManager());
        if (clan.banner.protegeContraBloques(estado)) event.setCancelled(true);
    }

    @EventHandler
    public void onMoveClanZona(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();
        String world = player.getWorld().getName();

        java.util.Set<String> antes = zonasClanActuales.getOrDefault(
                uuid, new java.util.HashSet<>());
        java.util.Set<String> ahora = new java.util.HashSet<>();

        for (var clan : plugin.getClanManager().getTodosLosClanes()) {
            if (clan.banner == null || !clan.banner.colocado) continue;
            if (clan.banner.contiene(world,
                    event.getTo().getBlockX(),
                    event.getTo().getBlockY(),
                    event.getTo().getBlockZ())) {
                ahora.add(clan.id);
            }
        }

        zonasClanActuales.put(uuid, ahora);

        // Entró a una zona
        for (String id : ahora) {
            if (antes.contains(id)) continue;
            var clan = plugin.getClanManager().getClan(id);
            if (clan == null) continue;
            player.sendMessage("§a§l[Clan] §eEntraste a la zona del clan §6" +
                    clan.nombre + "§e.");
            // Notificar al líder/co-líderes online si no es miembro
            if (!clan.tieneMiembro(uuid)) {
                clan.miembros.values().stream()
                        .filter(m -> m.tienePermisoStaff())
                        .forEach(m -> {
                            Player staff = org.bukkit.Bukkit.getPlayer(m.uuid);
                            if (staff != null && !staff.equals(player))
                                staff.sendMessage("§e§l[Clan] §6" + player.getName() +
                                        " §eentró a la zona del clan.");
                        });
            }
        }

        // Salió de una zona
        for (String id : antes) {
            if (ahora.contains(id)) continue;
            var clan = plugin.getClanManager().getClan(id);
            if (clan == null) continue;
            player.sendMessage("§c§l[Clan] §eSaliste de la zona del clan §6" +
                    clan.nombre + "§e.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMoveEnCombateClan(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();

        if (!plugin.getPvpManagerHook().estaEnCombate(player)) {
            if (borderManager.tieneBorde(player.getUniqueId())) {
                borderManager.quitarBorde(player);
            }
            return;
        }
        if (player.hasPermission("discordbridge.clan.admin")) return;

        String world = event.getTo().getWorld().getName();
        int x = event.getTo().getBlockX();
        int y = event.getTo().getBlockY();
        int z = event.getTo().getBlockZ();

        for (var clan : plugin.getClanManager().getTodosLosClanes()) {
            if (clan.banner == null || !clan.banner.colocado) continue;
            if (!clan.banner.worldName.equals(world)) continue;
            if (clan.tieneMiembro(player.getUniqueId())) continue;

            int distancia = Math.max(
                    Math.max(Math.abs(x - clan.banner.x), Math.abs(y - clan.banner.y)),
                    Math.abs(z - clan.banner.z)
            );

            if (distancia <= clan.banner.radio + DISTANCIA_AVISO && distancia > clan.banner.radio) {
                borderManager.mostrarBorde(player,
                        clan.banner.x, clan.banner.y, clan.banner.z, clan.banner.radio);
                return;
            }

            if (distancia <= clan.banner.radio) {
                event.setCancelled(true);
                player.sendMessage("§cNo puedes entrar a la zona del clan §6" +
                        clan.nombre + " §cmientras estás en combate.");
                return;
            }
        }

        if (borderManager.tieneBorde(player.getUniqueId())) {
            borderManager.quitarBorde(player);
        }
    }

    @EventHandler
    public void onBannerRotoExplosion(EntityExplodeEvent event) {
        event.blockList().forEach(block -> verificarBannerDestruido(block));
    }

    @EventHandler
    public void onBannerRotoExplosionBloque(BlockExplodeEvent event) {
        event.blockList().forEach(block -> verificarBannerDestruido(block));
    }

    @EventHandler
    public void onBannerRotoNatural(BlockBreakEvent event) {
        verificarBannerDestruido(event.getBlock());
    }

    public void limpiarJugador(java.util.UUID uuid) {
        zonasClanActuales.remove(uuid);
    }

    private void verificarBannerDestruido(org.bukkit.block.Block block) {
        if (!esBanner(block)) return;
        String world = block.getWorld().getName();
        Clan clan = plugin.getClanManager()
                .getClanBannerEnPosicion(world, block.getX(), block.getY(), block.getZ());
        if (clan == null) return;
        clan.banner.colocado = false;
        plugin.getClanManager().guardarBannerPublico(clan);
        org.bukkit.Bukkit.getOnlinePlayers().stream()
                .filter(p -> clan.tieneMiembro(p.getUniqueId()))
                .forEach(p -> p.sendMessage("§c§l[Clan] §eEl estandarte del clan §6" +
                        clan.nombre + " §eha sido destruido."));
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private boolean esBanner(Block block) {
        return block.getType().name().contains("BANNER");
    }

    private boolean esBannerItem(Material mat) {
        return mat.name().contains("BANNER");
    }

    private boolean tieneNBTClan(org.bukkit.inventory.ItemStack item) {
        if (!item.hasItemMeta()) return false;
        var nbt = item.getItemMeta().getPersistentDataContainer();
        var key = new org.bukkit.NamespacedKey(plugin, "clan_banner");
        return nbt.has(key, org.bukkit.persistence.PersistentDataType.BOOLEAN);
    }

    private org.bukkit.inventory.ItemStack crearItemEstandarte() {
        var item = new org.bukkit.inventory.ItemStack(Material.WHITE_BANNER);
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§6§lEstandarte de Clan")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                net.kyori.adventure.text.Component.text("§7Coloca este estandarte para")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                net.kyori.adventure.text.Component.text("§7activar la protección del clan.")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ));
        var nbt = meta.getPersistentDataContainer();
        nbt.set(new org.bukkit.NamespacedKey(plugin, "clan_banner"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    private void mostrarInfoClan(Player player, Clan clan) {
        ClanBanner.EstadoProteccion estado = clan.getEstadoProteccion(plugin.getVidasManager());
        int enAbismo = clan.getMiembrosEnAbismo(plugin.getVidasManager());
        player.sendMessage("§6§l[Clan] §eInformación:");
        player.sendMessage("§7Nombre: §6" + clan.nombre);
        player.sendMessage("§7Prefijo: §6" + (clan.prefijoDisplay != null ?
                clan.prefijoDisplay : "Ninguno"));
        player.sendMessage("§7Miembros: §e" + clan.miembros.size() +
                "§7/§e" + clan.maxMiembros);
        player.sendMessage("§7En abismo: §c" + enAbismo);
        player.sendMessage("§7Estado: " + switch (estado) {
            case COMPLETA -> "§a✔ Protección completa";
            case PARCIAL -> "§e⚠ Protección parcial (>50% en abismo)";
            case VULNERABLE -> "§c✘ Vulnerable (>75% en abismo)";
            case SIN_ESTANDARTE -> "§7— Sin estandarte colocado";
        });
        player.sendMessage("§7Radio: §e" + clan.banner.radio + " bloques");
    }
}