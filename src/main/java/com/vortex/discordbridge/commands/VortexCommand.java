package com.vortex.discordbridge.commands;

import com.vortex.discordbridge.DiscordBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class VortexCommand implements CommandExecutor {

    private final DiscordBridge plugin;

    public VortexCommand(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!sender.hasPermission("discordbridge.admin")) {
            sender.sendMessage("§cNo tienes permiso.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6§l[Vortex] §eUso: /vortex reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.getProtectionManager().cargarStones();
            plugin.getClanManager().reloadConfig();
            sender.sendMessage("§a§l[Vortex] §eConfiguración recargada:");
            sender.sendMessage("§7• §econfig.yml §arecargada.");
            sender.sendMessage("§7• §estones.yml §arecargado — §6" +
                    plugin.getProtectionManager().getStoneTypes().size() +
                    " §etipo(s) de piedra.");
            sender.sendMessage("§7• §eclan.yml §arecargado.");
        }

        return true;
    }
}