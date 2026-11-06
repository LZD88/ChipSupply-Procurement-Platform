package com.chipsupply;

import com.chipsupply.infrastructure.OutboxDispatcher;
import com.chipsupply.infrastructure.RabbitEventPublisher;
import com.chipsupply.service.BusinessEventService;
import com.chipsupply.tracing.TraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class BusinessEventOutboxTest {
    @AfterEach
    void clearTrace() { TraceContext.clear(); }

    @Test
    void businessEventAndOutboxUseSameEventAndTraceIdentity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TraceContext.start("TRACE-UNIT-12345678");
        BusinessEventService service = new BusinessEventService(jdbc, new ObjectMapper());

        service.record("ORDER_CREATED", "PURCHASE_ORDER", 9L, 9L,
                "test", "created", Map.of("orderId", 9));

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(anyString(), arguments.capture());
        Object[] business = arguments.getAllValues().get(0);
        Object[] outbox = arguments.getAllValues().get(1);
        assertEquals(business[0], outbox[0]);
        assertEquals("TRACE-UNIT-12345678", business[1]);
        assertEquals("TRACE-UNIT-12345678", outbox[4]);
    }

    @Test
    void rabbitFailureKeepsOutboxPendingForRetry() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of(
                "id", 1L, "event_id", "EVT-1", "event_type", "ORDER_CREATED",
                "aggregate_type", "PURCHASE_ORDER", "aggregate_id", "9",
                "trace_id", "TRACE-1", "payload_json", "{}", "retry_count", 0)));
        RabbitEventPublisher publisher = mock(RabbitEventPublisher.class);
        doThrow(new IllegalStateException("RabbitMQ down")).when(publisher).publish(anyMap());
        OutboxDispatcher dispatcher = new OutboxDispatcher(jdbc, publisher, new SimpleMeterRegistry());

        dispatcher.dispatch();

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("next_attempt_at"), arguments.capture());
        assertTrue(String.valueOf(arguments.getValue()[0]).contains("RabbitMQ down"));
        assertEquals(1L, arguments.getValue()[2]);
    }
}
