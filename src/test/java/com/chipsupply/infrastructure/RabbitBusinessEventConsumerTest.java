package com.chipsupply.infrastructure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RabbitBusinessEventConsumerTest {
    @Test
    void recordsConsumptionAndCountsSuccessfulEvent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        RabbitBusinessEventConsumer consumer = new RabbitBusinessEventConsumer(jdbc, metrics);
        consumer.setConsumerNameForTest("test-consumer");

        consumer.consume(Map.of("eventId", "EVT-1", "eventType", "ORDER_CREATED"));

        verify(jdbc).update(anyString(), eq("test-consumer"), eq("EVT-1"));
        assertEquals(1.0, metrics.get("chipsupply.events.consumed").counter().count());
    }

    @Test
    void duplicateEventIsAcknowledgedWithoutRunningTwice() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(jdbc).update(anyString(), eq("test-consumer"), eq("EVT-1"));
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        RabbitBusinessEventConsumer consumer = new RabbitBusinessEventConsumer(jdbc, metrics);
        consumer.setConsumerNameForTest("test-consumer");

        consumer.consume(Map.of("eventId", "EVT-1", "eventType", "ORDER_CREATED"));

        assertEquals(1.0, metrics.get("chipsupply.events.duplicates").counter().count());
    }

    @Test
    void malformedEventFailsSoRabbitCanRetryAndDeadLetterIt() {
        RabbitBusinessEventConsumer consumer = new RabbitBusinessEventConsumer(
                mock(JdbcTemplate.class), new SimpleMeterRegistry());
        consumer.setConsumerNameForTest("test-consumer");

        assertThrows(IllegalArgumentException.class, () -> consumer.consume(Map.of("eventType", "UNKNOWN")));
    }
}
