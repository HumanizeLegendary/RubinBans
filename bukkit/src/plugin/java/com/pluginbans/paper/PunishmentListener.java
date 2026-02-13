package com.pluginbans.paper;

import com.pluginbans.core.DurationFormatter;
import com.pluginbans.core.PunishmentRecord;
import com.pluginbans.core.PunishmentType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class PunishmentListener implements Listener {
    private static final Pattern ANYDESK_PATTERN = Pattern.compile("^\\d{6,19}$");
    private final PaperPunishmentService service;
    private final CheckManager checkManager;
    private final MessagesConfig messages;

    public PunishmentListener(PaperPunishmentService service, CheckManager checkManager, MessagesConfig messages) {
        this.service = service;
        this.checkManager = checkManager;
        this.messages = messages;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String ip = event.getAddress().getHostAddress();
        UUID uuid = event.getUniqueId();
        List<PunishmentRecord> punishments = new java.util.ArrayList<>(service.core().getActiveByUuid(uuid).join().all());
        punishments.addAll(service.core().getActiveByIp(ip).join());
        Optional<PunishmentRecord> ban = punishments.stream()
                .filter(record -> record.type() == PunishmentType.BAN
                        || record.type() == PunishmentType.TEMPBAN
                        || record.type() == PunishmentType.IPBAN)
                .findFirst();
        if (ban.isEmpty()) {
            return;
        }
        PunishmentRecord record = ban.get();
        String time = DurationFormatter.formatSeconds(record.durationSeconds());
        String message = service.messageService().applyPlaceholders(messages.kickMessage(), Map.of(
                "%reason%", record.reason(),
                "%time%", time,
                "%actor%", record.actor()
        ));
        Component component = service.messageService().format(message);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, component);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (checkManager.isInCheck(uuid)) {
            String messageText = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
            if (ANYDESK_PATTERN.matcher(messageText).matches()) {
                checkManager.pause(uuid);
                notifyStaffAnyDesk(event.getPlayer().getName(), messageText);
            } else {
                event.getPlayer().sendMessage(service.messageService().format(messages.checkBlockMessage()));
            }
            event.setCancelled(true);
            return;
        }
        if (event.getPlayer().hasPermission("bans.fullaccess")) {
            return;
        }
        List<PunishmentRecord> punishments = service.core().getActiveByUuid(uuid).join().all();
        Optional<PunishmentRecord> mute = punishments.stream()
                .filter(record -> record.type() == PunishmentType.MUTE)
                .findFirst();
        if (mute.isEmpty()) {
            return;
        }
        String messageText = PlainTextComponentSerializer.plainText().serialize(event.message());
        String response = service.messageService().applyPlaceholders(messages.mutedChatMessage(), Map.of(
                "%reason%", mute.get().reason(),
                "%time%", DurationFormatter.formatSeconds(mute.get().durationSeconds()),
                "%message%", messageText
        ));
        event.setCancelled(true);
        event.getPlayer().sendMessage(service.messageService().format(response));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!checkManager.isInCheck(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getFrom().distanceSquared(event.getTo()) > 0.0) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String ip = event.getPlayer().getAddress() == null ? null : event.getPlayer().getAddress().getAddress().getHostAddress();
        service.core().track(uuid, ip);
        service.core().getActiveByUuid(uuid).thenAccept(active -> {
            if (active.has(PunishmentType.CHECK)) {
                checkManager.startCheck(uuid, active.get(PunishmentType.CHECK).map(PunishmentRecord::endTime).orElse(null));
            }
        });
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!service.config().muteBlockCommands()) {
            return;
        }
        if (event.getPlayer().hasPermission("bans.fullaccess")) {
            return;
        }
        List<PunishmentRecord> punishments = service.core().getActiveByUuid(event.getPlayer().getUniqueId()).join().all();
        Optional<PunishmentRecord> mute = punishments.stream().filter(record -> record.type() == PunishmentType.MUTE).findFirst();
        if (mute.isPresent()) {
            event.setCancelled(true);
            String response = service.messageService().applyPlaceholders(messages.mutedChatMessage(), Map.of(
                    "%reason%", mute.get().reason(),
                    "%time%", DurationFormatter.formatSeconds(mute.get().durationSeconds())
            ));
            event.getPlayer().sendMessage(service.messageService().format(response));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        checkManager.stopCheck(event.getPlayer().getUniqueId());
        service.core().untrack(event.getPlayer().getUniqueId());
    }

    private void notifyStaffAnyDesk(String playerName, String code) {
        String message = "<yellow>Игрок <white>%s</white> передал AnyDesk: <green>%s</green></yellow>".formatted(playerName, code);
        org.bukkit.Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission("bans.check") || player.hasPermission("bans.fullaccess"))
                .forEach(player -> player.sendMessage(service.messageService().format(message)));
    }
}
