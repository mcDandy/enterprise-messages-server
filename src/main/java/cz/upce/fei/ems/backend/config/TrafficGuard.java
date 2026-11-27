package cz.upce.fei.ems.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/** In-process back-pressure for HTTP and WebSocket traffic. */
@Component
public class TrafficGuard {
    private static final int MAX_TRACKED_CLIENTS = 20_000;
    private static final long BUCKET_IDLE_NANOS = 10 * 60 * 1_000_000_000L;

    private final TokenBuckets buckets = new TokenBuckets();
    private final Semaphore httpConcurrency;
    private final Semaphore websocketWorkConcurrency;
    private final AtomicInteger activeWebSockets = new AtomicInteger();
    private final int maxWebSockets;

    public TrafficGuard(
            @Value("${api.limits.http-concurrent-requests:128}") int maxHttpRequests,
            @Value("${api.limits.websocket-concurrent-messages:32}") int maxWebSocketWork,
            @Value("${api.limits.websocket-connections:2000}") int maxWebSockets) {
        if (maxHttpRequests < 1 || maxWebSocketWork < 1 || maxWebSockets < 1) {
            throw new IllegalArgumentException("Traffic limits must be positive");
        }
        this.httpConcurrency = new Semaphore(maxHttpRequests);
        this.websocketWorkConcurrency = new Semaphore(maxWebSocketWork);
        this.maxWebSockets = maxWebSockets;
    }

    /** @return whether this HTTP request passes both global and per-client token buckets */
    public boolean allowHttpRequest(String client) {
        return buckets.allow("http:global", 1_200, 100, 1)
                && buckets.allow("http:client:" + client, 240, 20, 1);
    }

    /** @return whether this client may attempt another WebSocket handshake */
    public boolean allowWebSocketHandshake(String client) {
        return buckets.allow("ws:handshake:" + client, 30, 2, 1);
    }

    /** @return whether this WebSocket message passes global and per-client rate limits */
    public boolean allowWebSocketMessage(String client) {
        return buckets.allow("ws:global", 2_000, 1_000, 1)
                && buckets.allow("ws:client:" + client, 240, 20, 1);
    }

    /** Attempts to reserve an in-flight HTTP request slot without blocking. */
    public boolean tryEnterHttp() { return httpConcurrency.tryAcquire(); }

    /** Releases a slot previously acquired with {@link #tryEnterHttp()}. */
    public void leaveHttp() { httpConcurrency.release(); }

    /** Attempts to reserve a WebSocket message-processing slot without blocking. */
    public boolean tryEnterWebSocketWork() { return websocketWorkConcurrency.tryAcquire(); }

    /** Releases a slot previously acquired with {@link #tryEnterWebSocketWork()}. */
    public void leaveWebSocketWork() { websocketWorkConcurrency.release(); }

    /** Atomically reserves one active WebSocket connection if the configured cap allows it. */
    public boolean tryOpenWebSocket() {
        while (true) {
            int current = activeWebSockets.get();
            if (current >= maxWebSockets) return false;
            if (activeWebSockets.compareAndSet(current, current + 1)) return true;
        }
    }

    /** Releases one active WebSocket connection permit without allowing the count below zero. */
    public void closeWebSocket() { activeWebSockets.updateAndGet(current -> Math.max(0, current - 1)); }

    private static final class TokenBuckets {
        private final LinkedHashMap<String, Bucket> values = new LinkedHashMap<>(256, 0.75f, true);
        private long operations;

        synchronized boolean allow(String key, int capacity, double tokensPerSecond, int cost) {
            long now = System.nanoTime();
            Bucket bucket = values.get(key);
            if (bucket == null) {
                pruneIfNeeded(now);
                if (values.size() >= MAX_TRACKED_CLIENTS) {
                    Iterator<String> oldest = values.keySet().iterator();
                    if (oldest.hasNext()) values.remove(oldest.next());
                }
                bucket = new Bucket(capacity, now);
                values.put(key, bucket);
            }
            bucket.tokens = Math.min(capacity,
                    bucket.tokens + (now - bucket.updatedAt) / 1_000_000_000d * tokensPerSecond);
            bucket.updatedAt = now;
            if (bucket.tokens < cost) return false;
            bucket.tokens -= cost;
            return true;
        }

        private void pruneIfNeeded(long now) {
            if (++operations % 256 != 0) return;
            Iterator<Map.Entry<String, Bucket>> iterator = values.entrySet().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().getValue().updatedAt > BUCKET_IDLE_NANOS) iterator.remove();
            }
        }
    }

    private static final class Bucket {
        private double tokens;
        private long updatedAt;

        private Bucket(double tokens, long updatedAt) {
            this.tokens = tokens;
            this.updatedAt = updatedAt;
        }
    }
}
