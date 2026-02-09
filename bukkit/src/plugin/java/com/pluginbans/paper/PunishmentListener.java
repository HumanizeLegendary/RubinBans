package com.pluginbans.paper;

import com.pluginbans.core.DurationFormatter;
import com.pluginbans.core.PunishmentRecord;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.List;
import java.util.Map;

public final class PunishmentListener implements Listener {
    private final PaperPunishmentService service;
    private final MessagesConfig messages;

    public PunishmentListener(PaperPunishmentService service, MessagesConfig messages) {
        this.service = service;
        this.messages = messages;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String ip = event.getAddress().getHostAddress();
        List<PunishmentRecord> punishments = service.getActiveBanLike(event.getUniqueId(), ip).join();
        if (punishments.isEmpty()) {
            return;
        }
        PunishmentRecord punishment = punishments.get(0);
        String reason = punishment.nnr() ? service.config().nnrHiddenReason() : punishment.reason();
        String message = service.messageService().applyPlaceholders(messages.banScreen(), Map.of(
                "%reason%", reason,
                "%time%", DurationFormatter.formatSeconds(punishment.durationSeconds()),
                "%id%", punishment.id()
        ));
        Component component = service.messageService().format(message);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, component);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        List<PunishmentRecord> punishments = service.getActiveByUuid(event.getPlayer().getUniqueId()).join();
        boolean muted = punishments.stream().anyMatch(record -> record.type().equalsIgnoreCase("MUTE"));
        if (!muted) {
            return;
        }
        PunishmentRecord mute = punishments.stream().filter(record -> record.type().equalsIgnoreCase("MUTE")).findFirst().orElse(null);
        if (mute == null) {
            return;
        }
        String reason = mute.nnr() ? service.config().nnrHiddenReason() : mute.reason();
        String message = service.messageService().applyPlaceholders(messages.muteMessage(), Map.of(
                "%reason%", reason,
                "%time%", DurationFormatter.formatSeconds(mute.durationSeconds()),
                "%id%", mute.id()
        ));
        event.setCancelled(true);
        service.runSync(() -> event.getPlayer().sendMessage(service.messageService().format(message)));
    }
}
