package com.vortex.discordbridge;

import com.vortex.discordbridge.DiscordBridge;
import me.chancesd.pvpmanager.PvPManager;
import me.chancesd.pvpmanager.manager.PlayerManager;
import me.chancesd.pvpmanager.player.CombatPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PvPManagerHook {

    private PlayerManager playerManager;
    private boolean disponible = false;

    public PvPManagerHook(DiscordBridge plugin) {
        Plugin p = plugin.getServer().getPluginManager().getPlugin("PvPManager");

        if (p instanceof PvPManager manager) {
            this.playerManager = manager.getPlayerManager();
            this.disponible = true;
            plugin.getLogger().info("PvPManager 4.0.7 hookeado correctamente.");
        } else {
            plugin.getLogger().warning("PvPManager no encontrado.");
        }
    }

    public boolean estaEnCombate(Player player) {
        if (!disponible || playerManager == null) return false;

        CombatPlayer cp = playerManager.get(player);
        return cp != null && cp.isInCombat();
    }

    public boolean isDisponible() {
        return disponible;
    }
}