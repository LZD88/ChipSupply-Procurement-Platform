package com.chipsupply.config;

import com.chipsupply.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 订单创建接口限流。Redis 共享计数，故障时退化为实例内计数。 */
@Component
@RequiredArgsConstructor
public class OrderRateLimitFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MeterRegistry metrics;
    private final ConcurrentHashMap<String, AtomicInteger> local = new ConcurrentHashMap<>();
    @Value("${chipsupply.rate-limit.order-create-per-minute:30}") private int limit;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && "/api/orders/create".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String identity = request.getHeader("Authorization");
        if (identity == null) identity = request.getRemoteAddr();
        String bucket = Long.toString(Instant.now().getEpochSecond() / 60);
        String identityKey = Integer.toHexString(identity.hashCode());
        long count;
        try {
            String key = "chipsupply:ratelimit:order:" + identityKey + ":" + bucket;
            Long current = redis.opsForValue().increment(key);
            if (current != null && current == 1) redis.expire(key, Duration.ofSeconds(70));
            count = current == null ? 1 : current;
        } catch (Exception exception) {
            count = local.computeIfAbsent(identityKey + ':' + bucket, ignored -> new AtomicInteger()).incrementAndGet();
            if (local.size() > 10000) local.clear();
        }
        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(0, limit - count)));
        if (count > limit) {
            metrics.counter("chipsupply.rate_limit.rejected", "endpoint", "order-create").increment();
            response.setStatus(429);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Result.error(429, "请求过于频繁，请稍后重试"));
            return;
        }
        chain.doFilter(request, response);
    }
}
