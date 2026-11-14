package com.fotofetch.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class RateLimitingFilter implements Filter {

    // Thread-safe in-memory token buckets per IP
    private final Map<String, RequestBucket> searchLimits = new ConcurrentHashMap<>();
    private final Map<String, RequestBucket> authLimits = new ConcurrentHashMap<>();
    private final Map<String, RequestBucket> zipLimits = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();
        String clientIp = getClientIp(httpRequest);

        // 1. Rate limit facial recognition search: Max 15 searches per minute per IP
        if (path.contains("/public/events/") && path.endsWith("/search")) {
            RequestBucket bucket = searchLimits.computeIfAbsent(clientIp, k -> new RequestBucket(15, 60000));
            if (!bucket.tryAcquire()) {
                sendRateLimitError(httpResponse, "Too many facial recognition searches. Please wait a minute before trying again.");
                return;
            }
        }

        // 2. Rate limit login and register: Max 10 attempts per minute per IP
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")) {
            RequestBucket bucket = authLimits.computeIfAbsent(clientIp, k -> new RequestBucket(10, 60000));
            if (!bucket.tryAcquire()) {
                sendRateLimitError(httpResponse, "Too many authentication attempts. Please wait a minute before trying again.");
                return;
            }
        }

        // 3. Rate limit ZIP downloads: Max 6 per 5 minutes per IP
        if (path.contains("/photos/zip")) {
            RequestBucket bucket = zipLimits.computeIfAbsent(clientIp, k -> new RequestBucket(6, 300000));
            if (!bucket.tryAcquire()) {
                sendRateLimitError(httpResponse, "Too many ZIP download requests. Please wait a few minutes before trying again.");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void sendRateLimitError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429); // 429 Too Many Requests
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"success\":false,\"message\":\"%s\"}", message));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class RequestBucket {
        private final int maxTokens;
        private final long refillIntervalMs;
        private int tokens;
        private long lastRefillTime;

        public RequestBucket(int maxTokens, long refillIntervalMs) {
            this.maxTokens = maxTokens;
            this.refillIntervalMs = refillIntervalMs;
            this.tokens = maxTokens;
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - lastRefillTime > refillIntervalMs) {
                tokens = maxTokens;
                lastRefillTime = now;
            }
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }
    }
}
