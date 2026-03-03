package com.vortex.discordbridge.clan;

import com.vortex.discordbridge.DiscordBridge;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ClanManager {

    private final DiscordBridge plugin;
    private Economy economy;

    private final Map<String, Clan> clanes = new HashMap<>();
    // uuid → clan id para búsqueda rápida
    private final Map<UUID, String> jugadorAClan = new HashMap<>();
    // uuid pendiente de invitación → clan id
    private final Map<UUID, String> invitacionesPendientes = new HashMap<>();

    private File clanConfigFile;
    private FileConfiguration clanConfig;

    public ClanManager(DiscordBridge plugin) {
        this.plugin = plugin;
        inicializarVault();
        inicializarTablas();
        cargarConfig();
        cargarClanes();
    }

    // ─── VAULT ────────────────────────────────────────────────────────────

    private void inicializarVault() {
        var rsp = plugin.getServer().getServicesManager()
                .getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
            plugin.getLogger().info("Vault hookeado correctamente.");
        } else {
            plugin.getLogger().severe("Vault no encontrado — el sistema de clanes no funcionará.");
        }
    }

    public boolean cobrar(Player player, double cantidad) {
        if (economy == null) return false;
        if (!economy.has(player, cantidad)) {
            player.sendMessage("§cNo tienes suficiente dinero. Necesitas §e" +
                    economy.format(cantidad) + "§c.");
            return false;
        }
        economy.withdrawPlayer(player, cantidad);
        return true;
    }

    public double getBalance(Player player) {
        return economy != null ? economy.getBalance(player) : 0;
    }

    public String formatear(double cantidad) {
        return economy != null ? economy.format(cantidad) : String.valueOf(cantidad);
    }

    // ─── CONFIG ───────────────────────────────────────────────────────────

    public void cargarConfig() {
        clanConfigFile = new File(plugin.getDataFolder(), "clan.yml");
        if (!clanConfigFile.exists()) plugin.saveResource("clan.yml", false);
        clanConfig = YamlConfiguration.loadConfiguration(clanConfigFile);
    }

    public FileConfiguration getClanConfig() { return clanConfig; }

    // ─── BD ───────────────────────────────────────────────────────────────

    private void inicializarTablas() {
        try {
            var conn = plugin.getDatabase().getConnection();
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS clanes (
                    id TEXT PRIMARY KEY,
                    nombre TEXT NOT NULL,
                    prefijo_id TEXT DEFAULT '',
                    prefijo_display TEXT DEFAULT '',
                    max_miembros INTEGER DEFAULT 3,
                    slots_comprados INTEGER DEFAULT 0,
                    vidas_auxiliares INTEGER DEFAULT 0
                )
            """);
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS clan_miembros (
                    clan_id TEXT NOT NULL,
                    uuid TEXT NOT NULL,
                    nombre TEXT NOT NULL,
                    rol TEXT NOT NULL,
                    PRIMARY KEY (clan_id, uuid)
                )
            """);
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS clan_banner (
                    clan_id TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    radio INTEGER NOT NULL,
                    nivel_zona INTEGER DEFAULT 0,
                    tiene_explosiones INTEGER DEFAULT 0,
                    tiene_pvp INTEGER DEFAULT 0,
                    tiene_interacciones INTEGER DEFAULT 0,
                    tiene_vidas_auxiliares INTEGER DEFAULT 0
                )
            """);
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS clan_upgrades (
                    clan_id TEXT NOT NULL,
                    upgrade TEXT NOT NULL,
                    PRIMARY KEY (clan_id, upgrade)
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creando tablas de clanes: " + e.getMessage());
        }
    }

    // ─── CARGAR CLANES ────────────────────────────────────────────────────

    public void cargarClanes() {
        clanes.clear();
        jugadorAClan.clear();

        try {
            var conn = plugin.getDatabase().getConnection();

            // Cargar clanes
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM clanes");
            while (rs.next()) {
                Clan clan = new Clan(
                        rs.getString("id"),
                        rs.getString("nombre"),
                        rs.getInt("max_miembros")
                );
                clan.prefijoid = rs.getString("prefijo_id");
                clan.prefijoDisplay = rs.getString("prefijo_display");
                clan.slotsComprados = rs.getInt("slots_comprados");
                clan.vidasAuxiliares = rs.getInt("vidas_auxiliares");
                clanes.put(clan.id, clan);
            }
            rs.close();

            // Cargar miembros
            ResultSet rsM = conn.createStatement()
                    .executeQuery("SELECT * FROM clan_miembros");
            while (rsM.next()) {
                String clanId = rsM.getString("clan_id");
                Clan clan = clanes.get(clanId);
                if (clan == null) continue;

                UUID uuid = UUID.fromString(rsM.getString("uuid"));
                ClanMember member = new ClanMember(
                        uuid,
                        rsM.getString("nombre"),
                        ClanMember.Rol.valueOf(rsM.getString("rol"))
                );
                clan.miembros.put(uuid, member);
                jugadorAClan.put(uuid, clanId);
            }
            rsM.close();

            // Cargar banners
            ResultSet rsB = conn.createStatement()
                    .executeQuery("SELECT * FROM clan_banner");
            while (rsB.next()) {
                String clanId = rsB.getString("clan_id");
                Clan clan = clanes.get(clanId);
                if (clan == null) continue;

                ClanBanner banner = new ClanBanner(
                        rsB.getString("world"),
                        rsB.getInt("x"),
                        rsB.getInt("y"),
                        rsB.getInt("z"),
                        rsB.getInt("radio")
                );
                banner.nivelZona = rsB.getInt("nivel_zona");
                banner.tieneExplosiones = rsB.getInt("tiene_explosiones") == 1;
                banner.tienePvp = rsB.getInt("tiene_pvp") == 1;
                banner.tieneInteracciones = rsB.getInt("tiene_interacciones") == 1;
                banner.tieneVidasAuxiliares = rsB.getInt("tiene_vidas_auxiliares") == 1;
                clan.banner = banner;
            }
            rsB.close();

        } catch (SQLException e) {
            plugin.getLogger().severe("Error cargando clanes: " + e.getMessage());
        }

        plugin.getLogger().info("Clanes: " + clanes.size() + " clan(es) cargado(s).");
    }

    // ─── CREAR / DISOLVER ─────────────────────────────────────────────────

    public boolean crearClan(Player lider, String nombre) {
        if (jugadorAClan.containsKey(lider.getUniqueId())) {
            lider.sendMessage("§cYa perteneces a un clan.");
            return false;
        }

        // Verificar nombre único
        for (Clan c : clanes.values()) {
            if (c.nombre.equalsIgnoreCase(nombre)) {
                lider.sendMessage("§cYa existe un clan con ese nombre.");
                return false;
            }
        }

        double precio = clanConfig.getDouble("clan.precio-crear", 1000.0);
        if (!cobrar(lider, precio)) return false;

        String id = UUID.randomUUID().toString().substring(0, 8);
        int maxMiembros = clanConfig.getInt("clan.max-miembros-base", 3);

        Clan clan = new Clan(id, nombre, maxMiembros);
        ClanMember miembro = new ClanMember(
                lider.getUniqueId(), lider.getName(), ClanMember.Rol.LIDER
        );
        clan.miembros.put(lider.getUniqueId(), miembro);

        clanes.put(id, clan);
        jugadorAClan.put(lider.getUniqueId(), id);

        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement(
                    "INSERT INTO clanes (id, nombre, max_miembros) VALUES (?, ?, ?)"
            );
            ps.setString(1, id);
            ps.setString(2, nombre);
            ps.setInt(3, maxMiembros);
            ps.executeUpdate();
            ps.close();

            guardarMiembro(id, miembro);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creando clan: " + e.getMessage());
        }

        lider.sendMessage("§a§l[Clan] §eClan §6" + nombre + " §ecreado con ID: §8" + id);
        return true;
    }

    public void disolverClan(String clanId) {
        Clan clan = clanes.get(clanId);
        if (clan == null) return;

        // Notificar miembros
        clan.miembros.keySet().forEach(uuid -> {
            jugadorAClan.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage("§c§l[Clan] §eEl clan §6" +
                    clan.nombre + " §eha sido disuelto.");
        });

        // Eliminar banner si existe
        if (clan.banner != null) {
            eliminarBannerFisico(clan);
        }

        clanes.remove(clanId);

        try {
            var conn = plugin.getDatabase().getConnection();
            for (String tabla : List.of("clanes", "clan_miembros",
                    "clan_banner", "clan_upgrades")) {
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + tabla + " WHERE " +
                                (tabla.equals("clanes") ? "id" : "clan_id") + " = ?"
                );
                ps.setString(1, clanId);
                ps.executeUpdate();
                ps.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error disolviendo clan: " + e.getMessage());
        }
    }

    // ─── INVITACIONES ─────────────────────────────────────────────────────

    public void invitar(Player invitador, Player objetivo, Clan clan) {
        if (jugadorAClan.containsKey(objetivo.getUniqueId())) {
            invitador.sendMessage("§cEse jugador ya pertenece a un clan.");
            return;
        }
        if (clan.estaLleno()) {
            invitador.sendMessage("§cEl clan está lleno.");
            return;
        }

        invitacionesPendientes.put(objetivo.getUniqueId(), clan.id);

        invitador.sendMessage("§a§l[Clan] §eInvitación enviada a §6" +
                objetivo.getName() + "§e.");
        objetivo.sendMessage("§a§l[Clan] §e¡Fuiste invitado al clan §6" +
                clan.nombre + "§e!");
        objetivo.sendMessage("§7Usa §e/clan aceptar §7o §e/clan rechazar §7en los próximos 60 segundos.");

        // Timeout de 60 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (invitacionesPendientes.containsKey(objetivo.getUniqueId())) {
                invitacionesPendientes.remove(objetivo.getUniqueId());
                objetivo.sendMessage("§c§l[Clan] §eLa invitación expiró.");
            }
        }, 20L * 60);
    }

    public boolean aceptarInvitacion(Player player) {
        String clanId = invitacionesPendientes.remove(player.getUniqueId());
        if (clanId == null) {
            player.sendMessage("§cNo tienes invitaciones pendientes.");
            return false;
        }

        Clan clan = clanes.get(clanId);
        if (clan == null || clan.estaLleno()) {
            player.sendMessage("§cEl clan ya no está disponible o está lleno.");
            return false;
        }

        ClanMember miembro = new ClanMember(
                player.getUniqueId(), player.getName(), ClanMember.Rol.MIEMBRO
        );
        clan.miembros.put(player.getUniqueId(), miembro);
        jugadorAClan.put(player.getUniqueId(), clanId);
        guardarMiembro(clanId, miembro);

        player.sendMessage("§a§l[Clan] §eTe uniste al clan §6" + clan.nombre + "§e.");
        // Notificar al resto
        clan.miembros.keySet().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && !p.equals(player))
                p.sendMessage("§a§l[Clan] §e§6" + player.getName() +
                        " §ese unió al clan.");
        });
        return true;
    }

    public void rechazarInvitacion(Player player) {
        String clanId = invitacionesPendientes.remove(player.getUniqueId());
        if (clanId == null) {
            player.sendMessage("§cNo tienes invitaciones pendientes.");
            return;
        }
        player.sendMessage("§c§l[Clan] §eInvitación rechazada.");
        Clan clan = clanes.get(clanId);
        if (clan != null) {
            Player lider = Bukkit.getPlayer(clan.getLider().uuid);
            if (lider != null)
                lider.sendMessage("§c§l[Clan] §e§6" + player.getName() +
                        " §erechazó la invitación.");
        }
    }

    // ─── EXPULSAR ─────────────────────────────────────────────────────────

    public void expulsar(Clan clan, UUID objetivo) {
        ClanMember miembro = clan.miembros.remove(objetivo);
        if (miembro == null) return;
        jugadorAClan.remove(objetivo);

        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement(
                    "DELETE FROM clan_miembros WHERE clan_id = ? AND uuid = ?"
            );
            ps.setString(1, clan.id);
            ps.setString(2, objetivo.toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error expulsando miembro: " + e.getMessage());
        }

        Player p = Bukkit.getPlayer(objetivo);
        if (p != null) p.sendMessage("§c§l[Clan] §eFuiste expulsado del clan §6" +
                clan.nombre + "§e.");
    }

    // ─── BANNER ───────────────────────────────────────────────────────────

    public boolean colocarBanner(Clan clan, String world, int x, int y, int z) {
        int radio = getRadioBase() + (clan.banner != null ? clan.banner.nivelZona * 10 : 0);

        for (var region : plugin.getProtectionManager().getTodasLasRegiones()) {
            if (!region.worldName.equals(world)) continue;
            int dx = Math.abs(x - region.x);
            int dy = Math.abs(y - region.y);
            int dz = Math.abs(z - region.z);
            if (dx <= (radio + region.radio) &&
                    dy <= (radio + region.radio) &&
                    dz <= (radio + region.radio)) {
                return false;
            }
        }

        // Verificar solapamiento con otras protecciones
        for (Clan otroClan : clanes.values()) {
            if (otroClan.id.equals(clan.id)) continue;
            if (otroClan.banner == null || !otroClan.banner.colocado) continue;
            if (!otroClan.banner.worldName.equals(world)) continue;

            if (Math.abs(x - otroClan.banner.x) <= (radio + otroClan.banner.radio) &&
                    Math.abs(y - otroClan.banner.y) <= (radio + otroClan.banner.radio) &&
                    Math.abs(z - otroClan.banner.z) <= (radio + otroClan.banner.radio)) {
                return false;
            }
        }

        // Verificar solapamiento con protection stones
        for (var region : plugin.getProtectionManager().getTodasLasRegiones()) {
            if (!region.worldName.equals(world)) continue;
            if (Math.abs(x - region.x) <= (radio + region.radio) &&
                    Math.abs(y - region.y) <= (radio + region.radio) &&
                    Math.abs(z - region.z) <= (radio + region.radio)) {
                return false;
            }
        }

        if (clan.banner == null) {
            clan.banner = new ClanBanner(world, x, y, z, radio);
        } else {
            clan.banner.worldName = world;
            clan.banner.x = x;
            clan.banner.y = y;
            clan.banner.z = z;
            clan.banner.radio = radio;
            clan.banner.colocado = true;
        }

        guardarBanner(clan);
        return true;
    }

    private void eliminarBannerFisico(Clan clan) {
        if (clan.banner == null) return;
        org.bukkit.World world = Bukkit.getWorld(clan.banner.worldName);
        if (world != null) {
            world.getBlockAt(clan.banner.x, clan.banner.y, clan.banner.z)
                    .setType(org.bukkit.Material.AIR);
        }
    }

    // ─── MEJORAS ──────────────────────────────────────────────────────────

    public boolean comprarMejoraZona(Player player, Clan clan) {
        if (clan.banner == null) {
            player.sendMessage("§cEl clan no tiene estandarte colocado.");
            return false;
        }
        if (clan.banner.nivelZona >= 2) {
            player.sendMessage("§cYa tienes el máximo nivel de zona.");
            return false;
        }

        int nivelSiguiente = clan.banner.nivelZona + 1;
        var precios = clanConfig.getDoubleList("clan.estandarte.mejoras.zona-grande.precios");
        double precio = precios.get(nivelSiguiente - 1);

        // Verificar solapamiento antes de mejorar
        int nuevoRadio = getRadioBase() + (nivelSiguiente * 10);
        String conflicto = obtenerConflictoMejora(clan, nuevoRadio);
        if (conflicto != null) {
            player.sendMessage(conflicto);
            return false;
        }

        if (!cobrar(player, precio)) return false;

        clan.banner.nivelZona = nivelSiguiente;
        clan.banner.radio = nuevoRadio;
        guardarBanner(clan);

        player.sendMessage("§a§l[Clan] §eZona ampliada a §6" + nuevoRadio + "x" + nuevoRadio + "§e.");
        return true;
    }

    public boolean comprarMejora(Player player, Clan clan, ClanUpgrade upgrade) {
        if (clan.banner == null) {
            player.sendMessage("§cEl clan no tiene estandarte colocado.");
            return false;
        }

        String configKey;
        boolean yaComprado;
        switch (upgrade) {
            case EXPLOSIONES -> { configKey = "clan.estandarte.mejoras.explosiones.precio";
                yaComprado = clan.banner.tieneExplosiones; }
            case PVP -> { configKey = "clan.estandarte.mejoras.pvp.precio";
                yaComprado = clan.banner.tienePvp; }
            case INTERACCIONES -> { configKey = "clan.estandarte.mejoras.interacciones.precio";
                yaComprado = clan.banner.tieneInteracciones; }
            case VIDAS_AUXILIARES -> { configKey = "clan.vidas-auxiliares.precio-mejora";
                yaComprado = clan.banner.tieneVidasAuxiliares; }
            default -> { return false; }
        }

        if (yaComprado) {
            player.sendMessage("§cYa tienes esa mejora.");
            return false;
        }

        double precio = clanConfig.getDouble(configKey, 1500.0);
        if (!cobrar(player, precio)) return false;

        switch (upgrade) {
            case EXPLOSIONES -> clan.banner.tieneExplosiones = true;
            case PVP -> clan.banner.tienePvp = true;
            case INTERACCIONES -> clan.banner.tieneInteracciones = true;
            case VIDAS_AUXILIARES -> clan.banner.tieneVidasAuxiliares = true;
        }

        guardarBanner(clan);
        player.sendMessage("§a§l[Clan] §eMejora §6" + upgrade.name() + " §edesbloqueada.");
        return true;
    }

    private static final int MAX_MIEMBROS_ABSOLUTO = 20;

    public boolean comprarSlotMiembro(Player player, Clan clan) {
        if (clan.maxMiembros >= MAX_MIEMBROS_ABSOLUTO) {
            player.sendMessage("§cYa tienes el máximo de §e" +
                    MAX_MIEMBROS_ABSOLUTO + " §cmiembros posibles.");
            return false;
        }
        double precioBase = clanConfig.getDouble("clan.precio-slot-miembro", 500.0);
        double multiplicador = clanConfig.getDouble("clan.precio-slot-multiplicador", 1.5);
        double precio = precioBase * Math.pow(multiplicador, clan.slotsComprados);

        if (!cobrar(player, precio)) return false;

        clan.slotsComprados++;
        clan.maxMiembros++;

        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement(
                    "UPDATE clanes SET max_miembros = ?, slots_comprados = ? WHERE id = ?"
            );
            ps.setInt(1, clan.maxMiembros);
            ps.setInt(2, clan.slotsComprados);
            ps.setString(3, clan.id);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error comprando slot: " + e.getMessage());
        }

        player.sendMessage("§a§l[Clan] §eSlot de miembro comprado. Máximo: §6" +
                clan.maxMiembros + " §emigembros.");
        return true;
    }

    public boolean comprarVidasAuxiliares(Player player, Clan clan, int cantidad) {
        if (!clan.banner.tieneVidasAuxiliares) {
            player.sendMessage("§cPrimero debes desbloquear la mejora de vidas auxiliares.");
            return false;
        }

        double precioPorVida = clanConfig.getDouble("clan.vidas-auxiliares.precio-por-vida", 500.0);
        double total = precioPorVida * cantidad;

        if (!cobrar(player, total)) return false;

        clan.vidasAuxiliares += cantidad;

        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement(
                    "UPDATE clanes SET vidas_auxiliares = ? WHERE id = ?"
            );
            ps.setInt(1, clan.vidasAuxiliares);
            ps.setString(2, clan.id);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error comprando vidas auxiliares: " + e.getMessage());
        }

        player.sendMessage("§a§l[Clan] §eCompraste §6" + cantidad +
                " §evida(s) auxiliar(es). Stock: §6" + clan.vidasAuxiliares);
        return true;
    }

    public boolean usarVidaAuxiliar(Player lider, Player objetivo, Clan clan) {
        if (!clan.banner.tieneVidasAuxiliares) {
            lider.sendMessage("§cEl clan no tiene la mejora de vidas auxiliares.");
            return false;
        }
        if (clan.vidasAuxiliares <= 0) {
            lider.sendMessage("§cNo tienes vidas auxiliares en stock.");
            return false;
        }
        if (plugin.getVidasManager().estaEnAbismo(objetivo.getUniqueId())) {
            lider.sendMessage("§cNo puedes dar vidas auxiliares a alguien en el Abismo.");
            return false;
        }
        if (!clan.tieneMiembro(objetivo.getUniqueId())) {
            lider.sendMessage("§cEse jugador no es miembro de tu clan.");
            return false;
        }

        int maxVidas = plugin.getVidasManager().getMaxVidasJugador(objetivo);
        if (plugin.getVidasManager().getVidas(objetivo.getUniqueId()) >= maxVidas) {
            lider.sendMessage("§cEse jugador ya tiene el máximo de vidas.");
            return false;
        }

        clan.vidasAuxiliares--;
        plugin.getVidasManager().darVida(objetivo.getUniqueId(), objetivo.getName(), 1);

        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement(
                    "UPDATE clanes SET vidas_auxiliares = ? WHERE id = ?"
            );
            ps.setInt(1, clan.vidasAuxiliares);
            ps.setString(2, clan.id);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error usando vida auxiliar: " + e.getMessage());
        }

        lider.sendMessage("§a§l[Clan] §eDiste una vida auxiliar a §6" + objetivo.getName() + "§e.");
        objetivo.sendMessage("§a§l[Clan] §e¡Tu clan te dio una vida! Ahora tienes §6" +
                plugin.getVidasManager().getVidas(objetivo.getUniqueId()) + " §evidas.");
        return true;
    }

    // ─── PREFIJO ──────────────────────────────────────────────────────────

    public boolean comprarPrefijo(Player player, Clan clan, String prefijoId) {
        var prefijos = clanConfig.getMapList("clan.prefijos");
        Map<?, ?> prefijo = prefijos.stream()
                .filter(p -> prefijoId.equals(p.get("id")))
                .findFirst().orElse(null);

        if (prefijo == null) {
            player.sendMessage("§cPrefijo no encontrado.");
            return false;
        }

        double precio = ((Number) prefijo.get("precio")).doubleValue();
        if (!cobrar(player, precio)) return false;

        clan.prefijoid = prefijoId;
        clan.prefijoDisplay = (String) prefijo.get("display");

        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement(
                    "UPDATE clanes SET prefijo_id = ?, prefijo_display = ? WHERE id = ?"
            );
            ps.setString(1, prefijoId);
            ps.setString(2, clan.prefijoDisplay);
            ps.setString(3, clan.id);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error guardando prefijo: " + e.getMessage());
        }

        player.sendMessage("§a§l[Clan] §ePrefijo §6" + clan.prefijoDisplay + " §eactivado.");
        return true;
    }

    // ─── QUERIES ──────────────────────────────────────────────────────────

    public Clan getClanDeJugador(UUID uuid) {
        String id = jugadorAClan.get(uuid);
        return id != null ? clanes.get(id) : null;
    }

    public Clan getClan(String id) { return clanes.get(id); }

    public Collection<Clan> getTodosLosClanes() { return clanes.values(); }

    public Clan getClanEnBanner(String world, int x, int y, int z) {
        for (Clan clan : clanes.values()) {
            if (clan.banner == null || !clan.banner.colocado) continue;
            if (clan.banner.contiene(world, x, y, z)) return clan;
        }
        return null;
    }

    public Clan getClanBannerEnPosicion(String world, int x, int y, int z) {
        for (Clan clan : clanes.values()) {
            if (clan.banner == null || !clan.banner.colocado) continue;
            if (clan.banner.worldName.equals(world) &&
                    clan.banner.x == x &&
                    clan.banner.y == y &&
                    clan.banner.z == z) return clan;
        }
        return null;
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private int getRadioBase() {
        return clanConfig.getInt("clan.estandarte.radio-base", 10);
    }

    public String obtenerConflictoMejora(Clan clan, int nuevoRadio) {
        for (Clan otroClan : clanes.values()) {
            if (otroClan.id.equals(clan.id)) continue;
            if (otroClan.banner == null || !otroClan.banner.colocado) continue;
            if (!otroClan.banner.worldName.equals(clan.banner.worldName)) continue;
            int dx = Math.abs(clan.banner.x - otroClan.banner.x);
            int dy = Math.abs(clan.banner.y - otroClan.banner.y);
            int dz = Math.abs(clan.banner.z - otroClan.banner.z);
            if (dx <= (nuevoRadio + otroClan.banner.radio) &&
                    dy <= (nuevoRadio + otroClan.banner.radio) &&
                    dz <= (nuevoRadio + otroClan.banner.radio)) {
                return "§cSolaparía con el estandarte del clan §e" + otroClan.nombre +
                        "§c en §e" + otroClan.banner.x + ", " + otroClan.banner.y +
                        ", " + otroClan.banner.z + "§c. Distancia: §e" +
                        Math.max(Math.max(dx, dy), dz) + " bloques";
            }
        }
        for (var region : plugin.getProtectionManager().getTodasLasRegiones()) {
            if (!region.worldName.equals(clan.banner.worldName)) continue;
            int dx = Math.abs(clan.banner.x - region.x);
            int dy = Math.abs(clan.banner.y - region.y);
            int dz = Math.abs(clan.banner.z - region.z);
            if (dx <= (nuevoRadio + region.radio) &&
                    dy <= (nuevoRadio + region.radio) &&
                    dz <= (nuevoRadio + region.radio)) {
                return "§cSolaparía con una protection stone de §e" + region.ownerName +
                        "§c en §e" + region.x + ", " + region.y + ", " + region.z +
                        "§c. Distancia: §e" + Math.max(Math.max(dx, dy), dz) + " bloques";
            }
        }
        return null;
    }

    private void guardarMiembro(String clanId, ClanMember miembro) {
        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement("""
                INSERT OR REPLACE INTO clan_miembros (clan_id, uuid, nombre, rol)
                VALUES (?, ?, ?, ?)
            """);
            ps.setString(1, clanId);
            ps.setString(2, miembro.uuid.toString());
            ps.setString(3, miembro.nombre);
            ps.setString(4, miembro.rol.name());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error guardando miembro: " + e.getMessage());
        }
    }

    private void guardarBanner(Clan clan) {
        if (clan.banner == null) return;
        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement("""
                INSERT OR REPLACE INTO clan_banner
                (clan_id, world, x, y, z, radio, nivel_zona,
                tiene_explosiones, tiene_pvp, tiene_interacciones, tiene_vidas_auxiliares)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """);
            ps.setString(1, clan.id);
            ps.setString(2, clan.banner.worldName);
            ps.setInt(3, clan.banner.x);
            ps.setInt(4, clan.banner.y);
            ps.setInt(5, clan.banner.z);
            ps.setInt(6, clan.banner.radio);
            ps.setInt(7, clan.banner.nivelZona);
            ps.setInt(8, clan.banner.tieneExplosiones ? 1 : 0);
            ps.setInt(9, clan.banner.tienePvp ? 1 : 0);
            ps.setInt(10, clan.banner.tieneInteracciones ? 1 : 0);
            ps.setInt(11, clan.banner.tieneVidasAuxiliares ? 1 : 0);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error guardando banner: " + e.getMessage());
        }
    }

    public void guardarMiembroPublico(String clanId, ClanMember miembro) {
        guardarMiembro(clanId, miembro);
    }

    public void guardarBannerPublico(Clan clan) {
        guardarBanner(clan);
    }

    public org.bukkit.inventory.ItemStack crearItemEstandartePublico() {
        var item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.WHITE_BANNER);
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§6§lEstandarte de Clan")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                net.kyori.adventure.text.Component.text("§7Coloca este estandarte para")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                net.kyori.adventure.text.Component.text("§7activar la protección del clan.")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ));
        var nbt = meta.getPersistentDataContainer();
        nbt.set(new org.bukkit.NamespacedKey(this.plugin, "clan_banner"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public String intentarColocarBanner(Clan clan, String world, int x, int y, int z) {
        int radio = getRadioBase() + (clan.banner != null ? clan.banner.nivelZona * 10 : 0);

        // Verificar solapamiento con otros estandartes
        for (Clan otroClan : clanes.values()) {
            if (otroClan.id.equals(clan.id)) continue;
            if (otroClan.banner == null || !otroClan.banner.colocado) continue;
            if (!otroClan.banner.worldName.equals(world)) continue;

            int dx = Math.abs(x - otroClan.banner.x);
            int dy = Math.abs(y - otroClan.banner.y);
            int dz = Math.abs(z - otroClan.banner.z);

            if (dx <= (radio + otroClan.banner.radio) &&
                    dy <= (radio + otroClan.banner.radio) &&
                    dz <= (radio + otroClan.banner.radio)) {
                return "§cSolaparía con el estandarte del clan §e" + otroClan.nombre +
                        "§c en §e" + otroClan.banner.x + ", " + otroClan.banner.y +
                        ", " + otroClan.banner.z + "§c. Distancia: §e" +
                        Math.max(Math.max(dx, dy), dz) + " bloques";
            }
        }

        // Verificar solapamiento con protection stones
        for (var region : plugin.getProtectionManager().getTodasLasRegiones()) {
            if (!region.worldName.equals(world)) continue;

            int dx = Math.abs(x - region.x);
            int dy = Math.abs(y - region.y);
            int dz = Math.abs(z - region.z);

            if (dx <= (radio + region.radio) &&
                    dy <= (radio + region.radio) &&
                    dz <= (radio + region.radio)) {
                return "§cSolaparía con la protection stone de §e" + region.ownerName +
                        "§c en §e" + region.x + ", " + region.y + ", " + region.z +
                        "§c. Distancia: §e" + Math.max(Math.max(dx, dy), dz) + " bloques";
            }
        }

        // Sin conflictos — colocar
        if (clan.banner == null) {
            clan.banner = new ClanBanner(world, x, y, z, radio);
        } else {
            clan.banner.worldName = world;
            clan.banner.x = x;
            clan.banner.y = y;
            clan.banner.z = z;
            clan.banner.radio = radio;
            clan.banner.colocado = true;
        }

        guardarBanner(clan);
        return null;
    }

    public void reloadConfig() {
        if (clanConfigFile == null) {
            clanConfigFile = new File(plugin.getDataFolder(), "clan.yml");
        }

        clanConfig = YamlConfiguration.loadConfiguration(clanConfigFile);

        try {
            clanConfig.load(clanConfigFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Error recargando clan.yml: " + e.getMessage());
        }

        plugin.getLogger().info("Clan.yml recargado correctamente.");
    }
}