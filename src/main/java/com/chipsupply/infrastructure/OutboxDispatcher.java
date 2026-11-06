package com.chipsupply.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** MySQL Outbox -> RabbitMQ。发布失败保留 PENDING，稍后指数退避重试。 */
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final JdbcTemplate jdbc;
    private final RabbitEventPublisher publisher;
    private final MeterRegistry metrics;

    @Scheduled(fixedDelayString = "${chipsupply.events.dispatch-delay-ms:1000}")
    public void dispatch() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,event_id,event_type,aggregate_type,aggregate_id,trace_id,payload_json,retry_count
                FROM outbox_event WHERE status='PENDING' AND next_attempt_at<=NOW()
                ORDER BY id LIMIT 50
                """);
        for (Map<String, Object> row : rows) publish(row);
    }

    private void publish(Map<String, Object> row) {
        long id = ((Number) row.get("id")).longValue();
        int retry = ((Number) row.get("retry_count")).intValue();
        try {
            publisher.publish(Map.of(
                    "eventId", String.valueOf(row.get("event_id")),
                    "eventType", String.valueOf(row.get("event_type")),
                    "aggregateType", String.valueOf(row.get("aggregate_type")),
                    "aggregateId", String.valueOf(row.get("aggregate_id")),
                    "traceId", String.valueOf(row.get("trace_id")),
                    "payload", String.valueOf(row.get("payload_json"))));
            jdbc.update("UPDATE outbox_event SET status='PUBLISHED',published_at=NOW(),last_error=NULL WHERE id=? AND status='PENDING'", id);
            metrics.counter("chipsupply.outbox.published").increment();
        } catch (Exception exception) {
            int seconds = Math.min(300, 1 << Math.min(retry, 8));
            jdbc.update("""
                    UPDATE outbox_event SET retry_count=retry_count+1,last_error=?,
                    next_attempt_at=DATE_ADD(NOW(),INTERVAL ? SECOND) WHERE id=? AND status='PENDING'
                    """, abbreviate(exception.getMessage()), seconds, id);
            metrics.counter("chipsupply.outbox.publish.failures").increment();
            log.warn("RabbitMQ 暂不可用，Outbox {} 将在 {} 秒后重试: {}",
                    row.get("event_id"), seconds, abbreviate(exception.getMessage()));
        }
    }

    private String abbreviate(String value) {
        if (value == null) return "unknown";
        return value.length() > 480 ? value.substring(0, 480) : value;
    }
}
