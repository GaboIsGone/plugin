package com.vortex.discordbridge;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.sql.*;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            // Crear carpeta del plugin si no existe
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            String dbPath = dataFolder + File.separator +
                    plugin.getConfig().getString("database.path");

            // Conectar a SQLite
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            // Crear tablas si no existen
            createTables();
            plugin.getLogger().info("Base de datos inicializada.");

        } catch (SQLException e) {
            plugin.getLogger().severe("Error al inicializar la base de datos: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();

        // Tabla de jugadores vinculados
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS vinculaciones (
                uuid TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                discord_id TEXT UNIQUE,
                discord_tag TEXT,
                fecha_vinculacion TEXT
            )
        """);

        // Tabla de historial de IPs
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS historial_ips (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                username TEXT NOT NULL,
                ip TEXT NOT NULL,
                fecha TEXT NOT NULL,
                es_nueva INTEGER DEFAULT 0
            )
        """);

        // Tabla de historial de logins
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS historial_logins (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                username TEXT NOT NULL,
                ip TEXT NOT NULL,
                exitoso INTEGER NOT NULL,
                motivo_fallo TEXT,
                os_info TEXT,
                mc_version TEXT,
                fecha TEXT NOT NULL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS vidas (
                uuid TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                vidas INTEGER NOT NULL DEFAULT 3
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS tiempo_jugado (
                uuid TEXT PRIMARY KEY,
                segundos_jugados INTEGER NOT NULL DEFAULT 0,
                ultima_conexion TEXT
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS abismo (
                uuid TEXT PRIMARY KEY,
                en_abismo INTEGER NOT NULL DEFAULT 0,
                fecha_entrada TEXT,
                veces_abismo INTEGER NOT NULL DEFAULT 0
            )
        """);

        stmt.close();
    }

    // ─── VINCULACIONES ────────────────────────────────────────────────

    public void guardarVinculacion(String uuid, String username,
                                   String discordId, String discordTag) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO vinculaciones 
                (uuid, username, discord_id, discord_tag, fecha_vinculacion)
                VALUES (?, ?, ?, ?, datetime('now'))
            """);
            ps.setString(1, uuid);
            ps.setString(2, username);
            ps.setString(3, discordId);
            ps.setString(4, discordTag);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error guardando vinculación: " + e.getMessage());
        }
    }

    public ResultSet getVinculacionPorDiscord(String discordId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM vinculaciones WHERE discord_id = ?"
        );
        ps.setString(1, discordId);
        return ps.executeQuery();
    }

    public ResultSet getVinculacionPorUsername(String username) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM vinculaciones WHERE LOWER(username) = LOWER(?)"
        );
        ps.setString(1, username);
        return ps.executeQuery();
    }

    public void eliminarVinculacion(String username) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM vinculaciones WHERE LOWER(username) = LOWER(?)"
        );
        ps.setString(1, username);
        ps.executeUpdate();
        ps.close();
    }

    // ─── HISTORIAL DE IPs ─────────────────────────────────────────────

    public boolean esIpNueva(String uuid, String ip) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM historial_ips WHERE uuid = ? AND ip = ?"
            );
            ps.setString(1, uuid);
            ps.setString(2, ip);
            ResultSet rs = ps.executeQuery();
            boolean esNueva = rs.getInt(1) == 0;
            rs.close();
            ps.close();
            return esNueva;
        } catch (SQLException e) {
            return false;
        }
    }

    public void registrarIP(String uuid, String username, String ip, boolean esNueva) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO historial_ips (uuid, username, ip, fecha, es_nueva)
                VALUES (?, ?, ?, datetime('now'), ?)
            """);
            ps.setString(1, uuid);
            ps.setString(2, username);
            ps.setString(3, ip);
            ps.setInt(4, esNueva ? 1 : 0);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registrando IP: " + e.getMessage());
        }
    }

    public ResultSet getHistorialIPs(String username) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("""
            SELECT ip, fecha, es_nueva FROM historial_ips 
            WHERE LOWER(username) = LOWER(?)
            ORDER BY fecha DESC LIMIT 20
        """);
        ps.setString(1, username);
        return ps.executeQuery();
    }

    // ─── HISTORIAL DE LOGINS ──────────────────────────────────────────

    public void registrarLogin(String uuid, String username, String ip,
                               boolean exitoso, String motivoFallo,
                               String osInfo, String mcVersion) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO historial_logins 
                (uuid, username, ip, exitoso, motivo_fallo, os_info, mc_version, fecha)
                VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
            """);
            ps.setString(1, uuid);
            ps.setString(2, username);
            ps.setString(3, ip);
            ps.setInt(4, exitoso ? 1 : 0);
            ps.setString(5, motivoFallo);
            ps.setString(6, osInfo);
            ps.setString(7, mcVersion);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registrando login: " + e.getMessage());
        }
    }

    public ResultSet getHistorialLogins(String username) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("""
            SELECT ip, exitoso, motivo_fallo, os_info, mc_version, fecha 
            FROM historial_logins 
            WHERE LOWER(username) = LOWER(?)
            ORDER BY fecha DESC LIMIT 20
        """);
        ps.setString(1, username);
        return ps.executeQuery();
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error cerrando BD: " + e.getMessage());
        }
    }

    // Contar fallos consecutivos recientes (últimos 10 minutos)
    public int contarFallosRecientes(String uuid) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
            SELECT COUNT(*) FROM historial_logins
            WHERE uuid = ?
            AND exitoso = 0
            AND fecha >= datetime('now', '-10 minutes')
        """);
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            int count = rs.getInt(1);
            rs.close();
            ps.close();
            return count;
        } catch (SQLException e) {
            return 0;
        }
    }

    // Buscar todos los jugadores que usaron una IP
    public ResultSet buscarJugadoresPorIP(String ip) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("""
        SELECT DISTINCT username, uuid,
               COUNT(*) as veces,
               MIN(fecha) as primera_vez,
               MAX(fecha) as ultima_vez
        FROM historial_ips
        WHERE ip = ?
        GROUP BY uuid
        ORDER BY ultima_vez DESC
    """);
        ps.setString(1, ip);
        return ps.executeQuery();
    }

    // ─── VIDAS ────────────────────────────────────────────────────────────

    public int getVidas(String uuid) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT vidas FROM vidas WHERE uuid = ?"
            );
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            int vidas = rs.next() ? rs.getInt("vidas") : -1;
            rs.close(); ps.close();
            return vidas;
        } catch (SQLException e) { return -1; }
    }

    public void setVidas(String uuid, String username, int vidas) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO vidas (uuid, username, vidas)
            VALUES (?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET vidas = ?, username = ?
        """);
            ps.setString(1, uuid);
            ps.setString(2, username);
            ps.setInt(3, vidas);
            ps.setInt(4, vidas);
            ps.setString(5, username);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error seteando vidas: " + e.getMessage());
        }
    }

    public void registrarConexion(String uuid) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO tiempo_jugado (uuid, segundos_jugados, ultima_conexion)
            VALUES (?, 0, datetime('now'))
            ON CONFLICT(uuid) DO UPDATE SET ultima_conexion = datetime('now')
        """);
            ps.setString(1, uuid);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registrando conexión: " + e.getMessage());
        }
    }

    public void sumarTiempoJugado(String uuid, long segundos) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
            UPDATE tiempo_jugado SET segundos_jugados = segundos_jugados + ?
            WHERE uuid = ?
        """);
            ps.setLong(1, segundos);
            ps.setString(2, uuid);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error sumando tiempo: " + e.getMessage());
        }
    }

    public long getTiempoJugadoSegundos(String uuid) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT segundos_jugados FROM tiempo_jugado WHERE uuid = ?"
            );
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            long tiempo = rs.next() ? rs.getLong("segundos_jugados") : 0;
            rs.close(); ps.close();
            return tiempo;
        } catch (SQLException e) { return 0; }
    }

    public void setEnAbismo(String uuid, boolean valor) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO abismo (uuid, en_abismo, fecha_entrada)
            VALUES (?, ?, datetime('now'))
            ON CONFLICT(uuid) DO UPDATE SET 
                en_abismo = ?,
                fecha_entrada = CASE WHEN ? = 1 THEN datetime('now') 
                                     ELSE fecha_entrada END
        """);
            ps.setString(1, uuid);
            ps.setInt(2, valor ? 1 : 0);
            ps.setInt(3, valor ? 1 : 0);
            ps.setInt(4, valor ? 1 : 0);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error seteando abismo: " + e.getMessage());
        }
    }

    public boolean getEnAbismo(String uuid) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT en_abismo FROM abismo WHERE uuid = ?"
            );
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            boolean valor = rs.next() && rs.getInt("en_abismo") == 1;
            rs.close(); ps.close();
            return valor;
        } catch (SQLException e) { return false; }
    }

    public long getSegundosEnAbismo(String uuid) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
            SELECT CAST((julianday('now') - julianday(fecha_entrada)) * 86400 AS INTEGER)
            AS segundos FROM abismo WHERE uuid = ? AND en_abismo = 1
        """);
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            long segundos = rs.next() ? rs.getLong("segundos") : 0;
            rs.close(); ps.close();
            return segundos;
        } catch (SQLException e) { return 0; }
    }

    public void reducirTiempoAbismo(String uuid, long segundos) {
        try {
            // Adelantar la fecha de entrada para simular que lleva más tiempo
            PreparedStatement ps = connection.prepareStatement("""
            UPDATE abismo 
            SET fecha_entrada = datetime(fecha_entrada, ? || ' seconds')
            WHERE uuid = ? AND en_abismo = 1
        """);
            ps.setString(1, "-" + segundos);
            ps.setString(2, uuid);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error reduciendo tiempo abismo: " + e.getMessage());
        }
    }

    public int getVecesAbismo(String uuid) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT veces_abismo FROM abismo WHERE uuid = ?"
            );
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            int veces = rs.next() ? rs.getInt("veces_abismo") : 0;
            rs.close(); ps.close();
            return veces;
        } catch (SQLException e) { return 0; }
    }

    public void incrementarVecesAbismo(String uuid) {
        try {
            PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO abismo (uuid, en_abismo, veces_abismo)
            VALUES (?, 0, 1)
            ON CONFLICT(uuid) DO UPDATE SET veces_abismo = veces_abismo + 1
        """);
            ps.setString(1, uuid);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error incrementando veces_abismo: " + e.getMessage());
        }
    }

    public Connection getConnection() { return connection; }
}