package com.pluginbans.paper;

import com.pluginbans.core.DatabaseConfig;
import com.pluginbans.core.DatabaseManager;
import com.pluginbans.core.DatabaseType;
import com.pluginbans.core.DiscordBridge;
import com.pluginbans.core.JdbcPunishmentRepository;
import com.pluginbans.core.PunishmentRepository;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class PluginBansPaper extends JavaPlugin {
    private DatabaseManager databaseManager;
    private PunishmentRepository repository;
    private PaperPunishmentService punishmentService;
    private MessagesConfig messages;
    private VelocityMessenger velocityMessenger;
    private RestServer restServer;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        this.messages = loadMessages();
        PaperConfig config = loadPluginConfig();
        this.databaseManager = new DatabaseManager(config.databaseConfig());
        this.repository = new JdbcPunishmentRepository(databaseManager.dataSource(), databaseManager.executor());
        this.velocityMessenger = new VelocityMessenger(this);
        this.punishmentService = new PaperPunishmentService(
                this,
                repository,
                config,
                messages,
                new DiscordBridge(),
                velocityMessenger
        );
        registerCommands();
        registerListeners();
        if (config.restEnabled()) {
            this.restServer = new RestServer(config, repository);
            this.restServer.start();
        }
    }

    @Override
    public void onDisable() {
        if (restServer != null) {
            restServer.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    private void registerCommands() {
        getCommand("ban").setExecutor(new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.BAN));
        getCommand("ipban").setExecutor(new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.IPBAN));
        getCommand("mute").setExecutor(new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.MUTE));
        getCommand("warn").setExecutor(new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.WARN));
        getCommand("idban").setExecutor(new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.IDBAN));
        getCommand("punish").setExecutor(new CustomPunishCommand(punishmentService, messages));
        getCommand("checkpunish").setExecutor(new CheckPunishCommand(punishmentService, messages));
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PunishmentListener(punishmentService, messages), this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, VelocityMessenger.CHANNEL);
    }

    private PaperConfig loadPluginConfig() {
        FileConfiguration config = getConfig();
        DatabaseType type = DatabaseType.valueOf(config.getString("database.type", "SQLITE").toUpperCase());
        DatabaseConfig databaseConfig = new DatabaseConfig(
                type,
                config.getString("database.mysql.host", "localhost"),
                config.getInt("database.mysql.port", 3306),
                config.getString("database.mysql.database", "pluginbans"),
                config.getString("database.mysql.user", "root"),
                config.getString("database.mysql.password", ""),
                config.getString("database.sqlite.file", new File(getDataFolder(), "pluginbans.db").getPath()),
                config.getInt("database.pool-size", 10)
        );
        List<String> punishTypes = config.getStringList("punish.types");
        List<String> nnrTypes = config.getStringList("punish.nnr-types");
        long warnDuration = config.getLong("punish.warn-duration-seconds", 1209600L);
        String nnrHiddenReason = config.getString("punish.nnr-hidden-reason", "Служебное наказание");
        String autoIpbanReason = config.getString("punish.auto-ipban-reason", "Достигнут лимит предупреждений");
        boolean restEnabled = config.getBoolean("rest.enabled", true);
        int restPort = config.getInt("rest.port", 8081);
        String restBind = config.getString("rest.bind", "0.0.0.0");
        return new PaperConfig(databaseConfig, punishTypes, nnrTypes, warnDuration, nnrHiddenReason, autoIpbanReason, restEnabled, restPort, restBind);
    }

    private MessagesConfig loadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        return MessagesConfig.from(configuration);
    }
}
