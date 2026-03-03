package com.vortex.discordbridge;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AbismoBossBarManager {

    private final DiscordBridge plugin;

    // uuid del jugador → su bossbar
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    private static final long SEGUNDOS_TOTAL = 86400L;

    public AbismoBossBarManager(DiscordBridge plugin) {
        this.plugin = plugin;
        // Actualizar cada 5 segundos
        Bukkit.getScheduler().runTaskTimer(plugin, this::actualizarTodos, 20L, 20L * 5);
    }

    // ─── MOSTRAR BOSSBAR ──────────────────────────────────────────────────

    public void mostrarBossBar(Player jugador) {
        // Quitar la anterior si existía
        quitarBossBar(jugador);

        long segundos = plugin.getDatabase()
                .getSegundosEnAbismo(jugador.getUniqueId().toString());
        long falta = Math.max(0, SEGUNDOS_TOTAL - segundos);
        float progreso = Math.max(0f, Math.min(1f, (float) falta / SEGUNDOS_TOTAL));

        BossBar bossBar = BossBar.bossBar(
                construirTexto(falta),
                progreso,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_20
        );

        bossBars.put(jugador.getUniqueId(), bossBar);
        jugador.showBossBar(bossBar);
    }

    // ─── QUITAR BOSSBAR ───────────────────────────────────────────────────

    public void quitarBossBar(Player jugador) {
        BossBar bossBar = bossBars.remove(jugador.getUniqueId());
        if (bossBar != null) {
            jugador.hideBossBar(bossBar);
        }
    }

    // ─── ACTUALIZAR TODOS ─────────────────────────────────────────────────

    private void actualizarTodos() {
        for (Map.Entry<UUID, BossBar> entry : bossBars.entrySet()) {
            Player jugador = Bukkit.getPlayer(entry.getKey());
            if (jugador == null || !jugador.isOnline()) continue;

            long segundos = plugin.getDatabase()
                    .getSegundosEnAbismo(entry.getKey().toString());
            long falta = Math.max(0, SEGUNDOS_TOTAL - segundos);
            float progreso = Math.max(0f, Math.min(1f, (float) falta / SEGUNDOS_TOTAL));

            BossBar bossBar = entry.getValue();
            bossBar.name(construirTexto(falta));
            bossBar.progress(progreso);

            // Cambiar color según el tiempo restante
            if (falta <= 3600) {
                // Menos de 1 hora — verde (casi libre)
                bossBar.color(BossBar.Color.GREEN);
            } else if (falta <= 7200) {
                // Menos de 2 horas — amarillo
                bossBar.color(BossBar.Color.YELLOW);
            } else {
                // Más de 2 horas — rojo
                bossBar.color(BossBar.Color.RED);
            }
        }
    }

    // ─── LIMPIAR TODO ─────────────────────────────────────────────────────

    public void limpiarTodo() {
        for (Map.Entry<UUID, BossBar> entry : bossBars.entrySet()) {
            Player jugador = Bukkit.getPlayer(entry.getKey());
            if (jugador != null) jugador.hideBossBar(entry.getValue());
        }
        bossBars.clear();
    }

    // ─── HELPER ───────────────────────────────────────────────────────────

    private Component construirTexto(long falta) {
        String tiempo = plugin.getVidasManager().formatearTiempo(falta);
        return Component.text("☠ ", NamedTextColor.DARK_RED)
                .append(Component.text("Abismo", NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text(tiempo + " restantes", NamedTextColor.WHITE));
    }
}