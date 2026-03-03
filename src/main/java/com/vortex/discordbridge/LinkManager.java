package com.vortex.discordbridge;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LinkManager {

    private final DiscordBridge plugin;

    // ─── LINKS PENDIENTES ─────────────────────────────────────────────
    private final Map<String, PendingLink> pendingLinks = new HashMap<>();

    // ─── SEGURIDAD ────────────────────────────────────────────────────
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String, Integer> intentosFallidos = new HashMap<>();
    private final Map<String, Long> bloqueados = new HashMap<>();
    private PendingLink ultimoLinkValido = null;

    private static final int MAX_INTENTOS = 5;
    private static final long COOLDOWN_MS  = 30_000L;   // 30 segundos
    private static final long BLOQUEO_MS   = 300_000L;  // 5 minutos

    public enum ResultadoVerificacion {
        VALIDO, INVALIDO, COOLDOWN, BLOQUEADO
    }

    public LinkManager(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    // ─── GENERAR CÓDIGO ───────────────────────────────────────────────

    public String generarCodigo(UUID playerUUID, String username) {
        // Eliminar código anterior del mismo jugador si existía
        pendingLinks.values().removeIf(l -> l.uuid.equals(playerUUID));

        String codigo = generarCodigoAleatorio();
        pendingLinks.put(codigo, new PendingLink(playerUUID, username, System.currentTimeMillis()));
        return codigo;
    }

    // ─── VERIFICAR CON SEGURIDAD (usado por el handler HTTP) ─────────

    public ResultadoVerificacion verificarConSeguridad(String codigo, String discordId) {
        long ahora = System.currentTimeMillis();

        // ¿Está bloqueado?
        if (bloqueados.containsKey(discordId)) {
            if (ahora - bloqueados.get(discordId) < BLOQUEO_MS) {
                return ResultadoVerificacion.BLOQUEADO;
            }
            // Bloqueo expirado, limpiar
            bloqueados.remove(discordId);
            intentosFallidos.remove(discordId);
        }

        // ¿Está en cooldown?
        if (cooldowns.containsKey(discordId)) {
            if (ahora - cooldowns.get(discordId) < COOLDOWN_MS) {
                return ResultadoVerificacion.COOLDOWN;
            }
        }

        // Actualizar cooldown
        cooldowns.put(discordId, ahora);

        // Verificar código
        PendingLink link = verificarCodigo(codigo);
        if (link == null) {
            int intentos = intentosFallidos.getOrDefault(discordId, 0) + 1;
            intentosFallidos.put(discordId, intentos);

            if (intentos >= MAX_INTENTOS) {
                bloqueados.put(discordId, ahora);
                intentosFallidos.remove(discordId);
            }
            return ResultadoVerificacion.INVALIDO;
        }

        // Éxito — guardar link válido y limpiar estado
        ultimoLinkValido = link;
        cooldowns.remove(discordId);
        intentosFallidos.remove(discordId);
        return ResultadoVerificacion.VALIDO;
    }

    // ─── OBTENER ÚLTIMO LINK VÁLIDO (usado justo después de VALIDO) ───

    public PendingLink getUltimoLinkValido() {
        PendingLink link = ultimoLinkValido;
        ultimoLinkValido = null; // Limpiar tras leer
        return link;
    }

    // ─── VERIFICAR CÓDIGO (interno) ───────────────────────────────────

    private PendingLink verificarCodigo(String codigo) {
        PendingLink link = pendingLinks.get(codigo.toUpperCase());
        if (link == null) return null;

        long expiryMs = plugin.getConfig().getInt("link-code-expiry-minutes") * 60 * 1000L;
        if (System.currentTimeMillis() - link.timestamp > expiryMs) {
            pendingLinks.remove(codigo.toUpperCase());
            return null;
        }

        pendingLinks.remove(codigo.toUpperCase());
        return link;
    }

    // ─── GENERADOR DE CÓDIGO ALEATORIO ────────────────────────────────

    private String generarCodigoAleatorio() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Sin caracteres confusos
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++)
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    // ─── CLASE INTERNA ────────────────────────────────────────────────

    public static class PendingLink {
        public final UUID uuid;
        public final String username;
        public final long timestamp;

        public PendingLink(UUID uuid, String username, long timestamp) {
            this.uuid = uuid;
            this.username = username;
            this.timestamp = timestamp;
        }
    }
}