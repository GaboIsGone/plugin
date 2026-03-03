package com.vortex.discordbridge;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class VidasPlaceholder extends PlaceholderExpansion {

    private final DiscordBridge plugin;

    public VidasPlaceholder(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "vortex"; }
    @Override public @NotNull String getAuthor() { return "Gabo"; }
    @Override public @NotNull String getVersion() { return "1.0.27"; }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "0";

        VidasManager vm = plugin.getVidasManager();
        int vidas = vm.getVidas(player.getUniqueId());
        int max = vm.getMaxVidasJugador(player);

        switch (identifier) {
            // Número simple: %vortex_vidas%
            case "vidas" -> { return String.valueOf(vidas); }

            // Máximo de vidas del jugador: %vortex_vidas_max%
            case "vidas_max" -> { return String.valueOf(max); }

            // Corazones visuales: %vortex_vidas_corazones%
            // Ejemplo: ❤ ❤ ❤ ❤ ❤ (los que tiene en rojo, los que no en gris)
            case "vidas_corazones" -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < max; i++) {
                    sb.append(i < vidas ? "§c❤" : "§8❤");
                    if (i < max - 1) sb.append(" ");
                }
                return sb.toString();
            }

            case "abismo_tiempo" -> {
                if (!plugin.getVidasManager().estaEnAbismo(player.getUniqueId())) return "0";
                long segundos = plugin.getDatabase().getSegundosEnAbismo(player.getUniqueId().toString());
                long falta = Math.max(0, 86400 - segundos);
                return plugin.getVidasManager().formatearTiempo(falta);
            }

            // Si está en el abismo: %vortex_en_abismo%
            case "en_abismo" -> {
                return plugin.getVidasManager().estaEnAbismo(player.getUniqueId()) ? "true" : "false";
            }
        }

        // Prefijo del clan
        if (identifier.equals("clan_prefijo")) {
            var clan = plugin.getClanManager().getClanDeJugador(player.getUniqueId());
            if (clan == null) return "";
            if (clan.prefijoDisplay == null || clan.prefijoDisplay.isEmpty()) return "§8[§fClan§8]";
            return clan.prefijoDisplay;
        }

        // Nombre del clan
        if (identifier.equals("clan_nombre")) {
            var clan = plugin.getClanManager().getClanDeJugador(player.getUniqueId());
            if (clan == null) return "";
            return clan.nombre;
        }

        // Rol en el clan
        if (identifier.equals("clan_rol")) {
            var clan = plugin.getClanManager().getClanDeJugador(player.getUniqueId());
            if (clan == null) return "";
            var miembro = clan.getMiembro(player.getUniqueId());
            if (miembro == null) return "";
            return switch (miembro.rol) {
                case LIDER -> "§6Líder";
                case COLIDER -> "§eCo-líder";
                case MIEMBRO -> "§7Miembro";
            };
        }
        return null;
    }
}