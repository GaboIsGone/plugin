package com.vortex.discordbridge.commands;

import com.vortex.discordbridge.DiscordBridge;
import com.vortex.discordbridge.clan.Clan;
import com.vortex.discordbridge.clan.ClanMember;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClanChatCommand implements CommandExecutor {

    private final DiscordBridge plugin;
    // Staff con focus activo → clan id que están escuchando
    private final Map<UUID, String> staffFocus = new HashMap<>();

    public ClanChatCommand(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }

        // /clan focus [id-clan] — comando de staff
        if (label.equalsIgnoreCase("clanfocus")) {
            if (!player.hasPermission("discordbridge.clan.admin")) {
                player.sendMessage("§cNo tienes permiso.");
                return true;
            }

            if (args.length == 0) {
                // Sin argumento — quitar focus
                if (staffFocus.containsKey(player.getUniqueId())) {
                    String clanId = staffFocus.remove(player.getUniqueId());
                    Clan clan = plugin.getClanManager().getClan(clanId);
                    player.sendMessage("§c§l[Clan] §eYa no estás monitorizando el chat del clan §6" +
                            (clan != null ? clan.nombre : clanId) + "§e.");
                } else {
                    player.sendMessage("§cNo tienes ningún clan en focus. Usa §e/clanfocus <id|nombre>§c.");
                }
                return true;
            }

            // Buscar clan por id o nombre
            Clan clan = plugin.getClanManager().getClan(args[0]);
            if (clan == null) {
                clan = plugin.getClanManager().getTodosLosClanes().stream()
                        .filter(c -> c.nombre.equalsIgnoreCase(args[0]))
                        .findFirst().orElse(null);
            }
            if (clan == null) {
                player.sendMessage("§cClan no encontrado. Usa §e/clan lista §cpara ver los IDs.");
                return true;
            }

            staffFocus.put(player.getUniqueId(), clan.id);
            player.sendMessage("§a§l[Clan] §eAhora monitorizas el chat del clan §6" +
                    clan.nombre + "§e. Usa §e/clanfocus §7sin argumentos para quitar el focus.");
            return true;
        }

        // /cc <mensaje>
        if (args.length == 0) {
            player.sendMessage("§cUso: /cc <mensaje>");
            return true;
        }

        Clan clan = plugin.getClanManager().getClanDeJugador(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cNo perteneces a ningún clan.");
            return true;
        }

        ClanMember miembro = clan.getMiembro(player.getUniqueId());
        String rolColor = switch (miembro.rol) {
            case LIDER -> "§6[L] ";
            case COLIDER -> "§e[CL] ";
            case MIEMBRO -> "§7";
        };

        String mensaje = String.join(" ", args);

        // Construir componente del mensaje
        Component componente = Component.text()
                .append(Component.text("§8[§6Clan §e" + clan.nombre + "§8] ", NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(rolColor + player.getName() + "§7: §f" + mensaje)
                        .decoration(TextDecoration.ITALIC, false))
                .build();

        // Enviar a miembros online
        clan.miembros.keySet().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(componente);
        });

        // Enviar a staff con focus en este clan
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> clan.id.equals(staffFocus.get(p.getUniqueId())))
                .filter(p -> !clan.tieneMiembro(p.getUniqueId())) // evitar duplicados
                .forEach(p -> {
                    p.sendMessage(Component.text("§8[§cMON§8] ")
                            .append(componente));
                });

        return true;
    }

    public Map<UUID, String> getStaffFocus() { return staffFocus; }
}