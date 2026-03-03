package com.vortex.discordbridge;

import com.google.gson.JsonObject;
import okhttp3.*;
import java.io.IOException;

public class BotNotifier {

    private final DiscordBridge plugin;
    private final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json");

    public BotNotifier(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    public void enviarAlertaIPNueva(String username, String ip) {
        JsonObject body = new JsonObject();
        body.addProperty("tipo", "ip_nueva");
        body.addProperty("username", username);
        body.addProperty("ip", ip);

        enviarPost("/alerta", body.toString());
    }

    private void enviarPost(String ruta, String jsonBody) {
        String botUrl = plugin.getConfig().getString("bot-url") + ruta;
        String secret = plugin.getConfig().getString("api-secret");

        Request request = new Request.Builder()
                .url(botUrl)
                .header("X-Secret", secret)
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        // Asíncrono para no bloquear el servidor
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                plugin.getLogger().warning("No se pudo notificar al bot: " + e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) {
                response.close();
            }
        });
    }

    public void enviarMensajeChat(String username, String grupo, String mensaje) {
        JsonObject body = new JsonObject();
        body.addProperty("tipo", "chat");
        body.addProperty("username", username);
        body.addProperty("grupo", grupo);
        body.addProperty("mensaje", mensaje);

        enviarPost("/alerta", body.toString());
    }

    public void enviarLogConsola(String mensaje) {
        JsonObject body = new JsonObject();
        body.addProperty("tipo", "consola_log");
        body.addProperty("mensaje", mensaje);

        enviarPost("/alerta", body.toString());
    }

    public void enviarNotificacion(String tipo, String username, String grupo) {
        enviarNotificacion(tipo, username, grupo, null);
    }

    public void enviarNotificacion(String tipo, String username, String grupo, String causa) {
        JsonObject body = new JsonObject();
        body.addProperty("tipo", tipo);
        body.addProperty("username", username);
        if (grupo != null) body.addProperty("grupo", grupo);
        if (causa != null) body.addProperty("causa", causa);
        enviarPost("/alerta", body.toString());
    }

    public void enviarAlertaFuerzaBruta(String username, String ip, int intentos) {
        JsonObject body = new JsonObject();
        body.addProperty("tipo", "fuerza_bruta");
        body.addProperty("username", username);
        body.addProperty("ip", ip);
        body.addProperty("intentos", intentos);

        enviarPost("/alerta", body.toString());
    }
}