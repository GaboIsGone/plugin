package com.vortex.discordbridge.protection;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import org.bukkit.entity.Player;

import java.util.*;

public class ProtectionBorderManager {

    // uuid → bloques enviados al jugador (para quitarlos después)
    private final Map<UUID, Set<Vector3i>> bloquesEnviados = new HashMap<>();

    // ─── MOSTRAR BORDE ────────────────────────────────────────────────────

    public void mostrarBorde(Player player, int cx, int cy, int cz, int radio) {
        Set<Vector3i> nuevos = new HashSet<>();

        // Recorrer las 6 caras del cubo
        for (int x = cx - radio; x <= cx + radio; x++) {
            for (int y = cy - radio; y <= cy + radio; y++) {
                for (int z = cz - radio; z <= cz + radio; z++) {
                    boolean esBorde =
                            x == cx - radio || x == cx + radio ||
                                    y == cy - radio || y == cy + radio ||
                                    z == cz - radio || z == cz + radio;

                    if (!esBorde) continue;

                    // Solo mostrar bloques que estén en aire (no reemplazar bloques reales)
                    var loc = new org.bukkit.Location(player.getWorld(), x, y, z);
                    if (loc.getBlock().getType() != org.bukkit.Material.AIR) continue;

                    nuevos.add(new Vector3i(x, y, z));
                }
            }
        }

        // Quitar borde anterior si había
        quitarBorde(player);

        // Enviar nuevos bloques
        bloquesEnviados.put(player.getUniqueId(), nuevos);
        for (Vector3i pos : nuevos) {
            enviarBloque(player, pos,
                    WrappedBlockState.getByString("minecraft:red_stained_glass"));
        }
    }

    // ─── QUITAR BORDE ─────────────────────────────────────────────────────

    public void quitarBorde(Player player) {
        Set<Vector3i> bloques = bloquesEnviados.remove(player.getUniqueId());
        if (bloques == null) return;

        for (Vector3i pos : bloques) {
            // Restaurar bloque real en esa posición
            var loc = new org.bukkit.Location(player.getWorld(), pos.x, pos.y, pos.z);
            var blockState = WrappedBlockState.getByString(
                    "minecraft:" + loc.getBlock().getType().name().toLowerCase()
            );
            enviarBloque(player, pos, blockState);
        }
    }

    public boolean tieneBorde(UUID uuid) {
        return bloquesEnviados.containsKey(uuid);
    }

    // ─── HELPER ───────────────────────────────────────────────────────────

    private void enviarBloque(Player player, Vector3i pos, WrappedBlockState state) {
        try {
            WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(pos, state);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Exception e) {
            // Ignorar errores de packet
        }
    }
}