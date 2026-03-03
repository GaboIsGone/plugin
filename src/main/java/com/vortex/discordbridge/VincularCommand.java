package com.vortex.discordbridge;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VincularCommand implements CommandExecutor {

    private final DiscordBridge plugin;

    public VincularCommand(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        // Solo jugadores pueden usar este comando
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede usarlo un jugador.");
            return true;
        }

        // Si no pone argumento, genera el código
        if (args.length == 0) {
            String codigo = plugin.getLinkManager()
                    .generarCodigo(player.getUniqueId(), player.getName());

            player.sendMessage("§6§l[DiscordBridge] §eVe a Discord y escribe:");
            player.sendMessage("§f/vincular-mc " + codigo);
            player.sendMessage("§7El código expira en " +
                    plugin.getConfig().getInt("link-code-expiry-minutes") + " minutos.");
            return true;
        }

        player.sendMessage("§cUso: /vincular");
        return true;
    }
}