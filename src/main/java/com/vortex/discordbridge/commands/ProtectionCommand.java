package com.vortex.discordbridge.commands;

import com.vortex.discordbridge.DiscordBridge;
import com.vortex.discordbridge.protection.ProtectionRegion;
import com.vortex.discordbridge.protection.StoneType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ProtectionCommand implements CommandExecutor {

    private final DiscordBridge plugin;

    public ProtectionCommand(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }

        if (args.length == 0) {
            enviarAyuda(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // /ps lista
            case "lista", "list" -> {
                List<ProtectionRegion> regiones = plugin.getProtectionManager()
                        .getRegionesDeJugador(player.getUniqueId());

                if (regiones.isEmpty()) {
                    player.sendMessage("§cNo tienes protecciones.");
                    return true;
                }

                player.sendMessage("§6§l[PS] §eTus protecciones:");
                for (ProtectionRegion r : regiones) {
                    String estado = r.activa ? "§aActiva" : "§cInactiva";
                    player.sendMessage("§7• §e" + r.stoneTypeId +
                            " §8[" + r.worldName + " " + r.x + "," + r.y + "," + r.z + "]" +
                            " §8— " + estado +
                            " §8— §7Miembros: §e" + r.miembros.size() +
                            " §8— §7ID: §8" + r.id.substring(0, 8));
                }
            }

            // /ps add <jugador> <id>
            case "add", "agregar" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUso: /ps add <jugador> <id-protección>");
                    return true;
                }
                ProtectionRegion region = resolverRegion(player, args[2]);
                if (region == null) return true;
                if (!region.ownerUUID.equals(player.getUniqueId()) &&
                        !player.hasPermission("discordbridge.ps.admin")) {
                    player.sendMessage("§cNo eres el dueño de esa protección.");
                    return true;
                }

                Player objetivo = Bukkit.getPlayerExact(args[1]);
                if (objetivo == null) {
                    player.sendMessage("§cEse jugador no está conectado.");
                    return true;
                }
                if (region.miembros.contains(objetivo.getUniqueId())) {
                    player.sendMessage("§cEse jugador ya es miembro.");
                    return true;
                }

                plugin.getProtectionManager()
                        .agregarMiembro(region.id, objetivo.getUniqueId(), objetivo.getName());
                player.sendMessage("§a§l[PS] §e" + objetivo.getName() +
                        " §aagregado a la protección.");
                objetivo.sendMessage("§a§l[PS] §eFuiste agregado a la protección de §6" +
                        player.getName() + "§e.");
            }

            // /ps remove <jugador> <id>
            case "remove", "quitar" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUso: /ps remove <jugador> <id-protección>");
                    return true;
                }
                ProtectionRegion region = resolverRegion(player, args[2]);
                if (region == null) return true;
                if (!region.ownerUUID.equals(player.getUniqueId()) &&
                        !player.hasPermission("discordbridge.ps.admin")) {
                    player.sendMessage("§cNo eres el dueño de esa protección.");
                    return true;
                }

                Player objetivo = Bukkit.getPlayerExact(args[1]);
                if (objetivo == null) {
                    player.sendMessage("§cEse jugador no está conectado.");
                    return true;
                }

                plugin.getProtectionManager()
                        .quitarMiembro(region.id, objetivo.getUniqueId());
                player.sendMessage("§a§l[PS] §e" + objetivo.getName() +
                        " §aquitado de la protección.");
                objetivo.sendMessage("§c§l[PS] §eFuiste removido de la protección de §6" +
                        player.getName() + "§e.");
            }

            // /ps miembros <id>
            case "miembros", "members" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /ps miembros <id-protección>");
                    return true;
                }
                ProtectionRegion region = resolverRegion(player, args[1]);
                if (region == null) return true;
                if (!region.esMiembro(player.getUniqueId()) &&
                        !player.hasPermission("discordbridge.ps.admin")) {
                    player.sendMessage("§cNo tienes acceso a esa protección.");
                    return true;
                }

                player.sendMessage("§6§l[PS] §eMiembros de la protección:");
                player.sendMessage("§7Dueño: §6" + region.ownerName);
                if (region.miembros.isEmpty()) {
                    player.sendMessage("§7Sin miembros adicionales.");
                } else {
                    // Mostrar UUIDs como nombres si están online
                    region.miembros.forEach(uuid -> {
                        Player m = Bukkit.getPlayer(uuid);
                        String nombre = m != null ? m.getName() : uuid.toString().substring(0, 8);
                        player.sendMessage("§7• §e" + nombre);
                    });
                }
            }

            // /ps transferir <jugador> <id>
            case "transferir", "transfer" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUso: /ps transferir <jugador> <id-protección>");
                    return true;
                }
                ProtectionRegion region = resolverRegion(player, args[2]);
                if (region == null) return true;
                if (!region.ownerUUID.equals(player.getUniqueId()) &&
                        !player.hasPermission("discordbridge.ps.admin")) {
                    player.sendMessage("§cNo eres el dueño de esa protección.");
                    return true;
                }

                Player objetivo = Bukkit.getPlayerExact(args[1]);
                if (objetivo == null) {
                    player.sendMessage("§cEse jugador no está conectado.");
                    return true;
                }

                plugin.getProtectionManager()
                        .transferirDueno(region.id, objetivo.getUniqueId(), objetivo.getName());
                player.sendMessage("§a§l[PS] §eProtección transferida a §6" +
                        objetivo.getName() + "§e.");
                objetivo.sendMessage("§a§l[PS] §e" + player.getName() +
                        " §ate ha transferido una protección.");
            }

            // /ps dar <tipo> [jugador]
            case "dar", "give" -> {
                if (!sender.hasPermission("discordbridge.ps.admin")) {
                    player.sendMessage("§cNo tienes permiso.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUso: /ps dar <tipo> [jugador]");
                    player.sendMessage("§7Tipos disponibles: §e" +
                            String.join("§7, §e",
                                    plugin.getProtectionManager().getStoneTypes().keySet()));
                    return true;
                }

                StoneType st = plugin.getProtectionManager().getStoneType(args[1]);
                if (st == null) {
                    player.sendMessage("§cTipo de piedra no encontrado: §e" + args[1]);
                    player.sendMessage("§7Tipos disponibles: §e" +
                            String.join("§7, §e",
                                    plugin.getProtectionManager().getStoneTypes().keySet()));
                    return true;
                }

                Player objetivo = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : player;
                if (objetivo == null) {
                    player.sendMessage("§cEse jugador no está conectado.");
                    return true;
                }

                var item = plugin.getProtectionManager().crearItemPiedra(st);
                objetivo.getInventory().addItem(item);
                player.sendMessage("§a§l[PS] §eDiste §6" + st.displayName +
                        " §ea §e" + objetivo.getName() + "§e.");
                if (!objetivo.equals(player)) {
                    objetivo.sendMessage("§a§l[PS] §eRecibiste una §6" + st.displayName + "§e.");
                }
            }

            case "ayuda", "help" -> enviarAyuda(player);

            default -> enviarAyuda(player);
        }

        return true;
    }

    private ProtectionRegion resolverRegion(Player player, String idCorto) {
        // Buscar por ID corto (primeros 8 chars)
        var pm = plugin.getProtectionManager();
        for (var region : pm.getRegionesDeJugador(player.getUniqueId())) {
            if (region.id.startsWith(idCorto)) return region;
        }
        // Si tiene admin, buscar en todas
        if (player.hasPermission("discordbridge.ps.admin")) {
            for (var region : pm.getTodasLasRegiones()) {
                if (region.id.startsWith(idCorto)) return region;
            }
        }
        player.sendMessage("§cNo se encontró la protección §e" + idCorto +
                "§c. Usa §e/ps lista §cpara ver tus IDs.");
        return null;
    }

    private void enviarAyuda(Player player) {
        player.sendMessage("§6§l[PS] §eComandos disponibles:");
        player.sendMessage("§e/ps lista §7— Ver tus protecciones");
        player.sendMessage("§e/ps add <jugador> <id> §7— Agregar miembro");
        player.sendMessage("§e/ps remove <jugador> <id> §7— Quitar miembro");
        player.sendMessage("§e/ps miembros <id> §7— Ver miembros");
        player.sendMessage("§e/ps transferir <jugador> <id> §7— Transferir dueño");
        if (player.hasPermission("discordbridge.ps.admin")) {
            player.sendMessage("§e/ps dar <tipo> [jugador] §7— Dar piedra de protección");
        }
    }
}