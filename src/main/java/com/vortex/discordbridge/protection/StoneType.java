package com.vortex.discordbridge.protection;

public class StoneType {

    public final String id;
    public final String material;
    public final String itemsAdderId;
    public final String displayName;
    public final int radio;
    public final int limitePorJugador;
    public final String descripcion;
    public final String permiso;

    public StoneType(String id, String material, String itemsAdderId,
                     String displayName, int radio, int limitePorJugador,
                     String descripcion, String permiso) {
        this.id = id;
        this.material = material;
        this.itemsAdderId = itemsAdderId;
        this.displayName = displayName;
        this.radio = radio;
        this.limitePorJugador = limitePorJugador;
        this.descripcion = descripcion;
        this.permiso = permiso;
    }
}