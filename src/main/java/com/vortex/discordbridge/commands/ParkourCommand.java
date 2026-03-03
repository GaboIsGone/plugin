package com.vortex.discordbridge.commands;

import com.vortex.discordbridge.DiscordBridge;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ParkourCommand implements CommandExecutor {

    private final DiscordBridge plugin;

    public ParkourCommand(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores pueden usar este comando.");
            return true;
        }

        if (!sender.hasPermission("discordbridge.admin.parkour")) {
            sender.sendMessage("§cNo tienes permiso.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUso: /parkour <setinicio|setcheckpoint <n>|setfinal|info|reset>");
            return true;
        }

        var pm = plugin.getParkourManager();

        switch (args[0].toLowerCase()) {
            case "setinicio" -> {
                pm.setInicio(player.getLocation());
                player.sendMessage("§a✅ Inicio del parkour configurado en tu posición.");
            }
            case "setcheckpoint" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /parkour setcheckpoint <número>");
                    return true;
                }
                try {
                    int numero = Integer.parseInt(args[1]);
                    pm.setCheckpoint(numero, player.getLocation());
                    player.sendMessage("§a✅ Checkpoint §6" + numero +
                            " §aconfigured en tu posición.");
                } catch (NumberFormatException e) {
                    player.sendMessage("§cEl número debe ser un entero.");
                }
            }
            case "setfinal" -> {
                pm.setFinal(player.getLocation());
                player.sendMessage("§a✅ Final del parkour configurado en tu posición.");
            }
            case "info" -> {
                player.sendMessage("§6§l[Parkour] §eConfiguración actual:");
                player.sendMessage("§7Inicio: §e" + formatLoc(pm.getInicio()));
                player.sendMessage("§7Final: §e" + formatLoc(pm.getFinal()));
                player.sendMessage("§7Checkpoints: §e" + pm.getTotalCheckpoints());
                List<Location> cps = pm.getCheckpoints();
                for (int i = 0; i < cps.size(); i++) {
                    player.sendMessage("§7  #" + i + ": §e" + formatLoc(cps.get(i)));
                }
                player.sendMessage("§7Configurado: " +
                        (pm.estaConfigurado() ? "§a✅" : "§c❌ Falta inicio o final"));
            }
            default -> player.sendMessage(
                    "§cUso: /parkour <setinicio|setcheckpoint <n>|setfinal|info>"
            );
        }

        return true;
    }

    private String formatLoc(Location loc) {
        if (loc == null) return "§cNo configurado";
        return loc.getWorld().getName() + " " +
                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}