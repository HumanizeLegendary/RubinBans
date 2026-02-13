package com.pluginbans.paper;

import com.pluginbans.core.AuditLogger;
import com.pluginbans.core.DatabaseConfig;
import com.pluginbans.core.DatabaseManager;
import com.pluginbans.core.DatabaseType;
import com.pluginbans.core.JdbcPunishmentRepository;
import com.pluginbans.core.PunishmentRepository;
import com.pluginbans.core.PunishmentService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;

public final class PluginBansPaper extends JavaPlugin {
    private DatabaseManager databaseManager;
    private PunishmentRepository repository;
    private PaperPunishmentService punishmentService;
    private ForumApiServer forumApiServer;
    private MessagesConfig messages;
    private PunishmentService coreService;
    private CheckManager checkManager;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        this.messages = loadMessages();
        PaperConfig config = loadPluginConfig();
        this.databaseManager = new DatabaseManager(config.databaseConfig());
        this.repository = new JdbcPunishmentRepository(databaseManager.dataSource(), databaseManager.executor());
        this.coreService = new PunishmentService(repository, java.time.Duration.ofSeconds(5));
        this.punishmentService = new PaperPunishmentService(this, coreService, config, messages, new AuditLogger(auditPath()), null);
        this.checkManager = new CheckManager(this, punishmentService);
        this.punishmentService.setCheckManager(checkManager);
        this.coreService.registerListener(punishmentService);
        registerCommands();
        registerListeners();
        startApiServer(config);
    }

    @Override
    public void onDisable() {
        if (forumApiServer != null) {
            forumApiServer.close();
        }
        if (coreService != null) {
            coreService.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    private void registerCommands() {
        registerCommandExecutor("ban", new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.BAN));
        registerCommandExecutor("tempban", new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.TEMPBAN));
        registerCommandExecutor("ipban", new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.IPBAN));
        registerCommandExecutor("mute", new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.MUTE));
        registerCommandExecutor("warn", new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.WARN));
        registerCommandExecutor("punish", new CustomPunishCommand(punishmentService, messages));
        registerCommandExecutor("checkpunish", new CheckPunishCommand(punishmentService, messages));
        registerCommandExecutor("unpunish", new UnpunishCommand(punishmentService, messages));
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PunishmentListener(punishmentService, checkManager, messages), this);
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
        long warnDuration = config.getLong("punish.warn-duration-seconds", 1209600L);
        String autoBanReason = config.getString(
                "punish.auto-ban-reason",
                config.getString("punish.auto-ipban-reason", "Достигнут лимит предупреждений")
        );
        long checkDurationSeconds = config.getLong("check.duration-seconds", 900L);
        long checkTimeoutBanSeconds = config.getLong("check.timeout-ban-seconds", 0L);
        String checkTimeoutBanReason = config.getString("check.timeout-ban-reason", "Проверка не пройдена");
        boolean muteBlockCommands = config.getBoolean("mute.block-commands", false);
        boolean apiEnabled = config.getBoolean("api.enabled", false);
        String apiBind = config.getString("api.bind", "127.0.0.1");
        int apiPort = config.getInt("api.port", 8777);
        String apiToken = config.getString("api.token", "CHANGE_ME");
        return new PaperConfig(
                databaseConfig,
                warnDuration,
                autoBanReason,
                checkDurationSeconds,
                checkTimeoutBanSeconds,
                checkTimeoutBanReason,
                muteBlockCommands,
                apiEnabled,
                apiBind,
                apiPort,
                apiToken
        );
    }

    private MessagesConfig loadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        return MessagesConfig.from(configuration);
    }

    private Path auditPath() {
        return getDataFolder().toPath().resolve("audit.log");
    }

    private void startApiServer(PaperConfig config) {
        if (!config.apiEnabled()) {
            return;
        }
        try {
            this.forumApiServer = new ForumApiServer(punishmentService, config);
            this.forumApiServer.start();
            getLogger().info("Forum API enabled on " + config.apiBind() + ":" + config.apiPort());
        } catch (Exception exception) {
            getLogger().severe("Failed to start Forum API: " + exception.getMessage());
        }
    }

    private void registerCommandExecutor(String commandName, CommandExecutor executor) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            getLogger().severe("Команда не найдена в plugin.yml: " + commandName);
            return;
        }
        command.setExecutor(executor);
    }
}
