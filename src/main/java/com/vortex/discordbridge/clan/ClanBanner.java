package com.vortex.discordbridge.clan;

public class ClanBanner {

    public String worldName;
    public int x, y, z;
    public int radio;
    public boolean colocado;

    public int nivelZona = 0;
    public boolean tieneExplosiones = false;
    public boolean tienePvp = false;
    public boolean tieneInteracciones = false;
    public boolean tieneVidasAuxiliares = false;

    public enum EstadoProteccion {
        COMPLETA,
        PARCIAL,
        VULNERABLE,
        SIN_ESTANDARTE
    }

    public ClanBanner(String worldName, int x, int y, int z, int radio) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radio = radio;
        this.colocado = true;
    }

    public boolean contiene(String world, int px, int py, int pz) {
        if (!this.worldName.equals(world)) return false;
        return Math.abs(px - x) <= radio &&
                Math.abs(py - y) <= radio &&
                Math.abs(pz - z) <= radio;
    }

    public EstadoProteccion getEstado(int totalMiembros, int miembrosEnAbismo) {
        if (totalMiembros == 0) return EstadoProteccion.COMPLETA;
        if (miembrosEnAbismo == 0) return EstadoProteccion.COMPLETA;
        double porcentaje = (double) miembrosEnAbismo / totalMiembros * 100.0;
        if (porcentaje > 75.0) return EstadoProteccion.VULNERABLE;
        if (porcentaje > 50.0) return EstadoProteccion.PARCIAL;
        return EstadoProteccion.COMPLETA;
    }

    public boolean protegeContraBloques(EstadoProteccion estado) {
        return estado != EstadoProteccion.VULNERABLE && estado != EstadoProteccion.SIN_ESTANDARTE;
    }

    public boolean protegeContraExplosiones(EstadoProteccion estado) {
        return tieneExplosiones && estado != EstadoProteccion.VULNERABLE && estado != EstadoProteccion.SIN_ESTANDARTE;
    }

    public boolean protegeContraInteracciones(EstadoProteccion estado) {
        return tieneInteracciones && estado == EstadoProteccion.COMPLETA;
    }

    public boolean protegeContraPvP(EstadoProteccion estado) {
        return tienePvp && estado == EstadoProteccion.COMPLETA;
    }
}