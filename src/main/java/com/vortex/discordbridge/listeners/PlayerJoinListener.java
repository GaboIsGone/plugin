package com.vortex.discordbridge.listeners;

import com.vortex.discordbridge.DiscordBridge;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

public class PlayerJoinListener implements Listener {

    private final DiscordBridge plugin;

    public PlayerJoinListener(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        String username = event.getPlayer().getName();
        String ip = event.getAddress().getHostAddress();

        boolean esNueva = plugin.getDatabase().esIpNueva(uuid, ip);
        plugin.getDatabase().registrarIP(uuid, username, ip, esNueva);

        if (esNueva) {
            plugin.getBotNotifier().enviarAlertaIPNueva(username, ip);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String username = event.getPlayer().getName();
        java.util.UUID uuid = event.getPlayer().getUniqueId();

        // loadUser fuerza la carga aunque no esté en caché
        LuckPermsProvider.get().getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
            String grupo = user != null ? user.getPrimaryGroup() : "default";
            plugin.getBotNotifier().enviarNotificacion("join", username, grupo);
        });
    }
}