package com.vortex.discordbridge.clan;

import java.util.UUID;

public class ClanMember {

    public enum Rol { LIDER, COLIDER, MIEMBRO }

    public final UUID uuid;
    public final String nombre;
    public Rol rol;

    public ClanMember(UUID uuid, String nombre, Rol rol) {
        this.uuid = uuid;
        this.nombre = nombre;
        this.rol = rol;
    }

    public boolean esLider() { return rol == Rol.LIDER; }
    public boolean esColider() { return rol == Rol.COLIDER; }
    public boolean tienePermisoStaff() { return rol == Rol.LIDER || rol == Rol.COLIDER; }
}