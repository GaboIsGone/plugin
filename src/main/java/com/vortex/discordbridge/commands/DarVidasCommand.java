package com.vortex.discordbridge.commands;

import com.vortex.discordbridge.DiscordBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DarVidasCommand implements CommandExecutor {

    private final DiscordBridge plugin;

    public DarVidasCommand(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!sender.hasPermission("discordbridge.admin.vidas")) {
            sender.sendMessage("§cNo tienes permiso.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUso: /darvidas <jugador> <cantidad>");
            return true;
        }

        Player objetivo = Bukkit.getPlayerExact(args[0]);
        if (objetivo == null) {
            sender.sendMessage("§cEse jugador no está conectado.");
            return true;
        }

        int cantidad;
        try {
            cantidad = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLa cantidad debe ser un número.");
            return true;
        }

        plugin.getVidasManager().setVidas(objetivo.getUniqueId(), objetivo.getName(), cantidad);
        sender.sendMessage("§a§l[Vidas] §7Has dado §e" + cantidad +
                " §7vidas a §a" + objetivo.getName() + "§7.");
        objetivo.sendMessage("§a§l[Vidas] §7El staff te ha dado §e" +
                cantidad + " §7vidas.");
        return true;
    }
}