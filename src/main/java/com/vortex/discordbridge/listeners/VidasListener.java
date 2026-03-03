package com.vortex.discordbridge.listeners;

import com.vortex.discordbridge.DiscordBridge;
import com.vortex.discordbridge.VidasManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;

public class VidasListener implements Listener {

    private final DiscordBridge plugin;
    private final java.util.Set<UUID> pendienteAbismo = new java.util.HashSet<>();

    public VidasListener(DiscordBridge plugin) {
        this.plugin = plugin;
    }

    // ─── JOIN ─────────────────────────────────────────────────────────────
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getVidasManager().cargarVidas(
                player.getUniqueId(),
                player.getName()
        );
    }

    // ─── JOIN — restaurar abismo si aplica ────────────────────────────────
    @EventHandler
    public void onJoinAbismo(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.getVidasManager().estaEnAbismo(player.getUniqueId())) return;

            long segundos = plugin.getDatabase()
                    .getSegundosEnAbismo(player.getUniqueId().toString());

            if (segundos >= 86400) {
                liberarDelAbismoPublico(player);
                return;
            }

            World abismo = Bukkit.getWorld("abismo");
            if (abismo == null) return;

            player.teleport(new Location(abismo, 0.50, 77.00, 0.50));
            player.setGameMode(GameMode.ADVENTURE);
            plugin.getAbismoBossBarManager().mostrarBossBar(player);

            long falta = 86400 - segundos;
            player.sendMessage("");
            player.sendMessage("§4§l[Abismo] §cSigues en el Abismo.");
            player.sendMessage("§7Tiempo restante: §e" + plugin.getVidasManager().formatearTiempo(falta));
            player.sendMessage("§7Recuerda que puedes completar el §eparkour §7para salir antes.");
            player.sendMessage("");
        }, 20L);
    }

    // ─── QUIT ─────────────────────────────────────────────────────────────
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getAbismoBossBarManager().quitarBossBar(player);
        plugin.getVidasManager().descargarVidas(player.getUniqueId());
        plugin.getProtectionListener().limpiarJugador(player.getUniqueId());
    }

    // ─── MUERTE ───────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        VidasManager vm = plugin.getVidasManager();

        String causa = traducirCausaMuerte(event);

        // Si ya está en el abismo, respawn silencioso sin tocar vidas
        if (vm.estaEnAbismo(victim.getUniqueId())) {
            event.setDeathMessage(null);
            pendienteAbismo.add(victim.getUniqueId());
            return;
        }

        vm.quitarVida(victim.getUniqueId(), victim.getName());
        int vidasRestantes = vm.getVidas(victim.getUniqueId());

        if (vidasRestantes > 0) {
            // Mensaje privado al que murió
            victim.sendMessage("§cHas perdido una vida. §7Vidas restantes: §e" + vidasRestantes);

            // Anuncio global
            Bukkit.getOnlinePlayers().forEach(p ->
                    p.sendMessage("§8[§4☠§8] §c" + victim.getName() +
                            " §7ha perdido una vida. §8(§e" + vidasRestantes + " §7restantes§8)")
            );
            return;
        }

        // 0 vidas — primera vez que entra al abismo
        event.setDeathMessage(null);
        victim.getWorld().getPlayers().forEach(p ->
                p.sendMessage("§4☠ §c" + victim.getName() + " §7ha perdido §4todas sus vidas§7. Sus protecciones quedaron §4libres§7.")
        );

        pendienteAbismo.add(victim.getUniqueId());
        vm.entrarAbismo(victim.getUniqueId());
        // Desactivar protecciones mientras está en el abismo
        plugin.getProtectionManager().desactivarProteccionesDeJugador(victim.getUniqueId());
        int vecesAbismo = plugin.getVidasManager().getVecesAbismo(victim.getUniqueId());
        plugin.getBotNotifier().enviarNotificacion("sin_vidas", victim.getName(), String.valueOf(vecesAbismo));
    }

    // ─── RESPAWN — teleportar al abismo ───────────────────────────────────
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!pendienteAbismo.contains(player.getUniqueId())) return;
        pendienteAbismo.remove(player.getUniqueId());

        World abismo = Bukkit.getWorld("abismo");
        if (abismo == null) {
            plugin.getLogger().severe("¡El mundo 'abismo' no existe o no está cargado!");
            player.sendMessage("§cError: El abismo no está disponible. Contacta al staff.");
            return;
        }

        boolean primeraVez = plugin.getDatabase()
                .getSegundosEnAbismo(player.getUniqueId().toString()) < 5;

        event.setRespawnLocation(new Location(abismo, 0.50, 77.00, 0.50));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.setGameMode(GameMode.ADVENTURE);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20f);
            plugin.getAbismoBossBarManager().mostrarBossBar(player);

            if (primeraVez) {
                player.getInventory().clear();
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, 60, 1, false, false
                ));
                player.sendTitle(
                        "§4§lABISMO",
                        "§7Tu alma ha sido sentenciada.",
                        10, 80, 20
                );

                // Mensaje detallado con delay para que lo lea después del título
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.sendMessage("");
                    player.sendMessage("§4§l[Abismo] §cHas entrado al Abismo por perder todas tus vidas.");
                    player.sendMessage("§7Fuiste sentenciado a §e24 horas §7en el Abismo.");
                    player.sendMessage("§7No te preocupes, puedes adelantar el tiempo:");
                    player.sendMessage("§8 • §eCompletando el parkour §7varias veces.");
                    player.sendMessage("§8 • §eEsperando offline §7(el tiempo pasa desconectado).");
                    player.sendMessage("");
                }, 40L);
            }
        }, 1L);
    }

    // ─── INTERACCIÓN CON ITEMS ────────────────────────────────────────────
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return;

        String itemVidaExtra = plugin.getConfig().getString("items.vida-extra", "");
        String itemDonarVida = plugin.getConfig().getString("items.donar-vida", "");
        if (itemVidaExtra.isEmpty() && itemDonarVida.isEmpty()) return;

        String itemId = obtenerIdItemsAdder(item);
        if (itemId == null) return;

        VidasManager vm = plugin.getVidasManager();

        if (!itemVidaExtra.isEmpty() && itemId.equals(itemVidaExtra)) {
            event.setCancelled(true);

            if (vm.getVidas(player.getUniqueId()) >= vm.getMaxVidasJugador(player)) {
                player.sendMessage("§cYa tienes el máximo de vidas para tu rango.");
                return;
            }

            if (!vm.verificarCooldownVidaExtra(player)) return;

            vm.darVida(player.getUniqueId(), player.getName(), 1);
            item.setAmount(item.getAmount() - 1);
            player.sendMessage("§a+1 vida! Ahora tienes §e" +
                    vm.getVidas(player.getUniqueId()) + " §avidas.");
        }

        if (!itemDonarVida.isEmpty() && itemId.equals(itemDonarVida)) {
            event.setCancelled(true);
            vm.iniciarDonacion(player);
        }
    }

    // ─── CHAT — donación pendiente + filtro abismo ────────────────────────
    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (plugin.getVidasManager().tieneDonacionPendiente(player.getUniqueId())) {
            event.setCancelled(true);
            String respuesta = PlainTextComponentSerializer.plainText()
                    .serialize(event.message());
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getVidasManager().procesarRespuestaDonacion(player, respuesta)
            );
            return;
        }

        if (plugin.getVidasManager().estaEnAbismo(player.getUniqueId())) {
            event.viewers().removeIf(viewer -> {
                if (!(viewer instanceof Player p)) return false;
                return !plugin.getVidasManager().estaEnAbismo(p.getUniqueId());
            });
        }
    }

    // ─── MOVIMIENTO — cancelar donación ───────────────────────────────────
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getVidasManager().tieneDonacionPendiente(player.getUniqueId())) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        plugin.getVidasManager().cancelarDonacion(player.getUniqueId());
        player.sendMessage("§cDonación cancelada porque te moviste.");
    }

    // ─── COMANDOS — bloquear en el abismo ────────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getVidasManager().estaEnAbismo(player.getUniqueId())) return;
        if (player.hasPermission("discordbridge.abismo.bypass")) return;

        String cmd = event.getMessage().toLowerCase().split(" ")[0];
        Set<String> permitidos = Set.of(
                "/msg", "/tell", "/w", "/r", "/reply",
                "/login", "/l", "/register", "/reg",
                "/changepassword", "/changepass", "/cp"
        );

        if (!permitidos.contains(cmd)) {
            event.setCancelled(true);
            player.sendMessage("§cNo puedes usar comandos en el §4Abismo§c.");
        }
    }

    // ─── TELEPORT — bloquear salida del abismo ────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getVidasManager().estaEnAbismo(player.getUniqueId())) return;
        if (player.hasPermission("discordbridge.abismo.bypass")) return;

        World abismo = Bukkit.getWorld("abismo");
        if (abismo == null) return;

        if (event.getTo() == null || !event.getTo().getWorld().equals(abismo)) {
            event.setCancelled(true);
            player.sendMessage("§cNo puedes salir del §4Abismo§c.");
        }
    }

    // ─── LIBERAR DEL ABISMO ───────────────────────────────────────────────
    public void liberarDelAbismoPublico(Player player) {
        plugin.getVidasManager().salirAbismo(player.getUniqueId());
        plugin.getProtectionManager().activarProteccionesDeJugador(player.getUniqueId());
        plugin.getVidasManager().setVidas(player.getUniqueId(), player.getName(), 1);
        plugin.getAbismoBossBarManager().quitarBossBar(player);

        World mundo = Bukkit.getWorlds().get(0);
        player.teleport(mundo.getSpawnLocation());
        player.setGameMode(GameMode.SURVIVAL);

        player.sendTitle(
                "§a§lLIBRE",
                "§7Has salido del §4Abismo§7. Tienes §c1 §7vida.",
                10, 60, 20
        );
        player.sendMessage("§a§l[Abismo] §eHas sido liberado. §7Cuida esa vida.");
        Bukkit.getOnlinePlayers().forEach(p ->
                p.sendMessage("§8[§a★§8] §e" + player.getName() +
                        " §7ha salido del §4Abismo§7 y ha regresado al mundo.")
        );
    }

    // ─── HELPER ItemsAdder ────────────────────────────────────────────────
    private String obtenerIdItemsAdder(ItemStack item) {
        try {
            dev.lone.itemsadder.api.CustomStack stack =
                    dev.lone.itemsadder.api.CustomStack.byItemStack(item);
            return stack != null ? stack.getNamespacedID() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String traducirCausaMuerte(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        var cause = victim.getLastDamageCause();

        if (cause == null) return "Causa desconocida";

        // Si fue asesinado por otro jugador
        if (victim.getKiller() != null) {
            Player killer = victim.getKiller();
            ItemStack arma = killer.getInventory().getItemInMainHand();
            String nombreArma = traducirMaterial(arma.getType());
            return "Asesinado por **" + killer.getName() + "** con " + nombreArma;
        }

        // Si fue dañado por una entidad (mob)
        if (cause instanceof org.bukkit.event.entity.EntityDamageByEntityEvent ede) {
            String nombreEntidad = traducirEntidad(ede.getDamager().getType());
            return "Asesinado por " + nombreEntidad;
        }

        return switch (cause.getCause()) {
            case FALL -> "Cayó desde gran altura";
            case DROWNING -> "Murió ahogado";
            case FIRE -> "Murió quemado";
            case FIRE_TICK -> "Murió quemado por las llamas";
            case LAVA -> "Cayó en la lava";
            case MAGIC -> "Murió por magia";
            case POISON -> "Murió envenenado";
            case STARVATION -> "Murió de hambre";
            case SUFFOCATION -> "Murió aplastado";
            case THORNS -> "Murió por espinas";
            case VOID -> "Cayó al vacío";
            case WITHER -> "Murió por efecto Wither";
            case LIGHTNING -> "Fulminado por un rayo";
            case FREEZE -> "Murió congelado";
            case SONIC_BOOM -> "Destruido por un estallido sónico";
            case CONTACT -> "Murió al tocar un cactus";
            case CRAMMING -> "Murió aplastado por entidades";
            case HOT_FLOOR -> "Se quemó los pies";
            case PROJECTILE -> "Alcanzado por un proyectil";
            case KILL -> "Eliminado por el servidor";
            default -> "Murió por causas desconocidas";
        };
    }

    private String traducirMaterial(org.bukkit.Material mat) {
        return switch (mat) {
            case WOODEN_SWORD -> "una espada de madera";
            case STONE_SWORD -> "una espada de piedra";
            case IRON_SWORD -> "una espada de hierro";
            case GOLDEN_SWORD -> "una espada de oro";
            case DIAMOND_SWORD -> "una espada de diamante";
            case NETHERITE_SWORD -> "una espada de netherita";
            case WOODEN_AXE -> "un hacha de madera";
            case STONE_AXE -> "un hacha de piedra";
            case IRON_AXE -> "un hacha de hierro";
            case GOLDEN_AXE -> "un hacha de oro";
            case DIAMOND_AXE -> "un hacha de diamante";
            case NETHERITE_AXE -> "un hacha de netherita";
            case BOW -> "un arco";
            case CROSSBOW -> "una ballesta";
            case TRIDENT -> "un tridente";
            case MACE -> "una maza";
            case AIR -> "sus manos";
            default -> "un objeto";
        };
    }

    private String traducirEntidad(org.bukkit.entity.EntityType tipo) {
        return switch (tipo) {
            case ZOMBIE -> "un Zombi";
            case SKELETON -> "un Esqueleto";
            case CREEPER -> "un Creeper";
            case SPIDER -> "una Araña";
            case CAVE_SPIDER -> "una Araña de Cueva";
            case ENDERMAN -> "un Enderman";
            case WITCH -> "una Bruja";
            case BLAZE -> "un Blaze";
            case GHAST -> "un Ghast";
            case WITHER_SKELETON -> "un Esqueleto Wither";
            case WITHER -> "el Wither";
            case ELDER_GUARDIAN -> "un Guardián Mayor";
            case GUARDIAN -> "un Guardián";
            case ENDER_DRAGON -> "el Dragón del End";
            case PILLAGER -> "un Saqueador";
            case RAVAGER -> "un Devastador";
            case VEX -> "un Vex";
            case EVOKER -> "un Invocador";
            case VINDICATOR -> "un Vindicador";
            case DROWNED -> "un Ahogado";
            case HUSK -> "un Zombie Seco";
            case STRAY -> "un Esqueleto Errante";
            case PHANTOM -> "un Fantasma";
            case PIGLIN -> "un Piglin";
            case PIGLIN_BRUTE -> "un Piglin Bruto";
            case HOGLIN -> "un Hoglin";
            case ZOGLIN -> "un Zoglin";
            case SILVERFISH -> "un Silverfish";
            case ENDERMITE -> "un Endermite";
            case SHULKER -> "un Shulker";
            case MAGMA_CUBE -> "un Cubo de Magma";
            case SLIME -> "un Slime";
            case ARROW -> "una flecha";
            case TRIDENT -> "un tridente";
            case FIREBALL -> "una bola de fuego";
            case WITHER_SKULL -> "una calavera Wither";
            default -> "una entidad desconocida";
        };
    }
}