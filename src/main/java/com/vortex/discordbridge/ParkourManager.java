package com.vortex.discordbridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ParkourManager {

    private final DiscordBridge plugin;

    // Jugadores que están haciendo el parkour → último checkpoint alcanzado
    private final Map<UUID, Integer> checkpointActual = new HashMap<>();

    // Tiempo en que el jugador inició el parkour (para mostrar su tiempo)
    private final Map<UUID, Long> tiempoInicio = new HashMap<>();

    // Archivo de configuración del parkour
    private File parkourFile;
    private FileConfiguration parkourConfig;

    // Horas que reduce cada completado
    private static final long HORAS_REDUCCION = 8;
    private static final long SEGUNDOS_REDUCCION = HORAS_REDUCCION * 3600;

    public ParkourManager(DiscordBridge plugin) {
        this.plugin = plugin;
        cargarConfig();
    }

    // ─── CONFIG ───────────────────────────────────────────────────────────

    private void cargarConfig() {
        parkourFile = new File(plugin.getDataFolder(), "parkour.yml");
        if (!parkourFile.exists()) {
            try {
                parkourFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear parkour.yml");
            }
        }
        parkourConfig = YamlConfiguration.loadConfiguration(parkourFile);
    }

    private void guardarConfig() {
        try {
            parkourConfig.save(parkourFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar parkour.yml");
        }
    }

    private void guardarLocation(String key, Location loc) {
        parkourConfig.set(key + ".world", loc.getWorld().getName());
        parkourConfig.set(key + ".x", loc.getBlockX());
        parkourConfig.set(key + ".y", loc.getBlockY());
        parkourConfig.set(key + ".z", loc.getBlockZ());
        guardarConfig();
    }

    private Location cargarLocation(String key) {
        if (!parkourConfig.contains(key)) return null;
        String worldName = parkourConfig.getString(key + ".world");
        int x = parkourConfig.getInt(key + ".x");
        int y = parkourConfig.getInt(key + ".y");
        int z = parkourConfig.getInt(key + ".z");
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    // ─── SETTERS ──────────────────────────────────────────────────────────

    public void setInicio(Location loc) {
        guardarLocation("inicio", loc);
    }

    public void setCheckpoint(int numero, Location loc) {
        guardarLocation("checkpoints." + numero, loc);
    }

    public void setFinal(Location loc) {
        guardarLocation("final", loc);
    }

    // ─── GETTERS ──────────────────────────────────────────────────────────

    public Location getInicio() {
        return cargarLocation("inicio");
    }

    public Location getFinal() {
        return cargarLocation("final");
    }

    public List<Location> getCheckpoints() {
        List<Location> lista = new ArrayList<>();
        if (!parkourConfig.contains("checkpoints")) return lista;
        for (String key : parkourConfig.getConfigurationSection("checkpoints").getKeys(false)) {
            Location loc = cargarLocation("checkpoints." + key);
            if (loc != null) lista.add(loc);
        }
        return lista;
    }

    public int getTotalCheckpoints() {
        if (!parkourConfig.contains("checkpoints")) return 0;
        return parkourConfig.getConfigurationSection("checkpoints").getKeys(false).size();
    }

    public boolean estaConfigurado() {
        return getInicio() != null && getFinal() != null;
    }

    // ─── LÓGICA DEL PARKOUR ───────────────────────────────────────────────

    public void iniciarParkour(Player player) {
        checkpointActual.put(player.getUniqueId(), -1); // -1 = en el inicio
        tiempoInicio.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendMessage("§6§l[Parkour] §e¡Parkour iniciado! Llega al final para reducir §c" +
                HORAS_REDUCCION + "h §ede tu condena.");
        if (getTotalCheckpoints() > 0) {
            player.sendMessage("§7Checkpoints disponibles: §e" + getTotalCheckpoints());
        }
    }

    public boolean estaEnParkour(UUID uuid) {
        return checkpointActual.containsKey(uuid);
    }

    public void alcanzarCheckpoint(Player player, int numero) {
        int actual = checkpointActual.getOrDefault(player.getUniqueId(), -1);
        // Solo avanzar si es el siguiente checkpoint en orden
        if (numero != actual + 1) return;

        checkpointActual.put(player.getUniqueId(), numero);
        player.sendMessage("§a§l[Parkour] §eCheckpoint §6" + (numero + 1) + "§e alcanzado!");
        player.playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    public Location getCheckpointActual(UUID uuid) {
        int idx = checkpointActual.getOrDefault(uuid, -1);
        if (idx == -1) return getInicio();
        List<Location> cps = getCheckpoints();
        if (idx >= cps.size()) return getInicio();
        return cps.get(idx);
    }

    public void completarParkour(Player player) {
        if (!estaEnParkour(player.getUniqueId())) return;

        // Calcular tiempo que tardó
        long ms = System.currentTimeMillis() - tiempoInicio.getOrDefault(
                player.getUniqueId(), System.currentTimeMillis()
        );
        long segundosTardados = ms / 1000;

        // Limpiar estado del parkour
        checkpointActual.remove(player.getUniqueId());
        tiempoInicio.remove(player.getUniqueId());

        // Reducir tiempo en el abismo
        plugin.getDatabase().reducirTiempoAbismo(
                player.getUniqueId().toString(), SEGUNDOS_REDUCCION
        );

        // Verificar si ya puede salir
        long segundosRestantes = plugin.getDatabase()
                .getSegundosEnAbismo(player.getUniqueId().toString());
        long falta = Math.max(0, 86400 - segundosRestantes);

        player.playSound(player.getLocation(),
                org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        player.sendTitle(
                "§a§l¡COMPLETADO!",
                "§7Tiempo: §e" + plugin.getVidasManager().formatearTiempo(segundosTardados),
                10, 60, 20
        );

        player.sendMessage("§a§l[Parkour] §e¡Completado en §6" +
                plugin.getVidasManager().formatearTiempo(segundosTardados) + "§e!");
        player.sendMessage("§aReduciste §c" + HORAS_REDUCCION +
                "h §ade tu condena.");

        if (falta <= 0) {
            // ¡Libre!
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getVidasListener().liberarDelAbismoPublico(player);
            }, 40L);
        } else {
            player.sendMessage("§7Tiempo restante: §c" +
                    plugin.getVidasManager().formatearTiempo(falta));
            // Teleportar al inicio sin checkpoints
            Location inicio = getInicio();
            if (inicio != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                        player.teleport(inicio), 40L
                );
            }
            // Actualizar nametag
            plugin.getAbismoBossBarManager().mostrarBossBar(player);
        }
    }

    public void cancelarParkour(UUID uuid) {
        checkpointActual.remove(uuid);
        tiempoInicio.remove(uuid);
    }

    // Si cae al vacío, teleportar al último checkpoint
    public void manejarCaida(Player player) {
        if (!estaEnParkour(player.getUniqueId())) return;
        Location checkpoint = getCheckpointActual(player.getUniqueId());
        if (checkpoint == null) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.teleport(checkpoint);
            player.setHealth(20.0);
            player.sendMessage("§c§l[Parkour] §cHas caído. §7Vuelves al último checkpoint.");
        }, 1L);
    }
}