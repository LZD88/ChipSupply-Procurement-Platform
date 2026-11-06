package com.chipsupply.service;

import com.chipsupply.common.enums.ResultCode;
import com.chipsupply.common.exception.BusinessException;
import com.chipsupply.entity.PurchaseOrderWithDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderIdempotencyService {
    private final JdbcTemplate jdbc;
    private final PurchaseOrderService purchaseOrderService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry metrics;
    private final PlatformTransactionManager transactionManager;

    public record Outcome(PurchaseOrderWithDetails order, boolean replayed) { }

    @Transactional(rollbackFor = Exception.class)
    public Outcome create(String key, PurchaseOrderWithDetails order) {
        if (key == null || key.isBlank()) {
            if (!purchaseOrderService.createOrder(order)) throw new BusinessException(ResultCode.DATA_EXISTS);
            return new Outcome(order, false);
        }
        String normalized = key.trim();
        if (normalized.length() > 128 || !normalized.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new BusinessException("Idempotency-Key 格式无效（需为 8-128 位安全字符）");
        }
        String hash = hash(order);
        if (!reserve(normalized, hash)) {
            return replay(normalized, hash);
        }
        try {
            if (!purchaseOrderService.createOrder(order)) throw new BusinessException(ResultCode.DATA_EXISTS);
            String response = json(order);
            jdbc.update("""
                    UPDATE api_idempotency SET resource_id=?,response_json=?,status='COMPLETED',updated_at=NOW()
                    WHERE idempotency_key=?
                    """, order.getId(), response, normalized);
        } catch (RuntimeException exception) {
            releaseFailedReservation(normalized);
            throw exception;
        }
        metrics.counter("chipsupply.idempotency.created").increment();
        return new Outcome(order, false);
    }

    private boolean reserve(String key, String hash) {
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return Boolean.TRUE.equals(transaction.execute(status -> {
                jdbc.update("""
                        INSERT INTO api_idempotency(idempotency_key,request_hash,resource_type,status,expires_at,created_at,updated_at)
                        VALUES (?,?,'PURCHASE_ORDER','PROCESSING',?,NOW(),NOW())
                        """, key, hash, LocalDateTime.now().plusHours(24));
                return true;
            }));
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    private void releaseFailedReservation(String key) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> jdbc.update(
                "DELETE FROM api_idempotency WHERE idempotency_key=? AND status='PROCESSING'", key));
    }

    private Outcome replay(String key, String hash) {
        Map<String, Object> record = jdbc.queryForMap("""
                SELECT request_hash,response_json,status FROM api_idempotency WHERE idempotency_key=?
                """, key);
        if (!hash.equals(record.get("request_hash"))) {
            metrics.counter("chipsupply.idempotency.conflicts").increment();
            throw new BusinessException("同一 Idempotency-Key 不可用于不同的订单内容");
        }
        if (!"COMPLETED".equals(record.get("status")) || record.get("response_json") == null) {
            throw new BusinessException("相同订单请求正在处理中，请稍后重试");
        }
        try {
            PurchaseOrderWithDetails saved = objectMapper.readValue(String.valueOf(record.get("response_json")), PurchaseOrderWithDetails.class);
            metrics.counter("chipsupply.idempotency.replayed").increment();
            return new Outcome(saved, true);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("幂等响应读取失败", exception);
        }
    }

    private String hash(PurchaseOrderWithDetails order) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(order).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new BusinessException("订单摘要生成失败", exception);
        }
    }

    private String json(PurchaseOrderWithDetails order) {
        try { return objectMapper.writeValueAsString(order); }
        catch (JsonProcessingException exception) { throw new BusinessException("订单响应序列化失败", exception); }
    }
}
