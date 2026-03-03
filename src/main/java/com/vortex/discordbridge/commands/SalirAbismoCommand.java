package com.vortex.discordbridge.commands;

import com.vortex.discordbridge.DiscordBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SalirAbismoCommand implements CommandExecutor {

    private final DiscordBridge plugin;

    public SalirAbismoCommand(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!sender.hasPermission("discordbridge.admin.abismo")) {
            sender.sendMessage("§cNo tienes permiso.");
            return true;
        }

        // /salirabismo → se libera a sí mismo
        // /salirabismo <jugador> → libera a otro
        Player objetivo;
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cEspecifica un jugador: /salirabismo <jugador>");
                return true;
            }
            objetivo = (Player) sender;
        } else {
            objetivo = Bukkit.getPlayerExact(args[0]);
            if (objetivo == null) {
                sender.sendMessage("§cEse jugador no está conectado.");
                return true;
            }
        }

        if (!plugin.getVidasManager().estaEnAbismo(objetivo.getUniqueId())) {
            sender.sendMessage("§c" + objetivo.getName() + " no está en el Abismo.");
            return true;
        }

        plugin.getVidasListener().liberarDelAbismoPublico(objetivo);
        sender.sendMessage("§a✅ " + objetivo.getName() + " ha sido liberado del Abismo.");
        return true;
    }
}