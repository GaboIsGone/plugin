package com.vortex.discordbridge.listeners;

import com.vortex.discordbridge.DiscordBridge;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final DiscordBridge plugin;

    public PlayerQuitListener(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String username = event.getPlayer().getName();
        java.util.UUID uuid = event.getPlayer().getUniqueId();

        // En el quit el usuario SÍ está en caché, pero usamos loadUser
        // por consistencia y seguridad
        LuckPermsProvider.get().getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
            String grupo = user != null ? user.getPrimaryGroup() : "default";
            plugin.getBotNotifier().enviarNotificacion("quit", username, grupo);
        });
    }
}