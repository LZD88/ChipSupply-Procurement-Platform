package com.chipsupply.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** RabbitMQ 业务事件消费者：数据库唯一键保证同一消费者只处理一次。 */
@Component
@RequiredArgsConstructor
public class RabbitBusinessEventConsumer {
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;

    @Value("${chipsupply.events.consumer-name:local-erp}")
    private String consumerName;

    @RabbitListener(queues = "${chipsupply.events.queue:chipsupply.platform.events}")
    @Transactional
    public void consume(Map<String, String> event) {
        String eventId = event.get("eventId");
        if (eventId == null || eventId.isBlank()) {
            metrics.counter("chipsupply.events.consumer.failures", "reason", "missing_event_id").increment();
            throw new IllegalArgumentException("RabbitMQ 事件缺少 eventId");
        }
        try {
            jdbc.update("INSERT INTO event_consumption(consumer_name,event_id,consumed_at) VALUES(?,?,NOW())",
                    consumerName, eventId);
            metrics.counter("chipsupply.events.consumed", "event_type",
                    event.getOrDefault("eventType", "UNKNOWN")).increment();
        } catch (DuplicateKeyException ignored) {
            metrics.counter("chipsupply.events.duplicates").increment();
        } catch (RuntimeException exception) {
            metrics.counter("chipsupply.events.consumer.failures", "reason", "database").increment();
            throw exception;
        }
    }

    void setConsumerNameForTest(String name) {
        this.consumerName = name;
    }
}
