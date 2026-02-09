package com.pluginbans.paper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.pluginbans.core.PunishmentRecord;
import com.pluginbans.core.PunishmentRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Executors;

public final class RestServer {
    private final PaperConfig config;
    private final PunishmentRepository repository;
    private final Gson gson = new Gson();
    private HttpServer server;

    public RestServer(PaperConfig config, PunishmentRepository repository) {
        this.config = config;
        this.repository = repository;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(config.restBind(), config.restPort()), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось запустить REST сервер.", exception);
        }
        server.createContext("/punishment", this::handlePunishment);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handlePunishment(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String[] parts = exchange.getRequestURI().getPath().split("/");
        if (parts.length < 3) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        String id = parts[2];
        Optional<PunishmentRecord> record = repository.findById(id).join();
        if (record.isEmpty()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("uuid", record.get().uuid().toString());
        json.addProperty("type", record.get().type());
        json.addProperty("reason", record.get().reason());
        json.addProperty("duration", record.get().durationSeconds());
        json.addProperty("issuedBy", record.get().issuedBy());
        json.addProperty("timestamp", record.get().issuedAt().toEpochMilli());
        byte[] payload = gson.toJson(json).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }
}
