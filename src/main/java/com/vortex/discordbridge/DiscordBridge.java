package com.vortex.discordbridge;

import com.vortex.discordbridge.commands.DarVidasCommand;
import com.vortex.discordbridge.commands.ParkourCommand;
import com.vortex.discordbridge.commands.ProtectionCommand;
import com.vortex.discordbridge.commands.SalirAbismoCommand;
import com.vortex.discordbridge.commands.SetItemVidasCommand;
import com.vortex.discordbridge.commands.VidasCommand;
import com.vortex.discordbridge.commands.VortexCommand;
import com.vortex.discordbridge.commands.ClanChatCommand;
import com.vortex.discordbridge.listeners.AuthMeLoginListener;
import com.vortex.discordbridge.listeners.ChatListener;
import com.vortex.discordbridge.listeners.ParkourListener;
import com.vortex.discordbridge.listeners.PlayerJoinListener;
import com.vortex.discordbridge.listeners.PlayerQuitListener;
import com.vortex.discordbridge.listeners.VidasListener;
import com.vortex.discordbridge.protection.ProtectionManager;
import com.vortex.discordbridge.protection.listeners.ProtectionListener;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class DiscordBridge extends JavaPlugin {

    private static DiscordBridge instance;
    private DatabaseManager database;
    private LinkManager linkManager;
    private HttpServer httpServer;
    private BotNotifier botNotifier;
    private VidasManager vidasManager;
    private AbismoBossBarManager abismoBossBarManager;
    private ParkourManager parkourManager;
    private VidasListener vidasListener;
    private ProtectionManager protectionManager;
    private ProtectionListener protectionListener;
    private com.vortex.discordbridge.clan.ClanManager clanManager;
    private com.vortex.discordbridge.clan.gui.ClanUpgradeGUI clanUpgradeGUI;
    private com.vortex.discordbridge.clan.gui.ClanPrefixGUI clanPrefixGUI;
    private PvPManagerHook pvpManagerHook;
    private LuckPerms luckPerms;
    private ClanChatCommand clanChatCommand;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        database = new DatabaseManager(this);
        database.initialize();

        linkManager = new LinkManager(this);
        botNotifier = new BotNotifier(this);
        vidasManager = new VidasManager(this);
        abismoBossBarManager = new AbismoBossBarManager(this);
        parkourManager = new ParkourManager(this);
        vidasListener = new VidasListener(this);
        protectionManager = new ProtectionManager(this);
        protectionListener = new ProtectionListener(this);
        clanManager = new com.vortex.discordbridge.clan.ClanManager(this);
        clanUpgradeGUI = new com.vortex.discordbridge.clan.gui.ClanUpgradeGUI(this);
        clanPrefixGUI = new com.vortex.discordbridge.clan.gui.ClanPrefixGUI(this);
        pvpManagerHook = new PvPManagerHook(this);
        clanChatCommand = new ClanChatCommand(this);

        httpServer = new HttpServer(this);
        httpServer.start();

        // Listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new AuthMeLoginListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(vidasListener, this);
        getServer().getPluginManager().registerEvents(new ParkourListener(this), this);
        getServer().getPluginManager().registerEvents(protectionListener, this);
        getServer().getPluginManager().registerEvents(
                new com.vortex.discordbridge.clan.listeners.ClanBannerListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.vortex.discordbridge.clan.listeners.ClanChatListener(this), this);

        // Comandos
        getCommand("vincular").setExecutor(new VincularCommand(this));
        getCommand("vidas").setExecutor(new VidasCommand(this));
        getCommand("darvidas").setExecutor(new DarVidasCommand(this));
        getCommand("setitemvidas").setExecutor(new SetItemVidasCommand(this));
        getCommand("parkour").setExecutor(new ParkourCommand(this));
        getCommand("salirabismo").setExecutor(new SalirAbismoCommand(this));
        getCommand("ps").setExecutor(new ProtectionCommand(this));
        getCommand("vortex").setExecutor(new VortexCommand(this));
        getCommand("clan").setExecutor(new com.vortex.discordbridge.commands.ClanCommand(this));
        getCommand("verclan").setExecutor(new com.vortex.discordbridge.commands.ClanCommand(this));
        getCommand("cc").setExecutor(clanChatCommand);
        getCommand("clanfocus").setExecutor(clanChatCommand);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new VidasPlaceholder(this).register();
            getLogger().info("PlaceholderAPI hookeado correctamente.");
        }

        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            luckPerms = LuckPermsProvider.get();
            getLogger().info("LuckPerms hookeado correctamente.");
        }

        getLogger().info("DiscordBridge activado correctamente!");
    }

    @Override
    public void onDisable() {
        getServer().getOnlinePlayers().forEach(p ->
                vidasManager.descargarVidas(p.getUniqueId())
        );
        if (abismoBossBarManager != null) abismoBossBarManager.limpiarTodo();
        if (httpServer != null) httpServer.stop();
        if (database != null) database.close();
        getLogger().info("DiscordBridge desactivado.");
    }

    public static DiscordBridge getInstance() { return instance; }
    public DatabaseManager getDatabase() { return database; }
    public LinkManager getLinkManager() { return linkManager; }
    public BotNotifier getBotNotifier() { return botNotifier; }
    public VidasManager getVidasManager() { return vidasManager; }
    public AbismoBossBarManager getAbismoBossBarManager() { return abismoBossBarManager; }
    public ParkourManager getParkourManager() { return parkourManager; }
    public VidasListener getVidasListener() { return vidasListener; }
    public ProtectionManager getProtectionManager() { return protectionManager; }
    public ProtectionListener getProtectionListener() { return protectionListener; }
    public com.vortex.discordbridge.clan.ClanManager getClanManager() { return clanManager; }
    public com.vortex.discordbridge.clan.gui.ClanUpgradeGUI getClanUpgradeGUI() { return clanUpgradeGUI; }
    public com.vortex.discordbridge.clan.gui.ClanPrefixGUI getClanPrefixGUI() { return clanPrefixGUI; }
    public PvPManagerHook getPvpManagerHook() { return pvpManagerHook; }
    public LuckPerms getLuckPerms() { return luckPerms; }
    public ClanChatCommand getClanChatCommand() { return clanChatCommand; }
}