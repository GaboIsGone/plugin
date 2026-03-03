package com.vortex.discordbridge.commands;

import com.vortex.discordbridge.DiscordBridge;
import com.vortex.discordbridge.clan.Clan;
import com.vortex.discordbridge.clan.ClanMember;
import com.vortex.discordbridge.clan.ClanUpgrade;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ClanCommand implements CommandExecutor {

    private final DiscordBridge plugin;

    public ClanCommand(DiscordBridge plugin) {
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

        var cm = plugin.getClanManager();

        switch (args[0].toLowerCase()) {

            // /clan crear <nombre>
            case "crear" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /clan crear <nombre>");
                    return true;
                }
                cm.crearClan(player, args[1]);
            }

            // /clan disolver
            case "disolver" -> {
                Clan clan = cm.getClanDeJugador(player.getUniqueId());
                if (clan == null) { player.sendMessage("§cNo perteneces a un clan."); return true; }

                ClanMember miembro = clan.getMiembro(player.getUniqueId());
                if (!miembro.esLider() && !player.hasPermission("discordbridge.clan.admin")) {
                    player.sendMessage("§cSolo el líder puede disolver el clan.");
                    return true;
                }
                cm.disolverClan(clan.id);
                player.sendMessage("§a§l[Clan] §eClan disuelto.");
            }

            // /clan invitar <jugador>
            case "invitar" -> {
                if (args.length < 2) { player.sendMessage("§cUso: /clan invitar <jugador>"); return true; }
                Clan clan = cm.getClanDeJugador(player.getUniqueId());
                if (clan == null) { player.sendMessage("§cNo perteneces a un clan."); return true; }

                ClanMember miembro = clan.getMiembro(player.getUniqueId());
                if (!miembro.tienePermisoStaff()) {
                    player.sendMessage("§cNo tienes permiso para invitar jugadores.");
                    return true;
                }

                Player objetivo = Bukkit.getPlayerExact(args[1]);
                if (objetivo == null) { player.sendMessage("§cEse jugador no está conectado."); return true; }
                cm.invitar(player, objetivo, clan);
            }

            // /clan aceptar
            case "aceptar" -> cm.aceptarInvitacion(player);

            // /clan rechazar
            case "rechazar" -> cm.rechazarInvitacion(player);

            // /clan expulsar <jugador>
            case "expulsar" -> {
                if (args.length < 2) { player.sendMessage("§cUso: /clan expulsar <jugador>"); return true; }
                Clan clan = cm.getClanDeJugador(player.getUniqueId());
                if (clan == null) { player.sendMessage("§cNo perteneces a un clan."); return true; }

                ClanMember miembro = clan.getMiembro(player.getUniqueId());
                if (!miembro.tienePermisoStaff()) {
                    player.sendMessage("§cNo tienes permiso para expulsar jugadores.");
                    return true;
                }

                Player objetivo = Bukkit.getPlayerExact(args[1]);
                if (objetivo == null) { player.sendMessage("§cEse jugador no está conectado."); return true; }

                ClanMember objetivoMiembro = clan.getMiembro(objetivo.getUniqueId());
                if (objetivoMiembro == null) { player.sendMessage("§cEse jugador no es miembro del clan."); return true; }
                if (objetivoMiembro.esLider()) { player.sendMessage("§cNo puedes expulsar al líder."); return true; }
                if (objetivoMiembro.esColider() && !miembro.esLider()) {
                    player.sendMessage("§cNo puedes expulsar a un co-líder.");
                    return true;
                }

                if (plugin.getVidasManager().estaEnAbismo(objetivo.getUniqueId())) {
                    player.sendMessage("§cNo puedes expulsar a §e" + objetivo.getName() +
                            " §cmientras está en el Abismo.");
                    return true;
                }

                cm.expulsar(clan, objetivo.getUniqueId());
            }

            // /clan colider <jugador>
            case "colider" -> {
                if (args.length < 2) { player.sendMessage("§cUso: /clan colider <jugador>"); return true; }
                Clan clan = cm.getClanDeJugador(player.getUniqueId());
                if (clan == null) { player.sendMessage("§cNo perteneces a un clan."); return true; }

                ClanMember miembro = clan.getMiembro(player.getUniqueId());
                if (!miembro.esLider()) { player.sendMessage("§cSolo el líder puede asignar co-líderes."); return true; }

                Player objetivo = Bukkit.getPlayerExact(args[1]);
                if (objetivo == null) { player.sendMessage("§cEse jugador no está conectado."); return true; }

                ClanMember objetivoMiembro = clan.getMiembro(objetivo.getUniqueId());
                if (objetivoMiembro == null) { player.sendMessage("§cEse jugador no es miembro del clan."); return true; }

                objetivoMiembro.rol = ClanMember.Rol.COLIDER;
                cm.guardarMiembroPublico(clan.id, objetivoMiembro);
                player.sendMessage("§a§l[Clan] §e§6" + objetivo.getName() + " §eahora es co-líder.");
                objetivo.sendMessage("§a§l[Clan] §eAhora eres co-líder del clan §6" + clan.nombre + "§e.");
            }

            // /clan info [id]
            case "info" -> {
                Clan clan;
                if (args.length >= 2 && player.hasPermission("discordbridge.clan.admin")) {
                    clan = cm.getClan(args[1]);
                } else {
                    clan = cm.getClanDeJugador(player.getUniqueId());
                }
                if (clan == null) { player.sendMessage("§cClan no encontrado."); return true; }
                mostrarInfo(player, clan);
            }

            // /clan lista (staff)
            case "lista" -> {
                if (!player.hasPermission("discordbridge.clan.admin")) {
                    player.sendMessage("§cNo tienes permiso.");
                    return true;
                }
                var clanes = cm.getTodosLosClanes();
                player.sendMessage("§6§l[Clan] §eClanes en el servidor: §6" + clanes.size());
                clanes.forEach(c -> player.sendMessage(
                        "§7• §6" + c.nombre + " §8[" + c.id + "] §7— §e" +
                                c.miembros.size() + "§7/§e" + c.maxMiembros + " §7miembros"
                ));
            }

            // /clan salir
            case "salir" -> {
                Clan clan = cm.getClanDeJugador(player.getUniqueId());
                if (clan == null) { player.sendMessage("§cNo perteneces a un clan."); return true; }
                ClanMember miembro = clan.getMiembro(player.getUniqueId());
                if (miembro.esLider()) {
                    player.sendMessage("§cEl líder no puede salir. Disuelve el clan o transfiere el liderazgo.");
                    return true;
                }
                cm.expulsar(clan, player.getUniqueId());
                player.sendMessage("§c§l[Clan] §eSaliste del clan §6" + clan.nombre + "§e.");
            }

            // /clan mejoras
            case "mejoras" -> {
                Clan clan = cm.getClanDeJugador(player.getUniqueId());
                if (clan == null) { player.sendMessage("§cNo perteneces a un clan."); return true; }
                ClanMember miembro = clan.getMiembro(player.getUniqueId());
                if (!miembro.esLider()) { player.sendMessage("§cSolo el líder puede abrir las mejoras."); return true; }
                plugin.getClanUpgradeGUI().abrir(player, clan);
            }

            // /clan prefijos
            case "prefijos" -> {
                Clan clan = cm.getClanDeJugador(player.getUniqueId());
                if (clan == null) { player.sendMessage("§cNo perteneces a un clan."); return true; }
                ClanMember miembro = clan.getMiembro(player.getUniqueId());
                if (!miembro.esLider()) { player.sendMessage("§cSolo el líder puede cambiar el prefijo."); return true; }
                plugin.getClanPrefixGUI().abrir(player, clan);
            }

            // /clan vidaauxiliar <jugador>
            case "vidaauxiliar" -> {
                if (args.length < 2) { player.sendMessage("§cUso: /clan vidaauxiliar <jugador>"); return true; }
                Clan clan = cm.getClanDeJugador(player.getUniqueId());
                if (clan == null) { player.sendMessage("§cNo perteneces a un clan."); return true; }
                ClanMember miembro = clan.getMiembro(player.getUniqueId());
                if (!miembro.esLider()) { player.sendMessage("§cSolo el líder puede dar vidas auxiliares."); return true; }

                Player objetivo = Bukkit.getPlayerExact(args[1]);
                if (objetivo == null) { player.sendMessage("§cEse jugador no está conectado."); return true; }
                cm.usarVidaAuxiliar(player, objetivo, clan);
            }

            // /clan estandarte
            case "estandarte" -> {
                if (!player.hasPermission("discordbridge.clan.admin")) {
                    player.sendMessage("§cNo tienes permiso.");
                    return true;
                }
                player.getInventory().addItem(plugin.getClanManager().crearItemEstandartePublico());
                player.sendMessage("§a§l[Clan] §eEstandarte añadido a tu inventario.");
            }

            // /clan ver [jugador]
            case "ver", "verclan" -> {
                Clan clan;
                if (args.length >= 2) {
                    Player objetivo = org.bukkit.Bukkit.getPlayerExact(args[1]);
                    if (objetivo == null) {
                        player.sendMessage("§cEse jugador no está conectado.");
                        return true;
                    }
                    clan = plugin.getClanManager().getClanDeJugador(objetivo.getUniqueId());
                    if (clan == null) {
                        player.sendMessage("§e" + objetivo.getName() + " §7no pertenece a ningún clan.");
                        return true;
                    }
                } else {
                    clan = plugin.getClanManager().getClanDeJugador(player.getUniqueId());
                    if (clan == null) {
                        player.sendMessage("§cNo perteneces a ningún clan.");
                        return true;
                    }
                }
                mostrarInfo(player, clan);
            }

            case "staffdisolver" -> {
                if (!player.hasPermission("discordbridge.clan.admin")) {
                    player.sendMessage("§cNo tienes permiso.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUso: /clan staffdisolver <id-clan>");
                    return true;
                }
                Clan clanObjetivo = cm.getClan(args[1]);
                if (clanObjetivo == null) {
                    // Buscar por nombre
                    clanObjetivo = cm.getTodosLosClanes().stream()
                            .filter(c -> c.nombre.equalsIgnoreCase(args[1]))
                            .findFirst().orElse(null);
                }
                if (clanObjetivo == null) {
                    player.sendMessage("§cClan no encontrado. Usa §e/clan lista §cpara ver los IDs.");
                    return true;
                }
                String nombreClan = clanObjetivo.nombre;
                cm.disolverClan(clanObjetivo.id);
                player.sendMessage("§a§l[Clan] §eClan §6" + nombreClan + " §edisuelto por staff.");
            }

            default -> enviarAyuda(player);
        }

        return true;
    }

    private void mostrarInfo(Player player, Clan clan) {
        var vm = plugin.getVidasManager();
        int enAbismo = clan.getMiembrosEnAbismo(vm);
        var estado = clan.getEstadoProteccion(vm);

        player.sendMessage("§6§l════ " + clan.nombre + " ════");
        player.sendMessage("§7ID: §8" + clan.id);
        player.sendMessage("§7Prefijo: " + (clan.prefijoDisplay != null && !clan.prefijoDisplay.isEmpty()
                ? clan.prefijoDisplay : "Ninguno"));
        player.sendMessage("§7Miembros §8(" + clan.miembros.size() + "§7/§8" + clan.maxMiembros + "§7)§8:");
        clan.miembros.values().forEach(m -> {
            String rolColor = switch (m.rol) {
                case LIDER -> "§6[Líder] ";
                case COLIDER -> "§e[Co-líder] ";
                case MIEMBRO -> "§7";
            };
            boolean enAb = vm.estaEnAbismo(m.uuid);
            player.sendMessage("  §7• " + rolColor + m.nombre +
                    (enAb ? " §c[Abismo]" : ""));
        });
        player.sendMessage("§7Estado estandarte: " + switch (estado) {
            case COMPLETA -> "§a✔ Completa";
            case PARCIAL -> "§e⚠ Parcial (>50% en abismo)";
            case VULNERABLE -> "§c✘ Vulnerable (>75% en abismo)";
            case SIN_ESTANDARTE -> "§7— Sin estandarte";
        });
        if (clan.banner != null) {
            player.sendMessage("§7Radio: §e" + clan.banner.radio + " bloques");
            player.sendMessage("§7Mejoras: §e" +
                    (clan.banner.tieneExplosiones ? "§aExplosiones " : "") +
                    (clan.banner.tienePvp ? "§aPvP " : "") +
                    (clan.banner.tieneInteracciones ? "§aInteracciones " : "") +
                    (clan.banner.tieneVidasAuxiliares ? "§aVidas aux." : ""));
        }
        player.sendMessage("§7Vidas auxiliares en stock: §e" + clan.vidasAuxiliares);
    }

    private void enviarAyuda(Player player) {
        player.sendMessage("§6§l[Clan] §eComandos:");
        player.sendMessage("§e/clan crear <nombre> §7— Crear clan");
        player.sendMessage("§e/clan invitar <jugador> §7— Invitar jugador");
        player.sendMessage("§e/clan aceptar/rechazar §7— Responder invitación");
        player.sendMessage("§e/clan expulsar <jugador> §7— Expulsar miembro");
        player.sendMessage("§e/clan estandarte§7— Recibir estandarte de clan");
        player.sendMessage("§e/clan colider <jugador> §7— Asignar co-líder");
        player.sendMessage("§e/clan salir §7— Salir del clan");
        player.sendMessage("§e/clan info §7— Ver info del clan");
        player.sendMessage("§e/clan mejoras §7— Abrir GUI de mejoras");
        player.sendMessage("§e/clan prefijos §7— Cambiar prefijo del clan");
        player.sendMessage("§e/clan ver §7— Ver clan perteneciente de un jugador");
        player.sendMessage("§e/clan vidaauxiliar <jugador> §7— Dar vida auxiliar");
        player.sendMessage("§e/clan disolver §7— Disolver el clan");
        if (player.hasPermission("discordbridge.clan.admin")) {
            player.sendMessage("§e/clan lista §7— Ver todos los clanes");
            player.sendMessage("§e/clan staffdisolver <id|nombre> §7— Disolver clan como staff");
        }
    }
}