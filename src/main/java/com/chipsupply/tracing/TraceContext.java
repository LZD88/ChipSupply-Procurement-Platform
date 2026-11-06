package com.chipsupply.tracing;

import java.util.UUID;

/** 当前请求的轻量级追踪上下文，供日志、审计与领域事件共享。 */
public final class TraceContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() { }

    public static String start(String requestedTraceId) {
        String traceId = valid(requestedTraceId)
                ? requestedTraceId
                : "TRC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        CURRENT.set(traceId);
        return traceId;
    }

    public static String current() {
        String traceId = CURRENT.get();
        return traceId == null ? start(null) : traceId;
    }

    public static void clear() {
        CURRENT.remove();
    }

    private static boolean valid(String value) {
        return value != null && value.length() >= 8 && value.length() <= 64
                && value.matches("[A-Za-z0-9_-]+");
    }
}
