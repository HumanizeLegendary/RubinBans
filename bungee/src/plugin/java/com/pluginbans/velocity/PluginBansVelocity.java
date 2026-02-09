package com.pluginbans.velocity;

import com.google.gson.Gson;
import com.pluginbans.core.DatabaseConfig;
import com.pluginbans.core.DatabaseManager;
import com.pluginbans.core.DatabaseType;
import com.pluginbans.core.JdbcPunishmentRepository;
import com.pluginbans.core.PunishmentRepository;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.google.inject.Inject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@Plugin(id = "pluginbans", name = "PluginBans", version = "1.0.0", description = "Система наказаний.")
public final class PluginBansVelocity {
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("pluginbans:sync");
    private final ProxyServer proxy;
    private final Path dataDirectory;
    private DatabaseManager databaseManager;
    private PunishmentRepository repository;
    private VelocityPunishmentService punishmentService;
    private VelocityMessages messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Inject
    public PluginBansVelocity(ProxyServer proxy, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(com.velocitypowered.api.event.proxy.ProxyInitializeEvent event) {
        VelocityConfig config = loadConfig(dataDirectory);
        DatabaseConfig databaseConfig = new DatabaseConfig(
                DatabaseType.valueOf(config.database().type()),
                config.database().host(),
                config.database().port(),
                config.database().database(),
                config.database().user(),
                config.database().password(),
                dataDirectory.resolve("pluginbans-velocity.db").toString(),
                config.database().poolSize()
        );
        this.databaseManager = new DatabaseManager(databaseConfig);
        this.repository = new JdbcPunishmentRepository(databaseManager.dataSource(), databaseManager.executor());
        this.punishmentService = new VelocityPunishmentService(repository, config.nnrHiddenReason());
        this.messages = config.messages();
        proxy.getChannelRegistrar().register(CHANNEL);
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onPreLogin(PreLoginEvent event) {
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        UUID uuid = event.getUniqueId();
        Optional<Component> denial = punishmentService.buildDenial(uuid, ip, messages, miniMessage);
        if (denial.isPresent()) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(denial.get()));
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            String action = input.readUTF();
            if (!"НАКАЗАНИЕ".equals(action)) {
                return;
            }
            UUID uuid = UUID.fromString(input.readUTF());
            proxy.getPlayer(uuid).ifPresent(player -> {
                Optional<Component> denial = punishmentService.buildDenial(uuid, player.getRemoteAddress().getAddress().getHostAddress(), messages, miniMessage);
                denial.ifPresent(player::disconnect);
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось обработать сообщение синхронизации.", exception);
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    private VelocityConfig loadConfig(Path dataDirectory) {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Path configPath = dataDirectory.resolve("velocity-config.json");
            if (!Files.exists(configPath)) {
                try (var input = getClass().getResourceAsStream("/velocity-config.json")) {
                    if (input == null) {
                        throw new IllegalStateException("Отсутствует velocity-config.json в ресурсах.");
                    }
                    Files.copy(input, configPath);
                }
            }
            String json = Files.readString(configPath);
            return new Gson().fromJson(json, VelocityConfig.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось загрузить конфигурацию Velocity.", exception);
        }
    }
}
