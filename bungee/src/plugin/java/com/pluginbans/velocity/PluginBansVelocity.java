package com.pluginbans.velocity;

import com.google.inject.Inject;
import com.pluginbans.core.AuditLogger;
import com.pluginbans.core.DatabaseManager;
import com.pluginbans.core.JdbcPunishmentRepository;
import com.pluginbans.core.PunishmentCreateEvent;
import com.pluginbans.core.PunishmentListener;
import com.pluginbans.core.PunishmentRecord;
import com.pluginbans.core.PunishmentRules;
import com.pluginbans.core.PunishmentService;
import com.pluginbans.core.PunishmentType;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Plugin(id = "pluginbans", name = "PluginBans", version = "1.0.0", description = "Система наказаний.")
public final class PluginBansVelocity implements PunishmentListener {
    private final ProxyServer proxy;
    private final Path dataDirectory;
    private DatabaseManager databaseManager;
    private PunishmentService punishmentService;
    private VelocityConfig config;
    private ConnectionThrottle throttle;
    private AuditLogger auditLogger;

    @Inject
    public PluginBansVelocity(ProxyServer proxy, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(com.velocitypowered.api.event.proxy.ProxyInitializeEvent event) {
        this.config = VelocityConfigLoader.load(dataDirectory);
        this.databaseManager = new DatabaseManager(config.databaseConfig());
        this.punishmentService = new PunishmentService(new JdbcPunishmentRepository(databaseManager.dataSource(), databaseManager.executor()), Duration.ofSeconds(5));
        this.punishmentService.registerListener(this);
        this.throttle = new ConnectionThrottle(config.throttleMaxConnections(), config.throttleWindowSeconds());
        this.auditLogger = new AuditLogger(config.auditPath());
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onPreLogin(PreLoginEvent event) {
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        if (!throttle.tryAcquire(ip)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text("Слишком много подключений. Подождите.")));
            return;
        }
        UUID uuid = event.getUniqueId();
        java.util.List<PunishmentRecord> punishments;
        try {
            punishments = punishmentService.getActiveForConnection(uuid, ip).join();
        } catch (RuntimeException exception) {
            auditLogger.log("Ошибка проверки наказаний перед входом: " + uuid);
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text("Сервис наказаний временно недоступен.")));
            return;
        }
        Optional<PunishmentRecord> ban = punishments.stream()
                .filter(record -> PunishmentRules.blocksLogin(record.type()))
                .findFirst();
        if (ban.isPresent()) {
            auditLogger.log("Теневой вход: %s заблокирован (тип %s)".formatted(uuid, ban.get().type().name()));
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text(
                    "Вы заблокированы.\nID наказания: " + ban.get().internalId()
            )));
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String ip = event.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        punishmentService.track(uuid, ip);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        punishmentService.untrack(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        String target = event.getOriginalServer().getServerInfo().getName();
        if (config.lobbyServers().contains(target)) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        PunishmentType type = punishmentService.getActiveByUuid(uuid).join().all().stream()
                .map(PunishmentRecord::type)
                .filter(punishment -> punishment == PunishmentType.WARN || punishment == PunishmentType.CHECK)
                .findFirst()
                .orElse(null);
        if (type != null) {
            event.getPlayer().disconnect(Component.text("Вы на проверке"));
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
        }
    }

    @Override
    public void onCreate(PunishmentCreateEvent event) {
        PunishmentRecord record = event.record();
        if (!PunishmentRules.blocksLogin(record.type())) {
            return;
        }
        proxy.getPlayer(record.uuid()).ifPresent(player ->
                player.disconnect(Component.text("Вы заблокированы.\nID наказания: " + record.internalId())));
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (punishmentService != null) {
            punishmentService.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }
}
