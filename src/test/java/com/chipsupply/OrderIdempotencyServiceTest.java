package com.chipsupply;

import com.chipsupply.common.exception.BusinessException;
import com.chipsupply.entity.PurchaseOrderWithDetails;
import com.chipsupply.service.OrderIdempotencyService;
import com.chipsupply.service.PurchaseOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OrderIdempotencyServiceTest {
    @Test
    void sameKeyAndPayloadReplaysStoredOrderWithoutCreatingAgain() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PurchaseOrderService orders = mock(PurchaseOrderService.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        PurchaseOrderWithDetails request = order("PO-001");
        PurchaseOrderWithDetails stored = order("PO-001");
        stored.setId(77L);
        when(jdbc.update(startsWith("INSERT INTO api_idempotency"), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(jdbc.queryForMap(anyString(), eq("agent-order:12345678"))).thenReturn(Map.of(
                "request_hash", sha256(mapper, request),
                "response_json", json(mapper, stored),
                "status", "COMPLETED"));

        OrderIdempotencyService service = new OrderIdempotencyService(jdbc, orders, mapper, new SimpleMeterRegistry(), txManager());
        OrderIdempotencyService.Outcome outcome = service.create("agent-order:12345678", request);

        assertTrue(outcome.replayed());
        assertEquals(77L, outcome.order().getId());
        verify(orders, never()).createOrder(any());
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PurchaseOrderService orders = mock(PurchaseOrderService.class);
        when(jdbc.update(startsWith("INSERT INTO api_idempotency"), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(jdbc.queryForMap(anyString(), anyString())).thenReturn(Map.of(
                "request_hash", "different-hash", "response_json", "{}", "status", "COMPLETED"));
        OrderIdempotencyService service = new OrderIdempotencyService(
                jdbc, orders, new ObjectMapper().findAndRegisterModules(), new SimpleMeterRegistry(), txManager());

        assertThrows(BusinessException.class,
                () -> service.create("agent-order:12345678", order("PO-002")));
        verify(orders, never()).createOrder(any());
    }

    @Test
    void lookupDistinguishesAbsentAndCompletedWithoutCreatingOrder() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PurchaseOrderService orders = mock(PurchaseOrderService.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        OrderIdempotencyService service = new OrderIdempotencyService(
                jdbc, orders, mapper, new SimpleMeterRegistry(), txManager());

        when(jdbc.queryForList(anyString(), eq("agent-order:absent1"))).thenReturn(List.of());
        assertEquals("ABSENT", service.lookup("agent-order:absent1").status());

        PurchaseOrderWithDetails stored = order("PO-LOOKUP");
        stored.setId(88L);
        when(jdbc.queryForList(anyString(), eq("agent-order:complete1"))).thenReturn(List.of(Map.of(
                "response_json", json(mapper, stored), "status", "COMPLETED")));
        OrderIdempotencyService.Lookup completed = service.lookup("agent-order:complete1");
        assertEquals("COMPLETED", completed.status());
        assertEquals(88L, completed.order().getId());
        verify(orders, never()).createOrder(any());
    }

    private PurchaseOrderWithDetails order(String number) {
        PurchaseOrderWithDetails order = new PurchaseOrderWithDetails();
        order.setOrderNumber(number);
        return order;
    }

    private String json(ObjectMapper mapper, Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new RuntimeException(exception); }
    }

    private String sha256(ObjectMapper mapper, Object value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(value));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) { throw new RuntimeException(exception); }
    }

    private PlatformTransactionManager txManager() {
        return new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition definition) { return new SimpleTransactionStatus(); }
            public void commit(TransactionStatus status) { }
            public void rollback(TransactionStatus status) { }
        };
    }
}
