package com.vortex.discordbridge.protection;

import com.vortex.discordbridge.DiscordBridge;
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

public class ProtectionManager {

    private final DiscordBridge plugin;

    // Cache de regiones en memoria
    private final Map<String, ProtectionRegion> regiones = new HashMap<>();

    // Cache de tipos de piedra
    private final Map<String, StoneType> stoneTypes = new HashMap<>();

    private File stonesFile;
    private FileConfiguration stonesConfig;

    public ProtectionManager(DiscordBridge plugin) {
        this.plugin = plugin;
        inicializarTablas();
        cargarStones();
        cargarRegiones();
    }

    // ─── INICIALIZAR BD ───────────────────────────────────────────────────

    private void inicializarTablas() {
        try {
            plugin.getDatabase().getConnection().createStatement().execute("""
                CREATE TABLE IF NOT EXISTS protecciones (
                    id TEXT PRIMARY KEY,
                    owner_uuid TEXT NOT NULL,
                    owner_name TEXT NOT NULL,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    stone_type TEXT NOT NULL,
                    radio INTEGER NOT NULL,
                    activa INTEGER NOT NULL DEFAULT 1
                )
            """);
            plugin.getDatabase().getConnection().createStatement().execute("""
                CREATE TABLE IF NOT EXISTS proteccion_miembros (
                    proteccion_id TEXT NOT NULL,
                    member_uuid TEXT NOT NULL,
                    member_name TEXT NOT NULL,
                    PRIMARY KEY (proteccion_id, member_uuid)
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creando tablas de protección: " + e.getMessage());
        }
    }

    // ─── CARGAR STONES.YML ────────────────────────────────────────────────

    public void cargarStones() {
        stoneTypes.clear();
        stonesFile = new File(plugin.getDataFolder(), "stones.yml");

        if (!stonesFile.exists()) {
            plugin.saveResource("stones.yml", false);
        }

        stonesConfig = YamlConfiguration.loadConfiguration(stonesFile);
        var section = stonesConfig.getConfigurationSection("stones");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            var s = section.getConfigurationSection(key);
            if (s == null) continue;

            StoneType st = new StoneType(
                    key,
                    s.getString("material", "STONE"),
                    s.getString("itemsadder-id", ""),
                    s.getString("display-name", key),
                    s.getInt("radio", 15),
                    s.getInt("limite-por-jugador", 1),
                    s.getString("descripcion", ""),
                    s.getString("permiso", "")
            );
            stoneTypes.put(key, st);
        }
        plugin.getLogger().info("ProtectionStones: " + stoneTypes.size() + " tipos cargados.");
    }

    // ─── CARGAR REGIONES DE BD ────────────────────────────────────────────

    public void cargarRegiones() {
        regiones.clear();
        try {
            ResultSet rs = plugin.getDatabase().getConnection()
                    .createStatement()
                    .executeQuery("SELECT * FROM protecciones");

            while (rs.next()) {
                String id = rs.getString("id");
                ProtectionRegion region = new ProtectionRegion(
                        id,
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("owner_name"),
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getString("stone_type"),
                        rs.getInt("radio"),
                        rs.getInt("activa") == 1
                );
                regiones.put(id, region);
            }
            rs.close();

            // Cargar miembros
            ResultSet rsM = plugin.getDatabase().getConnection()
                    .createStatement()
                    .executeQuery("SELECT * FROM proteccion_miembros");

            while (rsM.next()) {
                String pid = rsM.getString("proteccion_id");
                ProtectionRegion region = regiones.get(pid);
                if (region != null) {
                    region.miembros.add(UUID.fromString(rsM.getString("member_uuid")));
                }
            }
            rsM.close();

        } catch (SQLException e) {
            plugin.getLogger().severe("Error cargando regiones: " + e.getMessage());
        }
    }

    // ─── CREAR REGIÓN ─────────────────────────────────────────────────────

    public boolean crearRegion(Player owner, String worldName, int x, int y, int z,
                               StoneType stoneType) {

        // Verificar solapamiento con banners de clanes
        for (var clan : plugin.getClanManager().getTodosLosClanes()) {
            if (clan.banner == null || !clan.banner.colocado) continue;
            if (!clan.banner.worldName.equals(worldName)) continue;

            int dx = Math.abs(x - clan.banner.x);
            int dy = Math.abs(y - clan.banner.y);
            int dz = Math.abs(z - clan.banner.z);

            if (dx <= (stoneType.radio + clan.banner.radio) &&
                    dy <= (stoneType.radio + clan.banner.radio) &&
                    dz <= (stoneType.radio + clan.banner.radio)) {
                owner.sendMessage("§cSolaparía con el estandarte del clan §e" +
                        clan.nombre + "§c en §e" + clan.banner.x + ", " +
                        clan.banner.y + ", " + clan.banner.z + "§c. Distancia: §e" +
                        Math.max(Math.max(dx, dy), dz) + " bloques");
                return false;
            }
        }

        // Verificar solapamiento de zonas
        for (ProtectionRegion r : regiones.values()) {
            if (!r.worldName.equals(worldName)) continue;
            if (Math.abs(x - r.x) <= (stoneType.radio + r.radio) &&
                    Math.abs(y - r.y) <= (stoneType.radio + r.radio) &&
                    Math.abs(z - r.z) <= (stoneType.radio + r.radio)) {
                owner.sendMessage("§cNo puedes colocar una protección aquí, se solaparía con la de §e" +
                        r.ownerName + "§c.");
                return false;
            }
        }

        // Verificar límite
        long count = regiones.values().stream()
                .filter(r -> r.ownerUUID.equals(owner.getUniqueId()) &&
                        r.stoneTypeId.equals(stoneType.id))
                .count();

        if (count >= stoneType.limitePorJugador) {
            owner.sendMessage("§cYa tienes el máximo de §e" + stoneType.limitePorJugador +
                    " §cprotecciones de tipo §e" + stoneType.displayName + "§c.");
            return false;
        }

        // Verificar solapamiento
        if (getRegionEnPosicion(worldName, x, y, z) != null) {
            owner.sendMessage("§cYa existe una protección en esa ubicación.");
            return false;
        }

        String id = UUID.randomUUID().toString();
        ProtectionRegion region = new ProtectionRegion(
                id, owner.getUniqueId(), owner.getName(),
                worldName, x, y, z, stoneType.id, stoneType.radio, true
        );
        regiones.put(id, region);

        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement("""
                INSERT INTO protecciones
                (id, owner_uuid, owner_name, world, x, y, z, stone_type, radio, activa)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
            """);
            ps.setString(1, id);
            ps.setString(2, owner.getUniqueId().toString());
            ps.setString(3, owner.getName());
            ps.setString(4, worldName);
            ps.setInt(5, x);
            ps.setInt(6, y);
            ps.setInt(7, z);
            ps.setString(8, stoneType.id);
            ps.setInt(9, stoneType.radio);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error guardando región: " + e.getMessage());
            return false;
        }

        return true;
    }

    // ─── ELIMINAR REGIÓN ──────────────────────────────────────────────────

    public void eliminarRegion(String id) {
        regiones.remove(id);
        try {
            PreparedStatement ps = plugin.getDatabase().getConnection()
                    .prepareStatement("DELETE FROM protecciones WHERE id = ?");
            ps.setString(1, id);
            ps.executeUpdate();
            ps.close();

            PreparedStatement ps2 = plugin.getDatabase().getConnection()
                    .prepareStatement("DELETE FROM proteccion_miembros WHERE proteccion_id = ?");
            ps2.setString(1, id);
            ps2.executeUpdate();
            ps2.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error eliminando región: " + e.getMessage());
        }
    }

    // ─── MIEMBROS ─────────────────────────────────────────────────────────

    public void agregarMiembro(String regionId, UUID memberUUID, String memberName) {
        ProtectionRegion region = regiones.get(regionId);
        if (region == null) return;
        region.miembros.add(memberUUID);

        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement("""
                INSERT OR IGNORE INTO proteccion_miembros
                (proteccion_id, member_uuid, member_name) VALUES (?, ?, ?)
            """);
            ps.setString(1, regionId);
            ps.setString(2, memberUUID.toString());
            ps.setString(3, memberName);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error agregando miembro: " + e.getMessage());
        }
    }

    public void quitarMiembro(String regionId, UUID memberUUID) {
        ProtectionRegion region = regiones.get(regionId);
        if (region == null) return;
        region.miembros.remove(memberUUID);

        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement(
                    "DELETE FROM proteccion_miembros WHERE proteccion_id = ? AND member_uuid = ?"
            );
            ps.setString(1, regionId);
            ps.setString(2, memberUUID.toString());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error quitando miembro: " + e.getMessage());
        }
    }

    // ─── TRANSFERIR DUEÑO ─────────────────────────────────────────────────

    public void transferirDueno(String regionId, UUID nuevoOwner, String nuevoNombre) {
        ProtectionRegion region = regiones.get(regionId);
        if (region == null) return;

        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement(
                    "UPDATE protecciones SET owner_uuid = ?, owner_name = ? WHERE id = ?"
            );
            ps.setString(1, nuevoOwner.toString());
            ps.setString(2, nuevoNombre);
            ps.setString(3, regionId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error transfiriendo dueño: " + e.getMessage());
        }

        // Recargar desde BD para actualizar el objeto inmutable
        cargarRegiones();
    }

    // ─── ACTIVAR/DESACTIVAR (abismo) ──────────────────────────────────────

    public void setActiva(String regionId, boolean activa) {
        ProtectionRegion region = regiones.get(regionId);
        if (region == null) return;
        region.activa = activa;

        try {
            PreparedStatement ps = plugin.getDatabase().getConnection().prepareStatement(
                    "UPDATE protecciones SET activa = ? WHERE id = ?"
            );
            ps.setInt(1, activa ? 1 : 0);
            ps.setString(2, regionId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error actualizando estado: " + e.getMessage());
        }
    }

    public void desactivarProteccionesDeJugador(UUID ownerUUID) {
        regiones.values().stream()
                .filter(r -> r.ownerUUID.equals(ownerUUID))
                .forEach(r -> setActiva(r.id, false));
    }

    public void activarProteccionesDeJugador(UUID ownerUUID) {
        regiones.values().stream()
                .filter(r -> r.ownerUUID.equals(ownerUUID))
                .forEach(r -> setActiva(r.id, true));
    }

    // ─── QUERIES ──────────────────────────────────────────────────────────

    public ProtectionRegion getRegionEnPosicion(String world, int x, int y, int z) {
        for (ProtectionRegion r : regiones.values()) {
            if (r.activa && r.x == x && r.y == y && r.z == z && r.worldName.equals(world)) {
                return r;
            }
        }
        return null;
    }

    public ProtectionRegion getRegionQueContiene(String world, int x, int y, int z) {
        for (ProtectionRegion r : regiones.values()) {
            if (r.activa && r.contiene(world, x, y, z)) return r;
        }
        return null;
    }

    public List<ProtectionRegion> getRegionesDeJugador(UUID ownerUUID) {
        return regiones.values().stream()
                .filter(r -> r.ownerUUID.equals(ownerUUID))
                .toList();
    }

    public Map<String, StoneType> getStoneTypes() { return stoneTypes; }

    public StoneType getStoneType(String id) { return stoneTypes.get(id); }

    public ProtectionRegion getRegion(String id) { return regiones.get(id); }

    public StoneType detectarTipoPiedra(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        if (!item.hasItemMeta()) return null;

        // Verificar NBT tag — única forma segura
        var nbt = item.getItemMeta().getPersistentDataContainer();
        var key = new org.bukkit.NamespacedKey(plugin, "protection_stone_type");
        if (nbt.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
            String typeId = nbt.get(key, org.bukkit.persistence.PersistentDataType.STRING);
            return stoneTypes.get(typeId);
        }

        return null;
    }

    public org.bukkit.inventory.ItemStack crearItemPiedra(StoneType stoneType) {
        org.bukkit.inventory.ItemStack item;

        // Si tiene ItemsAdder configurado, usar ese item
        if (stoneType.itemsAdderId != null && !stoneType.itemsAdderId.isEmpty()) {
            try {
                dev.lone.itemsadder.api.CustomStack cs =
                        dev.lone.itemsadder.api.CustomStack.getInstance(stoneType.itemsAdderId);
                if (cs != null) {
                    item = cs.getItemStack();
                } else {
                    item = new org.bukkit.inventory.ItemStack(
                            org.bukkit.Material.valueOf(stoneType.material)
                    );
                }
            } catch (Exception e) {
                item = new org.bukkit.inventory.ItemStack(
                        org.bukkit.Material.valueOf(stoneType.material)
                );
            }
        } else {
            item = new org.bukkit.inventory.ItemStack(
                    org.bukkit.Material.valueOf(stoneType.material)
            );
        }

        // Aplicar nombre y lore
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(stoneType.displayName)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        meta.lore(java.util.List.of(
                net.kyori.adventure.text.Component.text("§7" + stoneType.descripcion)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                net.kyori.adventure.text.Component.text("§8Radio: §e" + stoneType.radio + " bloques")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                net.kyori.adventure.text.Component.text("§8Límite: §e" + stoneType.limitePorJugador + " por jugador")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                net.kyori.adventure.text.Component.text("§8ID: §7" + stoneType.id)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ));

        // NBT tag para identificarlo como protection stone
        var nbt = meta.getPersistentDataContainer();
        nbt.set(
                new org.bukkit.NamespacedKey(plugin, "protection_stone_type"),
                org.bukkit.persistence.PersistentDataType.STRING,
                stoneType.id
        );

        item.setItemMeta(meta);
        return item;
    }

    public java.util.Collection<ProtectionRegion> getTodasLasRegiones() {
        return regiones.values();
    }
}