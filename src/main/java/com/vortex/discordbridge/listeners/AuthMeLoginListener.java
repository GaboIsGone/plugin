package com.vortex.discordbridge.listeners;

import com.vortex.discordbridge.DiscordBridge;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.events.FailedLoginEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class AuthMeLoginListener implements Listener {

    private final DiscordBridge plugin;
    // A partir de cuántos fallos consecutivos se manda alerta
    private static final int UMBRAL_FUERZA_BRUTA = 5;

    public AuthMeLoginListener(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLoginExitoso(LoginEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        String username = event.getPlayer().getName();
        String ip = event.getPlayer().getAddress().getAddress().getHostAddress();
        String mcVersion = event.getPlayer().getClientBrandName() != null ?
                event.getPlayer().getClientBrandName() : "Desconocido";

        plugin.getDatabase().registrarLogin(uuid, username, ip, true, null, "N/A", mcVersion);
    }

    @EventHandler
    public void onLoginFallido(FailedLoginEvent event) {
        String uuid = event.getPlayer().getUniqueId().toString();
        String username = event.getPlayer().getName();
        String ip = event.getPlayer().getAddress().getAddress().getHostAddress();

        plugin.getDatabase().registrarLogin(
                uuid, username, ip, false, "Contraseña incorrecta", "N/A", "Desconocido"
        );

        // Verificar fuerza bruta de forma asíncrona
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int fallos = plugin.getDatabase().contarFallosRecientes(uuid);
            if (fallos >= UMBRAL_FUERZA_BRUTA) {
                plugin.getBotNotifier().enviarAlertaFuerzaBruta(username, ip, fallos);
            }
        });
    }
}