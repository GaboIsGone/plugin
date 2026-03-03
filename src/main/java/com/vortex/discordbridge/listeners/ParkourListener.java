package com.vortex.discordbridge.listeners;

import com.vortex.discordbridge.DiscordBridge;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;

public class ParkourListener implements Listener {

    private final DiscordBridge plugin;

    // Distancia máxima para detectar pressure plate (en bloques)
    private static final double RADIO = 1.0;

    public ParkourListener(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Solo jugadores en el abismo
        if (!plugin.getVidasManager().estaEnAbismo(player.getUniqueId())) return;

        // Solo si cambió de bloque
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Location pos = player.getLocation();
        var pm = plugin.getParkourManager();

        if (!pm.estaConfigurado()) return;

        // ── Inicio ────────────────────────────────────────────────────
        Location inicio = pm.getInicio();
        if (inicio != null && !pm.estaEnParkour(player.getUniqueId()) &&
                estaCerca(pos, inicio)) {
            pm.iniciarParkour(player);
            return;
        }

        if (!pm.estaEnParkour(player.getUniqueId())) return;

        // ── Checkpoints ───────────────────────────────────────────────
        List<Location> checkpoints = pm.getCheckpoints();
        for (int i = 0; i < checkpoints.size(); i++) {
            if (estaCerca(pos, checkpoints.get(i))) {
                pm.alcanzarCheckpoint(player, i);
                return;
            }
        }

        // ── Final ─────────────────────────────────────────────────────
        Location fin = pm.getFinal();
        if (fin != null && estaCerca(pos, fin)) {
            pm.completarParkour(player);
        }
    }

    // Si muere durante el parkour, cancelarlo y volver al inicio
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!plugin.getParkourManager().estaEnParkour(player.getUniqueId())) return;
        plugin.getParkourManager().cancelarParkour(player.getUniqueId());
        player.sendMessage("§c§l[Parkour] §cMoriste. El parkour fue cancelado.");
    }

    private boolean estaCerca(Location a, Location b) {
        if (!a.getWorld().equals(b.getWorld())) return false;
        return Math.abs(a.getBlockX() - b.getBlockX()) <= RADIO &&
                Math.abs(a.getBlockY() - b.getBlockY()) <= RADIO &&
                Math.abs(a.getBlockZ() - b.getBlockZ()) <= RADIO;
    }
}