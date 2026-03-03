package com.vortex.discordbridge.clan.listeners;

import com.vortex.discordbridge.DiscordBridge;
import com.vortex.discordbridge.clan.Clan;
import com.vortex.discordbridge.clan.ClanBanner;
import com.vortex.discordbridge.clan.ClanMember;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ClanChatListener implements Listener {

    private final DiscordBridge plugin;

    public ClanChatListener(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Clan clan = plugin.getClanManager().getClanDeJugador(player.getUniqueId());

        // Prefijo de LuckPerms
        var luckPerms = plugin.getLuckPerms();
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        final String rawPrefix;
        if (user != null) {
            String p = user.getCachedData().getMetaData().getPrefix();
            rawPrefix = p != null ? p : "";
        } else {
            rawPrefix = "";
        }

        // Insignia del clan con hover (solo si tiene clan)
        final Component insignia;
        if (clan != null) {
            String textoInsignia = (clan.prefijoDisplay != null && !clan.prefijoDisplay.isEmpty())
                    ? clan.prefijoDisplay : "§6🔥";

            var vm = plugin.getVidasManager();
            int enAbismo = clan.getMiembrosEnAbismo(vm);
            ClanBanner.EstadoProteccion estado = clan.getEstadoProteccion(vm);

            String estadoTexto = switch (estado) {
                case COMPLETA -> "§aProtección completa";
                case PARCIAL -> "§eProtección parcial";
                case VULNERABLE -> "§cVulnerable";
                case SIN_ESTANDARTE -> "§7Sin estandarte";
            };

            ClanMember miembro = clan.getMiembro(player.getUniqueId());
            String rolTexto = switch (miembro.rol) {
                case LIDER -> "§6Líder";
                case COLIDER -> "§eCo-líder";
                case MIEMBRO -> "§7Miembro";
            };

            Component hoverTexto = Component.text()
                    .append(Component.text("⚔ " + clan.nombre, NamedTextColor.GOLD)
                            .decorate(TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("ID: §8" + clan.id))
                    .append(Component.newline())
                    .append(Component.text("Rol: " + rolTexto))
                    .append(Component.newline())
                    .append(Component.text("Miembros: §e" + clan.miembros.size() +
                            "§7/§e" + clan.maxMiembros))
                    .append(Component.newline())
                    .append(Component.text("En abismo: §c" + enAbismo))
                    .append(Component.newline())
                    .append(Component.text("Estado: " + estadoTexto))
                    .build();

            insignia = Component.text(" " + textoInsignia + " ")
                    .decoration(TextDecoration.ITALIC, false)
                    .hoverEvent(HoverEvent.showText(hoverTexto));
        } else {
            // Sin clan — sin insignia, solo espacio
            insignia = Component.text(" ");
        }

        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.text(rawPrefix)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(sourceDisplayName)
                        .append(insignia)
                        .append(Component.text("» ", NamedTextColor.DARK_GRAY))
                        .append(message)
        );
    }
}