package com.pluginbans.paper;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

public final class PlayerResolver {
    private PlayerResolver() {
    }

    public static Optional<UUID> resolveUuid(String input) {
        Player player = Bukkit.getPlayerExact(input);
        if (player != null) {
            return Optional.of(player.getUniqueId());
        }
        try {
            return Optional.of(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(input);
            if (offline != null && offline.getUniqueId() != null) {
                return Optional.of(offline.getUniqueId());
            }
        }
        return Optional.empty();
    }

    public static Optional<String> resolveIp(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return Optional.empty();
        }
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return Optional.empty();
        }
        return Optional.of(address.getAddress().getHostAddress());
    }
}
