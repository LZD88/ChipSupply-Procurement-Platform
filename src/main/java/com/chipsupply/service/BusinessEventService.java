package com.chipsupply.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chipsupply.security.AuthenticatedUser;
import com.chipsupply.tracing.TraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessEventService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void record(String eventType, String aggregateType, Object aggregateId, Long orderId,
                       String source, String summary, Map<String, ?> payload) {
        String eventId = "EVT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String traceId = TraceContext.current();
        String payloadJson = json(payload);
        jdbc.update("""
                INSERT INTO business_event
                (event_id,trace_id,event_type,aggregate_type,aggregate_id,order_id,actor,source,status,summary,payload_json,occurred_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                eventId, traceId, eventType, aggregateType, String.valueOf(aggregateId), orderId,
                currentActor(), source, "COMPLETED", summary, payloadJson, LocalDateTime.now());
        jdbc.update("""
                INSERT INTO outbox_event
                (event_id,aggregate_type,aggregate_id,event_type,trace_id,payload_json,status,retry_count,next_attempt_at,created_at)
                VALUES (?,?,?,?,?,?,'PENDING',0,NOW(),NOW())
                """, eventId, aggregateType, String.valueOf(aggregateId), eventType, traceId, payloadJson);
    }

    public Map<String, Object> overview() {
        List<Map<String, Object>> events = jdbc.queryForList("""
                SELECT e.id,e.event_id AS eventId,e.trace_id AS traceId,e.event_type AS eventType,
                       e.aggregate_type AS aggregateType,e.aggregate_id AS aggregateId,e.order_id AS orderId,
                       e.actor,e.source,e.status,e.summary,e.payload_json AS payloadJson,e.occurred_at AS occurredAt,
                       o.order_number AS orderNumber
                FROM business_event e LEFT JOIN purchase_order o ON o.id=e.order_id
                ORDER BY e.occurred_at DESC,e.id DESC LIMIT 300
                """);
        List<Map<String, Object>> traces = jdbc.queryForList("""
                SELECT e.trace_id AS traceId,MAX(e.occurred_at) AS lastOccurredAt,COUNT(*) AS eventCount,
                       COUNT(DISTINCT e.source) AS serviceCount,MAX(e.order_id) AS orderId,
                       MAX(o.order_number) AS orderNumber,
                       MAX(CASE WHEN e.status='FAILED' THEN 1 ELSE 0 END) AS hasFailure
                FROM business_event e LEFT JOIN purchase_order o ON o.id=e.order_id
                GROUP BY e.trace_id ORDER BY lastOccurredAt DESC LIMIT 80
                """);
        Map<String, Object> summary = jdbc.queryForMap("""
                SELECT COUNT(*) AS eventCount,COUNT(DISTINCT trace_id) AS traceCount,
                       COUNT(DISTINCT source) AS serviceCount,
                       SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) AS failureCount
                FROM business_event
                """);
        return Map.of("summary", summary, "traces", traces, "events", events);
    }

    public List<Map<String, Object>> trace(String traceId) {
        return jdbc.queryForList("""
                SELECT e.event_id AS eventId,e.trace_id AS traceId,e.event_type AS eventType,
                       e.aggregate_type AS aggregateType,e.aggregate_id AS aggregateId,e.order_id AS orderId,
                       e.actor,e.source,e.status,e.summary,e.payload_json AS payloadJson,e.occurred_at AS occurredAt,
                       o.order_number AS orderNumber
                FROM business_event e LEFT JOIN purchase_order o ON o.id=e.order_id
                WHERE e.trace_id=? ORDER BY e.occurred_at,e.id
                """, traceId);
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.username();
        }
        return "system";
    }

    private String json(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
