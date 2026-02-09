package com.pluginbans.paper;

import com.pluginbans.core.PunishmentRecord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class VelocityMessenger {
    public static final String CHANNEL = "pluginbans:sync";

    private final Plugin plugin;

    public VelocityMessenger(Plugin plugin) {
        this.plugin = plugin;
    }

    public void sendPunishment(PunishmentRecord record) {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        byte[] payload = buildPayload(record);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPluginMessage(plugin, CHANNEL, payload);
        }
    }

    private byte[] buildPayload(PunishmentRecord record) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             DataOutputStream data = new DataOutputStream(output)) {
            data.writeUTF("НАКАЗАНИЕ");
            data.writeUTF(record.uuid().toString());
            data.writeUTF(record.id());
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось отправить сообщение в Velocity.", exception);
        }
    }
}
