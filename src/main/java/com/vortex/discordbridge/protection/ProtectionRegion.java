package com.vortex.discordbridge.protection;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ProtectionRegion {

    public final String id;           // UUID único de la región
    public final UUID ownerUUID;
    public final String ownerName;
    public final String worldName;
    public final int x, y, z;         // Coordenadas de la piedra
    public final String stoneTypeId;
    public final int radio;
    public boolean activa;
    public final Set<UUID> miembros = new HashSet<>();

    public ProtectionRegion(String id, UUID ownerUUID, String ownerName,
                            String worldName, int x, int y, int z,
                            String stoneTypeId, int radio, boolean activa) {
        this.id = id;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.stoneTypeId = stoneTypeId;
        this.radio = radio;
        this.activa = activa;
    }

    public boolean contiene(String world, int px, int py, int pz) {
        if (!this.worldName.equals(world)) return false;
        return Math.abs(px - x) <= radio &&
                Math.abs(py - y) <= radio &&
                Math.abs(pz - z) <= radio;
    }

    public boolean esMiembro(UUID uuid) {
        return uuid.equals(ownerUUID) || miembros.contains(uuid);
    }
}