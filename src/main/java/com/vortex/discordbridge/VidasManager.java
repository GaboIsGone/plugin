package com.vortex.discordbridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VidasManager {

    private final DiscordBridge plugin;
    private final RangoVidasConfig rangoConfig;

    private final Map<UUID, Integer> cacheVidas = new HashMap<>();
    private final Map<UUID, Long> tiempoConexion = new HashMap<>();
    private final Map<UUID, DonacionPendiente> donacionesPendientes = new HashMap<>();
    private final Map<UUID, Long> cooldownsDonar = new HashMap<>();
    private final Map<UUID, Long> cooldownsVidaExtra = new HashMap<>();

    private static final long SEGUNDOS_24H = 86400L;
    private static final int TIMEOUT_DONACION = 30;

    public VidasManager(DiscordBridge plugin) {
        this.plugin = plugin;
        this.rangoConfig = new RangoVidasConfig(plugin);
    }

    // ─── CARGA / DESCARGA ─────────────────────────────────────────────

    public void cargarVidas(UUID uuid, String username) {
        int vidas = plugin.getDatabase().getVidas(uuid.toString());
        if (vidas == -1) {
            int iniciales = plugin.getConfig().getInt("vidas.iniciales", 3);
            plugin.getDatabase().setVidas(uuid.toString(), username, iniciales);
            cacheVidas.put(uuid, iniciales);
        } else {
            cacheVidas.put(uuid, vidas);
        }
        plugin.getDatabase().registrarConexion(uuid.toString());
        tiempoConexion.put(uuid, System.currentTimeMillis());

        // Cargar estado del abismo
        if (plugin.getDatabase().getEnAbismo(uuid.toString())) {
            enAbismo.add(uuid);
        }
    }

    public void descargarVidas(UUID uuid) {
        if (cacheVidas.containsKey(uuid)) {
            Player p = Bukkit.getPlayer(uuid);
            String username = p != null ? p.getName() : uuid.toString();
            plugin.getDatabase().setVidas(uuid.toString(), username, cacheVidas.get(uuid));
            cacheVidas.remove(uuid);
        }
        if (tiempoConexion.containsKey(uuid)) {
            long segundos = (System.currentTimeMillis() - tiempoConexion.get(uuid)) / 1000;
            plugin.getDatabase().sumarTiempoJugado(uuid.toString(), segundos);
            tiempoConexion.remove(uuid);
        }
        cancelarDonacion(uuid);
    }

    // ─── VIDAS ────────────────────────────────────────────────────────

    public int getVidas(UUID uuid) {
        return cacheVidas.getOrDefault(uuid, 0);
    }

    public void setVidas(UUID uuid, String username, int cantidad) {
        Player p = Bukkit.getPlayer(uuid);
        int max = p != null ? rangoConfig.getMaxVidas(p)
                : plugin.getConfig().getInt("vidas.maximas", 5);
        int final_ = Math.max(0, Math.min(cantidad, max));
        cacheVidas.put(uuid, final_);
        plugin.getDatabase().setVidas(uuid.toString(), username, final_);
    }

    public void quitarVida(UUID uuid, String username) {
        int actual = getVidas(uuid);
        if (actual <= 0) return;
        setVidas(uuid, username, actual - 1);
    }

    public void darVida(UUID uuid, String username, int cantidad) {
        setVidas(uuid, username, getVidas(uuid) + cantidad);
    }

    public int getMaxVidasJugador(Player player) {
        return rangoConfig.getMaxVidas(player);
    }

    // ─── COOLDOWN VIDA EXTRA ──────────────────────────────────────────

    public boolean verificarCooldownVidaExtra(Player player) {
        UUID uuid = player.getUniqueId();
        long cooldown = rangoConfig.getCooldownVidaExtra(player);

        if (cooldownsVidaExtra.containsKey(uuid)) {
            long transcurrido = (System.currentTimeMillis() - cooldownsVidaExtra.get(uuid)) / 1000;
            if (transcurrido < cooldown) {
                long falta = cooldown - transcurrido;
                player.sendMessage("§cDebes esperar §e" + formatearTiempo(falta) +
                        " §cpara usar otra vida extra.");
                return false;
            }
        }
        cooldownsVidaExtra.put(uuid, System.currentTimeMillis());
        return true;
    }

    // ─── TIEMPO JUGADO ────────────────────────────────────────────────

    public boolean tiene24HorasJugadas(UUID uuid) {
        long acumulado = plugin.getDatabase().getTiempoJugadoSegundos(uuid.toString());
        if (tiempoConexion.containsKey(uuid)) {
            acumulado += (System.currentTimeMillis() - tiempoConexion.get(uuid)) / 1000;
        }
        return acumulado >= SEGUNDOS_24H;
    }

    public String formatearTiempo(long segundos) {
        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;
        long segs = segundos % 60;
        if (horas > 0) return horas + "h " + minutos + "m";
        if (minutos > 0) return minutos + "m " + segs + "s";
        return segs + "s";
    }

    // ─── DONACIONES ───────────────────────────────────────────────────

    public void iniciarDonacion(Player donante) {
        UUID uuid = donante.getUniqueId();

        if (getVidas(uuid) < 2) {
            donante.sendMessage("§cNecesitas al menos §e2 vidas §cpara donar.");
            return;
        }

        if (!donante.hasPermission("discordbridge.vidas.bypass") && !tiene24HorasJugadas(uuid)) {
            long jugadas = plugin.getDatabase().getTiempoJugadoSegundos(uuid.toString());
            if (tiempoConexion.containsKey(uuid)) {
                jugadas += (System.currentTimeMillis() - tiempoConexion.get(uuid)) / 1000;
            }
            long falta = SEGUNDOS_24H - jugadas;
            donante.sendMessage("§cNecesitas §e24 horas §cde tiempo jugado para donar vidas.");
            donante.sendMessage("§7Te faltan: §e" + formatearTiempo(falta));
            return;
        }

        if (cooldownsDonar.containsKey(uuid)) {
            long cooldown = rangoConfig.getCooldownDonar(donante);
            long transcurrido = (System.currentTimeMillis() - cooldownsDonar.get(uuid)) / 1000;
            if (transcurrido < cooldown) {
                long falta = cooldown - transcurrido;
                donante.sendMessage("§cDebes esperar §e" + formatearTiempo(falta) +
                        " §cpara donar otra vida.");
                return;
            }
        }

        donacionesPendientes.put(uuid, new DonacionPendiente(
                donante.getLocation().clone(),
                System.currentTimeMillis()
        ));

        donante.sendMessage("§6§l[Vidas] §e¿A quién quieres donar una vida?");
        donante.sendMessage("§7Escribe el nombre del jugador en el chat. Tienes §e30 segundos§7.");
        donante.sendMessage("§7Si te mueves, se cancelará.");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (donacionesPendientes.containsKey(uuid)) {
                donacionesPendientes.remove(uuid);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendMessage("§cDonación cancelada por tiempo de espera.");
            }
        }, TIMEOUT_DONACION * 20L);
    }

    public void procesarRespuestaDonacion(Player donante, String respuesta) {
        UUID uuid = donante.getUniqueId();
        if (!donacionesPendientes.containsKey(uuid)) return;

        Player objetivo = Bukkit.getPlayerExact(respuesta);

        if (objetivo == null || !objetivo.isOnline()) {
            donante.sendMessage("§cEse jugador no está conectado.");
            return;
        }

        if (objetivo.getUniqueId().equals(uuid)) {
            donante.sendMessage("§cNo puedes donarte una vida a ti mismo.");
            return;
        }

        double distanciaMax = rangoConfig.getDistanciaDonar(donante);
        if (!donante.getWorld().equals(objetivo.getWorld()) ||
                donante.getLocation().distance(objetivo.getLocation()) > distanciaMax) {
            donante.sendMessage("§cEse jugador está demasiado lejos. " +
                    "Tu distancia máxima es §e" + (int) distanciaMax + " bloques§c.");
            return;
        }

        if (getVidas(objetivo.getUniqueId()) >= rangoConfig.getMaxVidas(objetivo)) {
            donante.sendMessage("§cEse jugador ya tiene el máximo de vidas.");
            return;
        }

        donacionesPendientes.remove(uuid);
        cooldownsDonar.put(uuid, System.currentTimeMillis());

        quitarVida(uuid, donante.getName());
        darVida(objetivo.getUniqueId(), objetivo.getName(), 1);

        donante.sendMessage("§a§l[Vidas] §eHas donado una vida a §a" + objetivo.getName() +
                "§e. Ahora tienes §c" + getVidas(uuid) + " vidas§e.");
        objetivo.sendMessage("§a§l[Vidas] §e¡" + donante.getName() +
                " §ete ha donado una vida! Ahora tienes §c" +
                getVidas(objetivo.getUniqueId()) + " vidas§e.");
    }

    public boolean tieneDonacionPendiente(UUID uuid) {
        return donacionesPendientes.containsKey(uuid);
    }

    public void cancelarDonacion(UUID uuid) {
        donacionesPendientes.remove(uuid);
    }

    // ─── CLASE INTERNA ────────────────────────────────────────────────

    public static class DonacionPendiente {
        public final org.bukkit.Location ubicacionInicial;
        public final long timestamp;

        public DonacionPendiente(org.bukkit.Location ubicacion, long timestamp) {
            this.ubicacionInicial = ubicacion;
            this.timestamp = timestamp;
        }
    }

    private final java.util.Set<UUID> enAbismo = new java.util.HashSet<>();

    public void entrarAbismo(UUID uuid) {
        enAbismo.add(uuid);
        plugin.getDatabase().setEnAbismo(uuid.toString(), true);
        plugin.getDatabase().incrementarVecesAbismo(uuid.toString()); // ← AÑADIR
    }

    public int getVecesAbismo(UUID uuid) {
        return plugin.getDatabase().getVecesAbismo(uuid.toString());
    }

    public void salirAbismo(UUID uuid) {
        enAbismo.remove(uuid);
        plugin.getDatabase().setEnAbismo(uuid.toString(), false);
    }

    public boolean estaEnAbismo(UUID uuid) {
        return enAbismo.contains(uuid);
    }
}