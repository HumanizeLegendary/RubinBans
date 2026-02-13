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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class PluginBansPaper extends JavaPlugin {
    private DatabaseManager databaseManager;
    private PunishmentRepository repository;
    private PaperPunishmentService punishmentService;
    private ForumApiServer forumApiServer;
    private MessagesConfig messages;
    private PunishmentService coreService;
    private CheckManager checkManager;
    private CustomPunishCommand customPunishCommand;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        this.messages = loadMessages();
        PaperConfig config = loadPluginConfig();
        this.databaseManager = new DatabaseManager(config.databaseConfig());
        this.repository = new JdbcPunishmentRepository(databaseManager.dataSource(), databaseManager.executor());
        this.coreService = new PunishmentService(repository, Duration.ofSeconds(Math.max(1L, config.syncPollSeconds())));
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
        this.customPunishCommand = new CustomPunishCommand(this, punishmentService, messages);
        registerCommandExecutor("punish", customPunishCommand);
        registerCommandExecutor("checkpunish", new CheckPunishCommand(punishmentService, messages));
        registerCommandExecutor("unpunish", new UnpunishCommand(punishmentService, messages));
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PunishmentListener(punishmentService, checkManager, messages), this);
        if (customPunishCommand != null) {
            Bukkit.getPluginManager().registerEvents(customPunishCommand, this);
        }
    }

    private PaperConfig loadPluginConfig() {
        FileConfiguration config = getConfig();
        DatabaseType type = DatabaseType.valueOf(config.getString("database.type", "SQLITE").toUpperCase());
        String sqlitePath = resolveSqlitePath(config.getString("database.sqlite.file", "pluginbans.db"));
        long syncPollSeconds = Math.max(1L, config.getLong("sync.poll-seconds", 2L));
        List<String> warnAllowedReasons = sanitizeList(
                config.getStringList("warn.allowed-reasons"),
                List.of("Отказ от проверки", "Нарушение правил проверки")
        );
        List<String> warnExternalActors = sanitizeList(
                config.getStringList("warn.external-actors"),
                List.of("CheckPlugin")
        );
        DatabaseConfig databaseConfig = new DatabaseConfig(
                type,
                config.getString("database.mysql.host", "localhost"),
                config.getInt("database.mysql.port", 3306),
                config.getString("database.mysql.database", "pluginbans"),
                config.getString("database.mysql.user", "root"),
                config.getString("database.mysql.password", ""),
                sqlitePath,
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
                syncPollSeconds,
                warnDuration,
                warnAllowedReasons,
                warnExternalActors,
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

    private String resolveSqlitePath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return new File(getDataFolder(), "pluginbans.db").getPath();
        }
        File file = new File(configuredPath);
        if (file.isAbsolute()) {
            return file.getPath();
        }
        File resolved = new File(getDataFolder(), configuredPath);
        if (resolved.exists()) {
            return resolved.getPath();
        }
        if (file.exists()) {
            if (!copyLegacySqlite(file, resolved)) {
                getLogger().warning("Используется старый путь SQLite: " + file.getPath());
                return file.getPath();
            }
        }
        return resolved.getPath();
    }

    private boolean copyLegacySqlite(File legacyRelativePath, File resolvedPath) {
        try {
            File parent = resolvedPath.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            copyIfExists(legacyRelativePath.toPath(), resolvedPath.toPath());
            copySiblingIfExists(legacyRelativePath, resolvedPath, "-wal");
            copySiblingIfExists(legacyRelativePath, resolvedPath, "-shm");
            getLogger().info("SQLite база скопирована в папку плагина: " + resolvedPath.getPath());
            return true;
        } catch (IOException exception) {
            getLogger().warning("Не удалось скопировать SQLite базу в папку плагина: " + exception.getMessage());
            return false;
        }
    }

    private void copySiblingIfExists(File legacyRelativePath, File resolvedPath, String suffix) throws IOException {
        Path from = legacyRelativePath.toPath().resolveSibling(legacyRelativePath.getName() + suffix);
        Path to = resolvedPath.toPath().resolveSibling(resolvedPath.getName() + suffix);
        copyIfExists(from, to);
    }

    private void copyIfExists(Path from, Path to) throws IOException {
        if (Files.exists(from)) {
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<String> sanitizeList(List<String> values, List<String> fallback) {
        List<String> source = values == null || values.isEmpty() ? fallback : values;
        List<String> result = new ArrayList<>(source.size());
        for (String value : source) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        if (result.isEmpty()) {
            result.addAll(fallback);
        }
        return List.copyOf(result);
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
