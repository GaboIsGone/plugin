package com.vortex.discordbridge.protection.listeners;

import com.vortex.discordbridge.DiscordBridge;
import com.vortex.discordbridge.protection.ProtectionRegion;
import com.vortex.discordbridge.protection.StoneType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

public class ProtectionListener implements Listener {

    private final DiscordBridge plugin;
    private final java.util.Map<java.util.UUID, java.util.Set<String>> regionesActuales =
            new java.util.HashMap<>();

    private final com.vortex.discordbridge.protection.ProtectionBorderManager borderManager =
            new com.vortex.discordbridge.protection.ProtectionBorderManager();
    private static final int DISTANCIA_AVISO = 7;

    public ProtectionListener(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        StoneType stoneType = plugin.getProtectionManager()
                .detectarTipoPiedra(event.getItemInHand());
        if (stoneType == null) return;

        // Verificar permiso para usar este tipo de piedra
        if (!stoneType.permiso.isEmpty() && !player.hasPermission(stoneType.permiso)
                && !player.hasPermission("discordbridge.ps.admin")) {
            event.setCancelled(true);
            player.sendMessage("§cNo tienes permiso para usar §e" +
                    stoneType.displayName + "§c.");
            return;
        }

        int x = event.getBlock().getX();
        int y = event.getBlock().getY();
        int z = event.getBlock().getZ();
        String world = event.getBlock().getWorld().getName();

        boolean creado = plugin.getProtectionManager()
                .crearRegion(player, world, x, y, z, stoneType);

        if (creado) {
            player.sendMessage("§a§l[PS] §eProtección §6" + stoneType.displayName +
                    " §ecreada con radio §6" + stoneType.radio + " §ebloques.");
            player.sendMessage("§7Usa §e/ps ayuda §7para ver los comandos disponibles.");
        } else {
            event.setCancelled(true);
        }
    }

    // ─── ROMPER PIEDRA ────────────────────────────────────────────────────
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        int x = event.getBlock().getX();
        int y = event.getBlock().getY();
        int z = event.getBlock().getZ();
        String world = event.getBlock().getWorld().getName();

        // Verificar si es una protection stone
        ProtectionRegion region = plugin.getProtectionManager()
                .getRegionEnPosicion(world, x, y, z);

        if (region != null) {
            if (!region.ownerUUID.equals(player.getUniqueId()) &&
                    !player.hasPermission("discordbridge.ps.admin")) {
                event.setCancelled(true);
                player.sendMessage("§cNo puedes romper esta protection stone.");
                return;
            }
            // Dueño la rompe — eliminar protección
            plugin.getProtectionManager().eliminarRegion(region.id);
            StoneType st = plugin.getProtectionManager().getStoneType(region.stoneTypeId);
            if (st != null) {
                org.bukkit.inventory.ItemStack itemDevuelto =
                        plugin.getProtectionManager().crearItemPiedra(st);
                player.getInventory().addItem(itemDevuelto);
                event.setDropItems(false);
            }
            player.sendMessage("§a§l[PS] §eProtección eliminada.");
            return;
        }

        // Verificar si el bloque está dentro de una protección
        ProtectionRegion regionContenedora = plugin.getProtectionManager()
                .getRegionQueContiene(world, x, y, z);

        if (regionContenedora == null) return;
        if (regionContenedora.esMiembro(player.getUniqueId())) return;
        if (player.hasPermission("discordbridge.ps.admin")) return;

