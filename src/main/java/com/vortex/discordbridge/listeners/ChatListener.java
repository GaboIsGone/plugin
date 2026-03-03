package com.vortex.discordbridge.listeners;

import com.vortex.discordbridge.DiscordBridge;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final DiscordBridge plugin;

    public ChatListener(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String username = event.getPlayer().getName();

        String mensaje = PlainTextComponentSerializer.plainText()
                .serialize(event.message());

        // Obtener el GRUPO principal de LuckPerms (no el prefijo)
        String grupo = "default";
        try {
            User user = LuckPermsProvider.get()
                    .getUserManager()
                    .getUser(event.getPlayer().getUniqueId());

            if (user != null) {
                grupo = user.getPrimaryGroup();
            }
        } catch (Exception e) {
            // LuckPerms no disponible
        }

        final String grupoFinal = grupo;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getBotNotifier().enviarMensajeChat(username, grupoFinal, mensaje)
        );
    }
}