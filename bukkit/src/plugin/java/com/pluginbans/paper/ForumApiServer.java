package com.pluginbans.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pluginbans.core.DurationParser;
import com.pluginbans.core.PunishmentHistoryRecord;
import com.pluginbans.core.PunishmentRecord;
import com.pluginbans.core.PunishmentType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ForumApiServer implements AutoCloseable {
    private static final String API_PREFIX = "/api/v1";

    private final PaperPunishmentService service;
    private final PaperConfig config;
    private final Gson gson;
    private final HttpServer server;
    private final ExecutorService executor;

    public ForumApiServer(PaperPunishmentService service, PaperConfig config) throws IOException {
        this.service = service;
        this.config = config;
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
        this.server = HttpServer.create(new InetSocketAddress(config.apiBind(), config.apiPort()), 0);
        this.executor = Executors.newFixedThreadPool(4);
        this.server.setExecutor(executor);
        this.server.createContext(API_PREFIX, this::handleRequest);
    }

    public void start() {
        server.start();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 204, Map.of("ok", true));
                return;
            }
            if (!isAuthorized(exchange)) {
                sendJson(exchange, 401, Map.of("ok", false, "error", "Unauthorized"));
                return;
            }
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            List<String> segments = extractSegments(exchange.getRequestURI().getPath());

            if ("GET".equals(method) && segments.size() == 1 && "health".equalsIgnoreCase(segments.get(0))) {
                sendJson(exchange, 200, Map.of("ok", true, "time", Instant.now().toString()));
                return;
            }
            if ("POST".equals(method) && segments.size() == 1 && "punishments".equalsIgnoreCase(segments.get(0))) {
                handleCreatePunishment(exchange);
                return;
            }
            if ("GET".equals(method) && segments.size() == 2 && "punishments".equalsIgnoreCase(segments.get(0))) {
                handleGetPunishment(exchange, segments.get(1));
                return;
            }
            if ("POST".equals(method) && segments.size() == 3 && "punishments".equalsIgnoreCase(segments.get(0))
                    && "revoke".equalsIgnoreCase(segments.get(2))) {
                handleRevokePunishment(exchange, segments.get(1));
                return;
            }
            if ("GET".equals(method) && segments.size() == 3 && "players".equalsIgnoreCase(segments.get(0))
                    && "active".equalsIgnoreCase(segments.get(2))) {
                handlePlayerActive(exchange, segments.get(1));
                return;
            }
            if ("GET".equals(method) && segments.size() == 3 && "players".equalsIgnoreCase(segments.get(0))
                    && "history".equalsIgnoreCase(segments.get(2))) {
                handlePlayerHistory(exchange, segments.get(1));
                return;
            }
            sendJson(exchange, 404, Map.of("ok", false, "error", "Not Found"));
        } catch (Exception exception) {
            service.logError("Forum API request failed", exception);
            sendJson(exchange, 500, Map.of("ok", false, "error", "Internal Server Error"));
        }
    }

    private void handleCreatePunishment(HttpExchange exchange) throws IOException {
        CreatePunishmentRequest request = gson.fromJson(readBody(exchange), CreatePunishmentRequest.class);
        if (request == null || isBlank(request.target) || isBlank(request.type) || isBlank(request.reason)) {
            sendJson(exchange, 400, Map.of("ok", false, "error", "target, type and reason are required"));
            return;
        }
        Optional<UUID> uuid;
        try {
            uuid = service.supplySync(() -> PlayerResolver.resolveUuid(request.target)).join();
        } catch (CompletionException exception) {
            service.logError("Forum API failed to resolve target: " + request.target, exception);
            sendJson(exchange, 500, Map.of("ok", false, "error", "Failed to resolve target player"));
            return;
        }
        if (uuid.isEmpty()) {
            sendJson(exchange, 404, Map.of("ok", false, "error", "Player not found"));
            return;
        }

        PunishmentType type;
        try {
            type = PunishmentType.valueOf(request.type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("ok", false, "error", "Invalid punishment type"));
            return;
        }

        long durationSeconds;
        try {
            durationSeconds = resolveDurationSeconds(request, type);
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("ok", false, "error", exception.getMessage()));
            return;
        }

        String ip;
        try {
            ip = service.supplySync(() -> PlayerResolver.resolveIp(uuid.get()).orElse(null)).join();
        } catch (CompletionException exception) {
            service.logError("Forum API failed to resolve player IP: " + uuid.get(), exception);
            sendJson(exchange, 500, Map.of("ok", false, "error", "Failed to resolve player IP"));
            return;
        }
        if (type == PunishmentType.IPBAN && isBlank(ip)) {
            sendJson(exchange, 400, Map.of("ok", false, "error", "Target player must be online for IPBAN"));
            return;
        }

        String actor = isBlank(request.actor) ? "ForumAPI" : request.actor.trim();
        boolean silent = Boolean.TRUE.equals(request.silent);
        boolean nnr = Boolean.TRUE.equals(request.nnr);
        String reason = request.reason.trim();
        if (type == PunishmentType.WARN) {
            Optional<String> normalizedWarnReason = service.normalizeWarnReason(reason);
            if (normalizedWarnReason.isEmpty()) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "Invalid WARN reason. Allowed: " + plainWarnReasons()));
                return;
            }
            if (!service.canIssueWarnFromExternalActor(actor)) {
                sendJson(exchange, 403, Map.of("ok", false, "error", "WARN via API is allowed only for configured external actors"));
                return;
            }
            reason = normalizedWarnReason.get();
        }

        try {
            PunishmentRecord record = service.issuePunishment(
                    uuid.get(),
                    type.name(),
                    reason,
                    durationSeconds,
                    actor,
                    ip,
                    silent,
                    nnr
            ).join();
            sendJson(exchange, 201, Map.of(
                    "ok", true,
                    "punishment", toPunishmentMap(record)
            ));
        } catch (CompletionException exception) {
            service.logError("Forum API create punishment failed", exception);
            sendJson(exchange, 500, Map.of("ok", false, "error", "Failed to create punishment"));
        }
    }

    private void handleGetPunishment(HttpExchange exchange, String id) throws IOException {
        String punishmentId = id.toUpperCase(Locale.ROOT);
        try {
            Optional<PunishmentRecord> optional = service.core().findByInternalId(punishmentId).join();
            if (optional.isEmpty()) {
                sendJson(exchange, 404, Map.of("ok", false, "error", "Punishment not found"));
                return;
            }
            sendJson(exchange, 200, Map.of("ok", true, "punishment", toPunishmentMap(optional.get())));
        } catch (CompletionException exception) {
            service.logError("Forum API get punishment failed", exception);
            sendJson(exchange, 500, Map.of("ok", false, "error", "Failed to load punishment"));
        }
    }

    private void handleRevokePunishment(HttpExchange exchange, String id) throws IOException {
        String punishmentId = id.toUpperCase(Locale.ROOT);
        RevokePunishmentRequest request = gson.fromJson(readBody(exchange), RevokePunishmentRequest.class);
        String actor = request == null || isBlank(request.actor) ? "ForumAPI" : request.actor.trim();
        String reason = request == null || isBlank(request.reason) ? "Снято через API" : request.reason.trim();
        try {
            Optional<PunishmentRecord> existing = service.core().findByInternalId(punishmentId).join();
            if (existing.isEmpty()) {
                sendJson(exchange, 404, Map.of("ok", false, "error", "Punishment not found"));
                return;
            }
            service.core().removePunishment(punishmentId, actor, reason, "API_REMOVE").join();
            Optional<PunishmentRecord> updated = service.core().findByInternalId(punishmentId).join();
            sendJson(exchange, 200, Map.of(
                    "ok", true,
                    "punishment", updated.map(this::toPunishmentMap).orElseGet(() -> Map.of("id", punishmentId, "active", false))
            ));
        } catch (CompletionException exception) {
            service.logError("Forum API revoke punishment failed", exception);
            sendJson(exchange, 500, Map.of("ok", false, "error", "Failed to revoke punishment"));
        }
    }

    private void handlePlayerActive(HttpExchange exchange, String target) throws IOException {
        Optional<UUID> uuid;
        try {
            uuid = service.supplySync(() -> PlayerResolver.resolveUuid(decodeSegment(target))).join();
        } catch (CompletionException exception) {
            service.logError("Forum API failed to resolve player for active endpoint: " + target, exception);
            sendJson(exchange, 500, Map.of("ok", false, "error", "Failed to resolve player"));
            return;
        }
        if (uuid.isEmpty()) {
            sendJson(exchange, 404, Map.of("ok", false, "error", "Player not found"));
            return;
        }
        try {
            List<PunishmentRecord> punishments = service.core().getActiveByUuid(uuid.get()).join().all();
            List<Map<String, Object>> payload = new ArrayList<>(punishments.size());
            for (PunishmentRecord punishment : punishments) {
                payload.add(toPunishmentMap(punishment));
            }
            sendJson(exchange, 200, Map.of("ok", true, "uuid", uuid.get().toString(), "active", payload));
        } catch (CompletionException exception) {
            service.logError("Forum API player active failed", exception);
            sendJson(exchange, 500, Map.of("ok", false, "error", "Failed to load active punishments"));
        }
    }

    private void handlePlayerHistory(HttpExchange exchange, String target) throws IOException {
        Optional<UUID> uuid;
        try {
            uuid = service.supplySync(() -> PlayerResolver.resolveUuid(decodeSegment(target))).join();
        } catch (CompletionException exception) {
            service.logError("Forum API failed to resolve player for history endpoint: " + target, exception);
            sendJson(exchange, 500, Map.of("ok", false, "error", "Failed to resolve player"));
            return;
        }
        if (uuid.isEmpty()) {
            sendJson(exchange, 404, Map.of("ok", false, "error", "Player not found"));
            return;
        }
        try {
            List<PunishmentHistoryRecord> history = service.core().history(uuid.get()).join();
            List<Map<String, Object>> payload = new ArrayList<>(history.size());
            for (PunishmentHistoryRecord record : history) {
                payload.add(toHistoryMap(record));
            }
            sendJson(exchange, 200, Map.of("ok", true, "uuid", uuid.get().toString(), "history", payload));
        } catch (CompletionException exception) {
            service.logError("Forum API player history failed", exception);
            sendJson(exchange, 500, Map.of("ok", false, "error", "Failed to load punishment history"));
        }
    }

    private long resolveDurationSeconds(CreatePunishmentRequest request, PunishmentType type) {
        if (request.durationSeconds != null) {
            if (request.durationSeconds < 0L) {
                throw new IllegalArgumentException("durationSeconds must be >= 0");
            }
            return request.durationSeconds;
        }
        if (!isBlank(request.duration)) {
            return DurationParser.parseToSeconds(request.duration.trim());
        }
        if (type == PunishmentType.WARN) {
            return service.config().warnDurationSeconds();
        }
        return 0L;
    }

    private Map<String, Object> toPunishmentMap(PunishmentRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.internalId());
        map.put("uuid", record.uuid().toString());
        map.put("type", record.type().name());
        map.put("reason", record.reason());
        map.put("actor", record.actor());
        map.put("active", record.active());
        map.put("silent", record.silent());
        map.put("ip", record.ip());
        map.put("startTime", record.startTime().toEpochMilli());
        map.put("endTime", record.endTime() == null ? null : record.endTime().toEpochMilli());
        map.put("durationSeconds", record.durationSeconds());
        return map;
    }

    private Map<String, Object> toHistoryMap(PunishmentHistoryRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.id());
        map.put("internalId", record.internalId());
        map.put("uuid", record.uuid().toString());
        map.put("type", record.type().name());
        map.put("action", record.action());
        map.put("reason", record.reason());
        map.put("actor", record.actor());
        map.put("startTime", record.startTime().toEpochMilli());
        map.put("endTime", record.endTime() == null ? null : record.endTime().toEpochMilli());
        map.put("actionTime", record.actionTime().toEpochMilli());
        return map;
    }

    private String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        if (bytes.length == 0) {
            return "{}";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private List<String> extractSegments(String path) {
        if (!path.startsWith(API_PREFIX)) {
            return List.of();
        }
        String suffix = path.substring(API_PREFIX.length());
        if (suffix.startsWith("/")) {
            suffix = suffix.substring(1);
        }
        if (suffix.isBlank()) {
            return List.of();
        }
        String[] raw = suffix.split("/");
        List<String> segments = new ArrayList<>(raw.length);
        for (String segment : raw) {
            if (!segment.isBlank()) {
                segments.add(decodeSegment(segment));
            }
        }
        return segments;
    }

    private String decodeSegment(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String configuredToken = config.apiToken();
        if (isBlank(configuredToken)) {
            return false;
        }
        String token = exchange.getRequestHeaders().getFirst("X-API-Token");
        if (configuredToken.equals(token)) {
            return true;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String bearer = authorization.substring("Bearer ".length()).trim();
            return configuredToken.equals(bearer);
        }
        return false;
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] body = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Token");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String plainWarnReasons() {
        StringBuilder builder = new StringBuilder();
        List<String> reasons = config.warnAllowedReasons();
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(i + 1).append(") ").append(reasons.get(i));
        }
        return builder.toString();
    }

    private static final class CreatePunishmentRequest {
        String target;
        String type;
        String duration;
        Long durationSeconds;
        String reason;
        String actor;
        Boolean silent;
        Boolean nnr;
    }

    private static final class RevokePunishmentRequest {
        String actor;
        String reason;
    }
}
