package com.vortex.discordbridge;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class RangoVidasConfig {

    private final DiscordBridge plugin;

    public RangoVidasConfig(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    private ConfigurationSection getTierConfig(Player player) {
        ConfigurationSection rangos = plugin.getConfig()
                .getConfigurationSection("vidas.rangos");
        if (rangos == null) return null;

        ConfigurationSection mejorTier = null;
        for (String key : rangos.getKeys(false)) {
            if (key.equals("default")) continue;
            ConfigurationSection tier = rangos.getConfigurationSection(key);
            if (tier == null) continue;
            String permiso = tier.getString("permiso", "");
            if (!permiso.isEmpty() && player.hasPermission(permiso)) {
                mejorTier = tier;
            }
        }

        if (mejorTier == null) {
            mejorTier = rangos.getConfigurationSection("default");
        }
        return mejorTier;
    }

    public int getMaxVidas(Player player) {
        ConfigurationSection tier = getTierConfig(player);
        if (tier == null) return plugin.getConfig().getInt("vidas.maximas", 5);
        return tier.getInt("maximas", plugin.getConfig().getInt("vidas.maximas", 5));
    }

    public long getCooldownVidaExtra(Player player) {
        ConfigurationSection tier = getTierConfig(player);
        if (tier == null) return 900L;
        return tier.getLong("cooldown-vida-extra", 900L);
    }

    public long getCooldownDonar(Player player) {
        ConfigurationSection tier = getTierConfig(player);
        if (tier == null) return 900L;
        return tier.getLong("cooldown-donar", 900L);
    }

    public double getDistanciaDonar(Player player) {
        ConfigurationSection tier = getTierConfig(player);
        if (tier == null) return 50.0;
        return tier.getDouble("distancia-donar", 50.0);
    }
}