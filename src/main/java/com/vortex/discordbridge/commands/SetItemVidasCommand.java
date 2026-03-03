package com.vortex.discordbridge.commands;

import com.vortex.discordbridge.DiscordBridge;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SetItemVidasCommand implements CommandExecutor {

    private final DiscordBridge plugin;

    public SetItemVidasCommand(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores pueden usar este comando.");
            return true;
        }

        if (!sender.hasPermission("discordbridge.admin.vidas")) {
            sender.sendMessage("§cNo tienes permiso.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUso: /setitemvidas <extra|donar>");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            sender.sendMessage("§cTienes que tener el item en la mano.");
            return true;
        }

        CustomStack stack = CustomStack.byItemStack(item);
        if (stack == null) {
            sender.sendMessage("§cEse item no es un item de ItemsAdder.");
            return true;
        }

        String itemId = stack.getNamespacedID();
        String tipo = args[0].toLowerCase();

        switch (tipo) {
            case "extra" -> {
                String actual = plugin.getConfig().getString("items.vida-extra", "");
                if (!actual.isEmpty()) {
                    sender.sendMessage("§cYa configurado: §e" + actual);
                    sender.sendMessage("§7Edita el §econfig.yml §7manualmente para cambiarlo.");
                    return true;
                }
                plugin.getConfig().set("items.vida-extra", itemId);
                plugin.saveConfig();
                sender.sendMessage("§a✅ Item de vida extra configurado: §e" + itemId);
            }
            case "donar" -> {
                String actual = plugin.getConfig().getString("items.donar-vida", "");
                if (!actual.isEmpty()) {
                    sender.sendMessage("§cYa configurado: §e" + actual);
                    sender.sendMessage("§7Edita el §econfig.yml §7manualmente para cambiarlo.");
                    return true;
                }
                plugin.getConfig().set("items.donar-vida", itemId);
                plugin.saveConfig();
                sender.sendMessage("§a✅ Item de donar vida configurado: §e" + itemId);
            }
            default -> sender.sendMessage("§cTipo inválido. Usa §eextra §co §edonar§c.");
        }

        return true;
    }
}