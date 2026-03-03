package com.vortex.discordbridge.commands;

import com.vortex.discordbridge.DiscordBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VidasCommand implements CommandExecutor {

    private final DiscordBridge plugin;

    public VidasCommand(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores pueden usar este comando.");
            return true;
        }

        int vidas = plugin.getVidasManager().getVidas(player.getUniqueId());
        int max = plugin.getVidasManager().getMaxVidasJugador(player);

        StringBuilder barra = new StringBuilder();
        for (int i = 0; i < max; i++) {
            barra.append(i < vidas ? "§c❤ " : "§8❤ ");
        }

        player.sendMessage("§6§l[Vidas] §r" + barra.toString().trim());
        player.sendMessage("§7Tienes §e" + vidas + "§7/§e" + max + " §7vidas.");
        return true;
    }
}