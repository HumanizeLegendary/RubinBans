package com.pluginbans.paper;

import com.pluginbans.core.AuditLogger;
import com.pluginbans.core.DatabaseConfig;
import com.pluginbans.core.DatabaseManager;
import com.pluginbans.core.DatabaseType;
import com.pluginbans.core.JdbcPunishmentRepository;
import com.pluginbans.core.PunishmentRepository;
import com.pluginbans.core.PunishmentService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;

public final class PluginBansPaper extends JavaPlugin {
    private DatabaseManager databaseManager;
    private PunishmentRepository repository;
    private PaperPunishmentService punishmentService;
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
    }

    @Override
    public void onDisable() {
        if (coreService != null) {
            coreService.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    private void registerCommands() {
        getCommand("ban").setExecutor(new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.BAN));
        getCommand("tempban").setExecutor(new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.TEMPBAN));
        getCommand("ipban").setExecutor(new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.IPBAN));
        getCommand("mute").setExecutor(new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.MUTE));
        getCommand("warn").setExecutor(new StandardPunishCommand(punishmentService, messages, StandardPunishCommand.Type.WARN));
        getCommand("punish").setExecutor(new CustomPunishCommand(punishmentService, messages));
        getCommand("checkpunish").setExecutor(new CheckPunishCommand(punishmentService, messages));
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
        return new PaperConfig(databaseConfig, warnDuration, autoBanReason, checkDurationSeconds, checkTimeoutBanSeconds, checkTimeoutBanReason, muteBlockCommands);
    }

    private MessagesConfig loadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        return MessagesConfig.from(configuration);
    }

    private Path auditPath() {
        return getDataFolder().toPath().resolve("audit.log");
    }
}