        event.setCancelled(true);
        player.sendMessage("§cEsta zona está protegida por §e" +
                regionContenedora.ownerName + "§c.");
    }

    // ─── INTERACCIONES (cofres, puertas, etc.) ────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();

        var block = event.getClickedBlock();
        var mat = block.getType();

        // Solo bloquear interacciones con contenedores y puertas
        boolean esInteraccionProtegida =
                mat.name().contains("CHEST") ||
                        mat.name().contains("BARREL") ||
                        mat.name().contains("SHULKER") ||
                        mat.name().contains("FURNACE") ||
                        mat.name().contains("HOPPER") ||
                        mat.name().contains("DROPPER") ||
                        mat.name().contains("DISPENSER") ||
                        mat.name().contains("DOOR") ||
                        mat.name().contains("GATE") ||
                        mat.name().contains("TRAPDOOR") ||
                        mat.name().contains("ANVIL") ||
                        mat == org.bukkit.Material.CRAFTING_TABLE ||
                        mat == org.bukkit.Material.ENCHANTING_TABLE ||
                        mat == org.bukkit.Material.ENDER_CHEST;

        if (!esInteraccionProtegida) return;

        String world = block.getWorld().getName();
        ProtectionRegion region = plugin.getProtectionManager()
                .getRegionQueContiene(world, block.getX(), block.getY(), block.getZ());

        if (region == null) return;

        // Click en la propia protection stone — mostrar info
        ProtectionRegion stonePosicion = plugin.getProtectionManager()
                .getRegionEnPosicion(world, block.getX(), block.getY(), block.getZ());

        if (stonePosicion != null) {
            mostrarInfo(player, stonePosicion);
            event.setCancelled(true);
            return;
        }

        if (region.esMiembro(player.getUniqueId())) return;
        if (player.hasPermission("discordbridge.ps.admin")) return;

        event.setCancelled(true);
        player.sendMessage("§cEsta zona está protegida por §e" + region.ownerName + "§c.");
    }

    // ─── EXPLOSIONES ──────────────────────────────────────────────────────
    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            ProtectionRegion region = plugin.getProtectionManager()
                    .getRegionQueContiene(block.getWorld().getName(),
                            block.getX(), block.getY(), block.getZ());
            return region != null;
        });
    }

    @EventHandler
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            ProtectionRegion region = plugin.getProtectionManager()
                    .getRegionQueContiene(block.getWorld().getName(),
                            block.getX(), block.getY(), block.getZ());
            return region != null;
        });
    }

    // ─── FLUIDOS ──────────────────────────────────────────────────────────
    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("discordbridge.ps.admin")) return;

        var block = event.getBlockClicked().getRelative(event.getBlockFace());
        ProtectionRegion region = plugin.getProtectionManager()
                .getRegionQueContiene(block.getWorld().getName(),
                        block.getX(), block.getY(), block.getZ());

        if (region == null) return;
        if (region.esMiembro(player.getUniqueId())) return;

        event.setCancelled(true);
        player.sendMessage("§cNo puedes colocar fluidos en una zona protegida.");
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        var to = event.getToBlock();
        ProtectionRegion region = plugin.getProtectionManager()
                .getRegionQueContiene(to.getWorld().getName(),
                        to.getX(), to.getY(), to.getZ());
        if (region != null) event.setCancelled(true);
    }

    // ─── MASCOTAS ─────────────────────────────────────────────────────────
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Solo proteger mascotas (Tameable con dueño)
        if (!(event.getEntity() instanceof Tameable tameable)) return;
        if (tameable.getOwner() == null) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;
        if (attacker.hasPermission("discordbridge.ps.admin")) return;

        var loc = event.getEntity().getLocation();
        ProtectionRegion region = plugin.getProtectionManager()
                .getRegionQueContiene(loc.getWorld().getName(),
                        loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        if (region == null) return;
        if (region.esMiembro(attacker.getUniqueId())) return;

        event.setCancelled(true);
        attacker.sendMessage("§cNo puedes atacar mascotas en una zona protegida.");
    }

    // ─── HELPER: mostrar info ─────────────────────────────────────────────
    private void mostrarInfo(Player player, ProtectionRegion region) {
        StoneType st = plugin.getProtectionManager().getStoneType(region.stoneTypeId);
        player.sendMessage("§6§l[PS] §eInformación de la protección:");
        player.sendMessage("§7Dueño: §e" + region.ownerName);
        player.sendMessage("§7Tipo: §e" + (st != null ? st.displayName : region.stoneTypeId));
        player.sendMessage("§7Radio: §e" + region.radio + " bloques");
        player.sendMessage("§7Estado: " + (region.activa ? "§aActiva" : "§cInactiva (dueño en el Abismo)"));
        player.sendMessage("§7Miembros: §e" + region.miembros.size());
        player.sendMessage("§7ID: §8" + region.id);
    }
    // ─── BLOQUEAR PVP EN PROTECCIONES ────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (attacker.hasPermission("discordbridge.ps.admin")) return;

        var loc = victim.getLocation();
        ProtectionRegion region = plugin.getProtectionManager()
                .getRegionQueContiene(loc.getWorld().getName(),
                        loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        if (region == null) return;

        event.setCancelled(true);
        attacker.sendMessage("§cNo puedes atacar jugadores en una zona protegida.");
    }

    // ─── AVISAR AL ENTRAR/SALIR DE PROTECCIONES ───────────────────────────
    @EventHandler
    public void onMoveProteccion(PlayerMoveEvent event) {
        // Solo si cambió de bloque
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();
        String world = player.getWorld().getName();

        // Regiones en las que estaba antes
        java.util.Set<String> antes = regionesActuales.getOrDefault(
                uuid, new java.util.HashSet<>()
        );

        // Regiones en las que está ahora
        java.util.Set<String> ahora = new java.util.HashSet<>();
        for (var region : plugin.getProtectionManager().getTodasLasRegiones()) {
            if (region.activa && region.contiene(world,
                    event.getTo().getBlockX(),
                    event.getTo().getBlockY(),
                    event.getTo().getBlockZ())) {
                ahora.add(region.id);
            }
        }

        regionesActuales.put(uuid, ahora);

        // Regiones que el jugador acaba de entrar
        for (String id : ahora) {
            if (antes.contains(id)) continue;
            var region = plugin.getProtectionManager().getRegion(id);
            if (region == null) continue;

            StoneType st = plugin.getProtectionManager().getStoneType(region.stoneTypeId);
            String nombre = st != null ? st.displayName : region.stoneTypeId;

            player.sendMessage("§a§l[PS] §eEntraste a la zona protegida de §6" +
                    region.ownerName + " §8(" + nombre + "§8)§e.");

            // Notificar al dueño si está online
            Player dueno = org.bukkit.Bukkit.getPlayer(region.ownerUUID);
            if (dueno != null && !dueno.equals(player)) {
                dueno.sendMessage("§e§l[PS] §6" + player.getName() +
                        " §eentró a tu zona protegida §8(" + nombre + "§8)§e.");
            }
        }

        // Regiones que el jugador acaba de salir
        for (String id : antes) {
            if (ahora.contains(id)) continue;
            var region = plugin.getProtectionManager().getRegion(id);
            if (region == null) continue;

            StoneType st = plugin.getProtectionManager().getStoneType(region.stoneTypeId);
            String nombre = st != null ? st.displayName : region.stoneTypeId;

            player.sendMessage("§c§l[PS] §eSaliste de la zona protegida de §6" +
                    region.ownerName + " §8(" + nombre + "§8)§e.");

            // Notificar al dueño si está online
            Player dueno = org.bukkit.Bukkit.getPlayer(region.ownerUUID);
            if (dueno != null && !dueno.equals(player)) {
                dueno.sendMessage("§e§l[PS] §6" + player.getName() +
                        " §esalió de tu zona protegida §8(" + nombre + "§8)§e.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMoveEnCombate(PlayerMoveEvent event) {
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
        if (player.hasPermission("discordbridge.ps.admin")) return;

        String world = event.getTo().getWorld().getName();
        int x = event.getTo().getBlockX();
        int y = event.getTo().getBlockY();
        int z = event.getTo().getBlockZ();

        for (var region : plugin.getProtectionManager().getTodasLasRegiones()) {
            if (!region.worldName.equals(world)) continue;
            if (region.esMiembro(player.getUniqueId())) continue;

            int distancia = Math.max(
                    Math.max(Math.abs(x - region.x), Math.abs(y - region.y)),
                    Math.abs(z - region.z)
            );

            if (distancia <= region.radio + DISTANCIA_AVISO && distancia > region.radio) {
                borderManager.mostrarBorde(player, region.x, region.y, region.z, region.radio);
                return;
            }

            if (distancia <= region.radio) {
                event.setCancelled(true);
                player.sendMessage("§cNo puedes entrar a zonas protegidas mientras estás en combate.");
                return;
            }
        }

        if (borderManager.tieneBorde(player.getUniqueId())) {
            borderManager.quitarBorde(player);
        }
    }

    public void limpiarJugador(java.util.UUID uuid) {
        regionesActuales.remove(uuid);
    }
}