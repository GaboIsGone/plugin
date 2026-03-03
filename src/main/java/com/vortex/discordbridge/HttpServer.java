package com.vortex.discordbridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.ResultSet;
import java.util.concurrent.Executors;

public class HttpServer {

    private final DiscordBridge plugin;
    private com.sun.net.httpserver.HttpServer server;
    private final Gson gson = new Gson();

    public HttpServer(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    public void start() {
        try {
            server = com.sun.net.httpserver.HttpServer.create(
                    new InetSocketAddress(3001), 0 // Puerto 3001
            );

            // Rutas HTTP
            server.createContext("/vincular", new VincularHandler());
            server.createContext("/historial-ips", new HistorialIPsHandler());
            server.createContext("/historial-logins", new HistorialLoginsHandler());
            server.createContext("/info-jugador", new InfoJugadorHandler());
            server.createContext("/mensaje-discord", new MensajeDiscordHandler());
            server.createContext("/buscar-ip", new BuscarIPHandler());
            server.createContext("/online", new OnlinePlayersHandler());
            server.createContext("/desvincular", new DesvincularHandler());

            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            plugin.getLogger().info("Servidor HTTP del plugin iniciado en puerto 3001");

        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo iniciar el servidor HTTP: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // Valida la clave secreta en cada request
    private boolean validarSecret(HttpExchange exchange) {
        String secret = exchange.getRequestHeaders().getFirst("X-Secret");
        return plugin.getConfig().getString("api-secret").equals(secret);
    }

    private void enviarRespuesta(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    // ─── HANDLER: Completar vinculación ──────────────────────────────
    class VincularHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!validarSecret(exchange)) {
                enviarRespuesta(exchange, 403, "{\"error\":\"No autorizado\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes());
            JsonObject json = gson.fromJson(body, JsonObject.class);

            String codigo = json.get("codigo").getAsString().toUpperCase();
            String discordId = json.get("discord_id").getAsString();
            String discordTag = json.get("discord_tag").getAsString();

            // Verificar con seguridad
            LinkManager.ResultadoVerificacion resultado =
                    plugin.getLinkManager().verificarConSeguridad(codigo, discordId);

            switch (resultado) {
                case COOLDOWN -> {
                    enviarRespuesta(exchange, 429,
                            "{\"error\":\"Espera 30 segundos antes de intentarlo de nuevo.\"}");
                    return;
                }
                case BLOQUEADO -> {
                    enviarRespuesta(exchange, 429,
                            "{\"error\":\"Demasiados intentos fallidos. Espera 5 minutos.\"}");
                    return;
                }
                case INVALIDO -> {
                    enviarRespuesta(exchange, 400,
                            "{\"error\":\"Código inválido o expirado.\"}");
                    return;
                }
                case VALIDO -> {
                    // Obtener el link ya verificado
                    // Como verificarCodigo ya lo consumió internamente, necesitamos el link
                }
            }

            // Obtener datos del link (ya fue validado arriba)
            LinkManager.PendingLink link = plugin.getLinkManager().getUltimoLinkValido();
            if (link == null) {
                enviarRespuesta(exchange, 500, "{\"error\":\"Error interno.\"}");
                return;
            }

            plugin.getDatabase().guardarVinculacion(
                    link.uuid.toString(), link.username, discordId, discordTag
            );

            JsonObject resp = new JsonObject();
            resp.addProperty("success", true);
            resp.addProperty("username", link.username);
            enviarRespuesta(exchange, 200, gson.toJson(resp));
        }
    }

    // ─── HANDLER: Historial de IPs ────────────────────────────────────
    class HistorialIPsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!validarSecret(exchange)) {
                enviarRespuesta(exchange, 403, "{\"error\":\"No autorizado\"}");
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String username = query.replace("username=", "");

            try {
                ResultSet rs = plugin.getDatabase().getHistorialIPs(username);
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append("{")
                            .append("\"ip\":\"").append(rs.getString("ip")).append("\",")
                            .append("\"fecha\":\"").append(rs.getString("fecha")).append("\",")
                            .append("\"es_nueva\":").append(rs.getInt("es_nueva") == 1)
                            .append("}");
                    first = false;
                }
                sb.append("]");

                enviarRespuesta(exchange, 200, sb.toString());
            } catch (Exception e) {
                enviarRespuesta(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // ─── HANDLER: Historial de Logins ────────────────────────────────
    class HistorialLoginsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!validarSecret(exchange)) {
                enviarRespuesta(exchange, 403, "{\"error\":\"No autorizado\"}");
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String username = query.replace("username=", "");

            try {
                ResultSet rs = plugin.getDatabase().getHistorialLogins(username);
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append("{")
                            .append("\"ip\":\"").append(rs.getString("ip")).append("\",")
                            .append("\"exitoso\":").append(rs.getInt("exitoso") == 1).append(",")
                            .append("\"motivo_fallo\":\"").append(
                                    rs.getString("motivo_fallo") != null ? rs.getString("motivo_fallo") : "").append("\",")
                            .append("\"cliente\":\"").append(
                                    rs.getString("mc_version") != null ? rs.getString("mc_version") : "").append("\",")
                            .append("\"fecha\":\"").append(rs.getString("fecha")).append("\"")
                            .append("}");
                    first = false;
                }
                sb.append("]");

                enviarRespuesta(exchange, 200, sb.toString());
            } catch (Exception e) {
                enviarRespuesta(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // ─── HANDLER: Info de jugador ─────────────────────────────────────
    class InfoJugadorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!validarSecret(exchange)) {
                enviarRespuesta(exchange, 403, "{\"error\":\"No autorizado\"}");
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String username = query.replace("username=", "");

            try {
                ResultSet rs = plugin.getDatabase().getVinculacionPorUsername(username);
                if (rs.next()) {
                    JsonObject resp = new JsonObject();
                    resp.addProperty("uuid", rs.getString("uuid"));
                    resp.addProperty("username", rs.getString("username"));
                    resp.addProperty("discord_id", rs.getString("discord_id"));
                    resp.addProperty("discord_tag", rs.getString("discord_tag"));
                    resp.addProperty("fecha_vinculacion", rs.getString("fecha_vinculacion"));
                    enviarRespuesta(exchange, 200, gson.toJson(resp));
                } else {
                    enviarRespuesta(exchange, 404, "{\"error\":\"Jugador no vinculado\"}");
                }
            } catch (Exception e) {
                enviarRespuesta(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    class MensajeDiscordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!validarSecret(exchange)) {
                enviarRespuesta(exchange, 403, "{\"error\":\"No autorizado\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes());
            JsonObject json = gson.fromJson(body, JsonObject.class);

            String username = json.get("username").getAsString();
            String mensaje = json.get("mensaje").getAsString();

            // Detectar si el mensaje tiene prefijo de respuesta (↩ nombre)
            String mensajeFinal;
            if (mensaje.startsWith("(↩ ")) {
                // Separar el prefijo de respuesta del mensaje real
                int fin = mensaje.indexOf(") ");
                if (fin != -1) {
                    String respondidoA = mensaje.substring(3, fin); // Extrae el nombre
                    String textoReal = mensaje.substring(fin + 2);  // Extrae el mensaje
                    mensajeFinal = "§8(↩ §7" + respondidoA + "§8) §r" + textoReal;
                } else {
                    mensajeFinal = mensaje;
                }
            } else {
                mensajeFinal = mensaje;
            }

            final String lineaFinal = "§9[Discord] §7" + username + " §f» §r" + mensajeFinal;

            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getServer().broadcastMessage(lineaFinal)
            );

            enviarRespuesta(exchange, 200, "{\"ok\":true}");
        }
    }

    class BuscarIPHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!validarSecret(exchange)) {
                enviarRespuesta(exchange, 403, "{\"error\":\"No autorizado\"}");
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String ip = query.replace("ip=", "");

            try {
                ResultSet rs = plugin.getDatabase().buscarJugadoresPorIP(ip);
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;

                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append("{")
                            .append("\"username\":\"").append(rs.getString("username")).append("\",")
                            .append("\"veces\":").append(rs.getInt("veces")).append(",")
                            .append("\"primera_vez\":\"").append(rs.getString("primera_vez")).append("\",")
                            .append("\"ultima_vez\":\"").append(rs.getString("ultima_vez")).append("\"")
                            .append("}");
                    first = false;
                }
                sb.append("]");

                enviarRespuesta(exchange, 200, sb.toString());
            } catch (Exception e) {
                enviarRespuesta(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    class OnlinePlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!validarSecret(exchange)) {
                enviarRespuesta(exchange, 403, "{\"error\":\"No autorizado\"}");
                return;
            }

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;

            for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
                if (!first) sb.append(",");

                // Obtener grupo de LuckPerms
                String grupo = "default";
                try {
                    net.luckperms.api.model.user.User user = net.luckperms.api.LuckPermsProvider
                            .get().getUserManager().getUser(player.getUniqueId());
                    if (user != null) grupo = user.getPrimaryGroup();
                } catch (Exception ignored) {}

                sb.append("{")
                        .append("\"username\":\"").append(player.getName()).append("\",")
                        .append("\"grupo\":\"").append(grupo).append("\"")
                        .append("}");
                first = false;
            }
            sb.append("]");

            enviarRespuesta(exchange, 200, sb.toString());
        }
    }

    class DesvincularHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!validarSecret(exchange)) {
                enviarRespuesta(exchange, 403, "{\"error\":\"No autorizado\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes());
            JsonObject json = gson.fromJson(body, JsonObject.class);
            String username = json.get("username").getAsString();

            try {
                // Obtener discord_id antes de eliminar para notificar al jugador
                ResultSet rs = plugin.getDatabase().getVinculacionPorUsername(username);
                String discordId = null;
                if (rs.next()) {
                    discordId = rs.getString("discord_id");
                }
                rs.close();

                plugin.getDatabase().eliminarVinculacion(username);

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.addProperty("discord_id", discordId != null ? discordId : "");
                enviarRespuesta(exchange, 200, gson.toJson(resp));

            } catch (Exception e) {
                enviarRespuesta(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }
}