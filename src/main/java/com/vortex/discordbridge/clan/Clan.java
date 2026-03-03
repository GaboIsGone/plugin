package com.vortex.discordbridge.clan;

import java.util.*;

public class Clan {

    public final String id;
    public String nombre;
    public String prefijoid;    // id del prefijo elegido
    public String prefijoDisplay; // emoji/texto del prefijo
    public int maxMiembros;
    public int slotsComprados;
    public int vidasAuxiliares; // stock de vidas auxiliares disponibles

    public final Map<UUID, ClanMember> miembros = new LinkedHashMap<>();
    public ClanBanner banner;

    public Clan(String id, String nombre, int maxMiembros) {
        this.id = id;
        this.nombre = nombre;
        this.maxMiembros = maxMiembros;
        this.slotsComprados = 0;
        this.vidasAuxiliares = 0;
    }

    public ClanMember getLider() {
        return miembros.values().stream()
                .filter(ClanMember::esLider)
                .findFirst().orElse(null);
    }

    public ClanMember getMiembro(UUID uuid) {
        return miembros.get(uuid);
    }

    public boolean tieneMiembro(UUID uuid) {
        return miembros.containsKey(uuid);
    }

    public boolean estaLleno() {
        return miembros.size() >= maxMiembros;
    }

    public int getMiembrosEnAbismo(com.vortex.discordbridge.VidasManager vm) {
        return (int) miembros.keySet().stream()
                .filter(vm::estaEnAbismo)
                .count();
    }

    public ClanBanner.EstadoProteccion getEstadoProteccion(
            com.vortex.discordbridge.VidasManager vm) {
        if (banner == null || !banner.colocado)
            return ClanBanner.EstadoProteccion.SIN_ESTANDARTE;
        return banner.getEstado(miembros.size(), getMiembrosEnAbismo(vm));
    }

    public String getPrefijoFormateado() {
        if (prefijoDisplay == null || prefijoDisplay.isEmpty()) return "";
        return prefijoDisplay + " ";
    }
}