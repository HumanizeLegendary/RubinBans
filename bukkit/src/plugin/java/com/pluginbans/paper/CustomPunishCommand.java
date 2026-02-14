package com.pluginbans.paper;

import com.pluginbans.core.DurationParser;
import com.pluginbans.core.DurationFormatter;
import com.pluginbans.core.PunishmentRecord;
import com.pluginbans.core.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CustomPunishCommand implements CommandExecutor, Listener {
    private static final int INVENTORY_SIZE = 27;
    private static final int[] OPTION_SLOTS = {10, 11, 12, 13, 14};
    private static final int EDGE_GLASS_SLOT = 15;
    private static final Material BORDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material OPTION_MATERIAL = Material.FERMENTED_SPIDER_EYE;
    private static final Material EMPTY_SLOT_MATERIAL = Material.GRAY_DYE;

    private final JavaPlugin plugin;
    private final PaperPunishmentService service;
    private final MessagesConfig messages;

    public CustomPunishCommand(JavaPlugin plugin, PaperPunishmentService service, MessagesConfig messages) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bans.punish") && !sender.hasPermission("bans.fullaccess")) {
            service.messageService().send(sender, messages.permissionDenied());
            return true;
        }
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                service.messageService().send(sender, "<red>Меню доступно только игроку. Используйте: /punish <игрок|uuid> <тип> <длительность> <причина></red>");
                return true;
            }
            openMenu(player, args[0]);
            return true;
        }
        if (args.length < 4) {
            sendUsage(sender, command);
            return true;
        }
        issueManual(sender, args);
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PunishMenuHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        MenuPunishment punishment = holder.punishmentAt(slot);
        if (punishment == null) {
            if (isOptionSlot(slot)) {
                service.messageService().send(player, "<yellow>Пустой слот. Добавьте наказание в config.yml -> punish.menu.punishments.</yellow>");
            }
            return;
        }
        player.closeInventory();
        issueFromMenu(player, holder.targetUuid(), punishment);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PunishMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void issueManual(CommandSender sender, String[] args) {
        Optional<UUID> uuid = PlayerResolver.resolveUuid(args[0]);
        if (uuid.isEmpty()) {
            service.messageService().send(sender, messages.error("player_not_found"));
            return;
        }
        String typeName = args[1].toUpperCase(Locale.ROOT);
        PunishmentType type;
        try {
            type = PunishmentType.valueOf(typeName);
        } catch (IllegalArgumentException exception) {
            service.messageService().send(sender, messages.error("type"));
            return;
        }
        long durationSeconds;
        try {
            durationSeconds = DurationParser.parseToSeconds(args[2]);
        } catch (IllegalArgumentException exception) {
            service.messageService().send(sender, messages.error("duration"));
            return;
        }
        if (type == PunishmentType.TEMPBAN && durationSeconds == 0L) {
            service.messageService().send(sender, "<red>Для временного бана укажите срок больше 0.</red>");
            return;
        }
        String reason = joinArgs(args, 3);
        if (reason.isBlank()) {
            service.messageService().send(sender, messages.error("reason"));
            return;
        }
        if (type == PunishmentType.WARN) {
            Optional<String> normalizedWarn = service.normalizeWarnReason(reason);
            if (normalizedWarn.isEmpty()) {
                service.messageService().send(sender, "<red>Для WARN доступно только 2 причины.</red>");
                service.messageService().send(sender, service.warnReasonsHint());
                return;
            }
            reason = normalizedWarn.get();
        }
        boolean silent = hasFlag(args, "-s");
        boolean nnr = hasFlag(args, "-nnr");
        UUID target = uuid.get();
        String actor = sender.getName();
        String ip = PlayerResolver.resolveIp(target).orElse(null);
        if (type == PunishmentType.IPBAN && (ip == null || ip.isBlank())) {
            service.messageService().send(sender, "<red>Для IP-бана игрок должен быть онлайн.</red>");
            return;
        }
        issue(sender, target, type, reason.trim(), durationSeconds, actor, ip, silent, nnr);
    }

    private void openMenu(Player sender, String targetInput) {
        Optional<UUID> uuid = PlayerResolver.resolveUuid(targetInput);
        if (uuid.isEmpty()) {
            service.messageService().send(sender, messages.error("player_not_found"));
            return;
        }
        List<MenuPunishment> punishments = loadMenuPunishments();
        UUID targetUuid = uuid.get();
        String targetName = resolveTargetName(targetUuid, targetInput);
        String title = plugin.getConfig().getString("punish.menu.title", "<dark_red>Выдача наказания</dark_red>");
        PunishMenuHolder holder = new PunishMenuHolder(
                targetUuid,
                targetName,
                service.messageService().formatRaw(title)
        );
        renderLayout(holder, punishments);
        sender.openInventory(holder.inventory());
    }

    private void renderLayout(PunishMenuHolder holder, List<MenuPunishment> punishments) {
        Inventory inventory = holder.inventory();
        ItemStack empty = createEmptyPunishmentItem();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, empty);
        }
        inventory.setItem(EDGE_GLASS_SLOT, createBorderItem());

        if (punishments.isEmpty()) {
            for (int i = 0; i < OPTION_SLOTS.length; i++) {
                inventory.setItem(OPTION_SLOTS[i], createEmptyPunishmentItem());
            }
            return;
        }

        int added = 0;
        for (int i = 0; i < OPTION_SLOTS.length; i++) {
            int slot = OPTION_SLOTS[i];
            if (i < punishments.size()) {
                MenuPunishment punishment = punishments.get(i);
                holder.putPunishment(slot, punishment);
                inventory.setItem(slot, createPunishmentItem(holder.targetName(), punishment));
                added++;
            } else {
                inventory.setItem(slot, createEmptyPunishmentItem());
            }
        }
        if (added == 0) {
            plugin.getLogger().warning("Не удалось отрисовать punish-меню: все записи punish.menu.punishments невалидны.");
        }
    }

    private void issueFromMenu(Player sender, UUID target, MenuPunishment punishment) {
        String ip = PlayerResolver.resolveIp(target).orElse(null);
        if (punishment.type() == PunishmentType.IPBAN && (ip == null || ip.isBlank())) {
            service.messageService().send(sender, "<red>Для IP-бана игрок должен быть онлайн.</red>");
            return;
        }
        if (punishment.type() == PunishmentType.WARN && service.normalizeWarnReason(punishment.reason()).isEmpty()) {
            service.messageService().send(sender, "<red>Для WARN доступно только 2 причины.</red>");
            service.messageService().send(sender, service.warnReasonsHint());
            return;
        }
        issue(sender, target, punishment.type(), punishment.reason(), punishment.durationSeconds(), sender.getName(), ip, punishment.silent(), punishment.nnr());
    }

    private void issue(CommandSender sender, UUID target, PunishmentType type, String reason, long durationSeconds, String actor, String ip, boolean silent, boolean nnr) {
        service.issuePunishment(target, type.name(), reason, durationSeconds, actor, ip, silent, nnr)
                .whenComplete((record, throwable) -> {
                    if (throwable != null) {
                        service.logError("Не удалось выдать наказание " + type.name() + " для " + target, throwable);
                        service.runSync(() -> service.messageService().send(sender, "<red>Не удалось выдать наказание.</red>"));
                        return;
                    }
                    service.runSync(() -> sendIssueSummary(sender, record));
                });
    }

    private void sendUsage(CommandSender sender, Command command) {
        String usage = command.getUsage();
        if (usage == null || usage.isBlank()) {
            service.messageService().send(sender, messages.error("usage"));
            return;
        }
        service.messageService().send(sender, "<red>Использование:</red> <white>/punish <игрок|uuid></white>");
        service.messageService().send(sender, "<gray>или:</gray> <white>" + usage + "</white>");
    }

    private boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase(flag)) {
                return true;
            }
        }
        return false;
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("-s") || arg.equalsIgnoreCase("-nnr")) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(arg);
        }
        return builder.toString();
    }

    private void sendIssueSummary(CommandSender sender, PunishmentRecord record) {
        String time = DurationFormatter.formatSeconds(record.durationSeconds());
        service.messageService().send(sender,
                "<green>Наказание выдано:</green> <white>" + record.type().name() + "</white> <dark_gray>|</dark_gray> <gray>ID:</gray> <yellow>" + record.internalId() + "</yellow>");
        service.messageService().send(sender,
                "<gray>Причина:</gray> <white>" + record.reason() + "</white> <dark_gray>|</dark_gray> <gray>Срок:</gray> <white>" + time + "</white>");
    }

    private List<MenuPunishment> loadMenuPunishments() {
        FileConfiguration config = plugin.getConfig();
        List<Map<?, ?>> raw = config.getMapList("punish.menu.punishments");
        List<MenuPunishment> punishments = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            if (punishments.size() >= OPTION_SLOTS.length) {
                break;
            }
            MenuPunishment punishment = parseMenuPunishment(entry);
            if (punishment != null) {
                punishments.add(punishment);
            }
        }
        return punishments;
    }

    private MenuPunishment parseMenuPunishment(Map<?, ?> entry) {
        String typeRaw = readString(entry.get("type"));
        String reason = readString(entry.get("reason"));
        if (typeRaw == null || typeRaw.isBlank() || reason == null || reason.isBlank()) {
            return null;
        }
        PunishmentType type;
        try {
            type = PunishmentType.valueOf(typeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Пропущена запись punish.menu.punishments: неверный type=" + typeRaw);
            return null;
        }
        long durationSeconds = resolveConfiguredDuration(entry, type);
        if (durationSeconds < 0L) {
            plugin.getLogger().warning("Пропущена запись punish.menu.punishments: duration должен быть >= 0");
            return null;
        }
        if (type == PunishmentType.TEMPBAN && durationSeconds == 0L) {
            plugin.getLogger().warning("Пропущена запись punish.menu.punishments: TEMPBAN требует duration > 0");
            return null;
        }
        if (type == PunishmentType.WARN && service.normalizeWarnReason(reason).isEmpty()) {
            plugin.getLogger().warning("Пропущена запись punish.menu.punishments: WARN reason должен быть в warn.allowed-reasons");
            return null;
        }
        String name = readString(entry.get("name"));
        if (name == null || name.isBlank()) {
            name = "<red>" + type.name() + "</red>";
        }
        List<String> lore = readStringList(entry.get("lore"));
        boolean silent = readBoolean(entry.get("silent"));
        boolean nnr = readBoolean(entry.get("nnr"));
        return new MenuPunishment(name, lore, type, durationSeconds, reason.trim(), silent, nnr);
    }

    private long resolveConfiguredDuration(Map<?, ?> entry, PunishmentType type) {
        Object rawSeconds = entry.get("duration-seconds");
        if (rawSeconds instanceof Number number) {
            return number.longValue();
        }
        String durationRaw = readString(entry.get("duration"));
        if (durationRaw != null && !durationRaw.isBlank()) {
            try {
                return DurationParser.parseToSeconds(durationRaw.trim());
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Неверная duration в punish.menu.punishments: " + durationRaw);
                return -1L;
            }
        }
        if (type == PunishmentType.WARN) {
            return service.config().warnDurationSeconds();
        }
        return 0L;
    }

    private String resolveTargetName(UUID uuid, String fallback) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return fallback;
    }

    private ItemStack createBorderItem() {
        ItemStack item = new ItemStack(BORDER_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(service.messageService().formatRaw(" "));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmptyPunishmentItem() {
        ItemStack item = new ItemStack(EMPTY_SLOT_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(service.messageService().formatRaw("<gray>Пустой слот</gray>"));
        meta.lore(List.of(
                service.messageService().formatRaw("<dark_gray>Добавьте наказание в config.yml</dark_gray>"),
                service.messageService().formatRaw("<dark_gray>punish.menu.punishments</dark_gray>")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPunishmentItem(String targetName, MenuPunishment punishment) {
        ItemStack item = new ItemStack(OPTION_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(service.messageService().formatRaw(punishment.name()));
        List<String> loreLines = punishment.lore();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (loreLines.isEmpty()) {
            loreLines = List.of(
                    "<gray>Игрок:</gray> <white>%player%</white>",
                    "<gray>Тип:</gray> <white>%type%</white>",
                    "<gray>Причина:</gray> <white>%reason%</white>",
                    "<gray>Срок:</gray> <white>%time%</white>",
                    "<green>Нажмите, чтобы выдать</green>"
            );
        }
        String time = DurationFormatter.formatSeconds(punishment.durationSeconds());
        for (String line : loreLines) {
            String rendered = line
                    .replace("%player%", targetName)
                    .replace("%type%", punishment.type().name())
                    .replace("%reason%", punishment.reason())
                    .replace("%time%", time);
            lore.add(service.messageService().formatRaw(rendered));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isOptionSlot(int slot) {
        for (int optionSlot : OPTION_SLOTS) {
            if (optionSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private String readString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(rawList.size());
        for (Object entry : rawList) {
            if (entry != null) {
                lines.add(String.valueOf(entry));
            }
        }
        return lines;
    }

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private record MenuPunishment(
            String name,
            List<String> lore,
            PunishmentType type,
            long durationSeconds,
            String reason,
            boolean silent,
            boolean nnr
    ) {
    }

    private static final class PunishMenuHolder implements InventoryHolder {
        private final UUID targetUuid;
        private final String targetName;
        private final Inventory inventory;
        private final Map<Integer, MenuPunishment> punishmentsBySlot = new HashMap<>();

        private PunishMenuHolder(UUID targetUuid, String targetName, net.kyori.adventure.text.Component title) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);
        }

        public UUID targetUuid() {
            return targetUuid;
        }

        public String targetName() {
            return targetName;
        }

        public Inventory inventory() {
            return inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void putPunishment(int slot, MenuPunishment punishment) {
            punishmentsBySlot.put(slot, punishment);
        }

        public MenuPunishment punishmentAt(int slot) {
            return punishmentsBySlot.get(slot);
        }
    }
}
