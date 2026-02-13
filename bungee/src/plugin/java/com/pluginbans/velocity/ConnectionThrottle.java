package com.pluginbans.velocity;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectionThrottle {
    private final int maxConnections;
    private final int windowSeconds;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public ConnectionThrottle(int maxConnections, int windowSeconds) {
        this.maxConnections = Math.max(1, maxConnections);
        this.windowSeconds = Math.max(1, windowSeconds);
    }

    public boolean tryAcquire(String ip) {
        long now = Instant.now().getEpochSecond();
        Deque<Long> deque = attempts.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > windowSeconds) {
                deque.removeFirst();
            }
            if (deque.size() >= maxConnections) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
