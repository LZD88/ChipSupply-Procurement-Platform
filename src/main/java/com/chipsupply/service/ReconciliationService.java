package com.chipsupply.service;

import com.chipsupply.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReconciliationService {
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("1.00");

    private final JdbcTemplate jdbc;
    private final BusinessEventService eventService;

    public Map<String, Object> overview() {
        List<Map<String, Object>> raw = jdbc.queryForList("""
                SELECT o.id AS orderId,o.order_number AS orderNumber,o.status AS orderStatus,
                       o.total_amount AS orderAmount,o.order_time AS orderTime,
                       COALESCE(SUM(od.quantity),0) AS orderQuantity,
                       MIN(s.name) AS supplierName,
                       gr.id AS receiptId,gr.receipt_number AS receiptNumber,gr.received_quantity AS receivedQuantity,
                       gr.received_amount AS receivedAmount,gr.received_at AS receivedAt,
                       si.id AS invoiceId,si.invoice_number AS invoiceNumber,si.invoice_quantity AS invoiceQuantity,
                       si.invoice_amount AS invoiceAmount,si.invoice_date AS invoiceDate,si.status AS invoiceStatus
                FROM purchase_order o
                LEFT JOIN order_detail od ON od.order_id=o.id AND od.deleted=0
                LEFT JOIN part p ON p.id=od.part_id
                LEFT JOIN supplier s ON s.id=p.supplier_id
                LEFT JOIN goods_receipt gr ON gr.order_id=o.id
                LEFT JOIN supplier_invoice si ON si.order_id=o.id
                WHERE o.deleted=0 AND o.status<>5
                GROUP BY o.id,gr.id,si.id ORDER BY o.order_time DESC
                """);
        List<Map<String, Object>> items = raw.stream().map(this::evaluate).toList();
        long matched = count(items, "MATCHED");
        long discrepancy = count(items, "DISCREPANCY");
        long waiting = count(items, "WAITING");
        BigDecimal exceptionAmount = items.stream().filter(item -> "DISCREPANCY".equals(item.get("matchStatus")))
                .map(item -> (BigDecimal) item.get("amountDifference")).map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("matchedCount", matched);
        summary.put("discrepancyCount", discrepancy);
        summary.put("waitingCount", waiting);
        summary.put("matchRate", items.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(matched * 100D / items.size()).setScale(1, RoundingMode.HALF_UP));
        summary.put("exceptionAmount", exceptionAmount.setScale(2, RoundingMode.HALF_UP));
        summary.put("amountTolerance", AMOUNT_TOLERANCE);
        return Map.of("summary", summary, "items", items);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveReceipt(Long orderId, Integer quantity, BigDecimal amount, String receiver, String location) {
        requireOrder(orderId);
        if (quantity == null || quantity <= 0 || amount == null || amount.signum() <= 0) {
            throw new BusinessException("收货数量和金额必须大于零");
        }
        jdbc.update("""
                INSERT INTO goods_receipt(receipt_number,order_id,received_quantity,received_amount,received_at,warehouse_location,receiver,status,remark)
                VALUES(?,?,?,?,?,?,?,'CONFIRMED','手工确认收货')
                ON DUPLICATE KEY UPDATE received_quantity=VALUES(received_quantity),received_amount=VALUES(received_amount),
                received_at=VALUES(received_at),warehouse_location=VALUES(warehouse_location),receiver=VALUES(receiver),status='CONFIRMED'
                """, "GR-" + orderId + "-" + System.currentTimeMillis(), orderId, quantity, amount,
                LocalDateTime.now(), location, receiver);
        eventService.record("GOODS_RECEIPT_CONFIRMED", "PURCHASE_ORDER", orderId, orderId,
                "warehouse-service", "收货单已确认，进入三单匹配", Map.of("quantity", quantity, "amount", amount));
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveInvoice(Long orderId, String invoiceNumber, Integer quantity, BigDecimal amount, BigDecimal taxRate) {
        requireOrder(orderId);
        if (invoiceNumber == null || invoiceNumber.isBlank() || quantity == null || quantity <= 0
                || amount == null || amount.signum() <= 0) {
            throw new BusinessException("发票号码、数量和金额不能为空");
        }
        Long supplierId = jdbc.query("""
                SELECT MIN(p.supplier_id) FROM order_detail od JOIN part p ON p.id=od.part_id WHERE od.order_id=?
                """, rs -> rs.next() ? rs.getLong(1) : null, orderId);
        jdbc.update("""
                INSERT INTO supplier_invoice(invoice_number,order_id,supplier_id,invoice_quantity,invoice_amount,invoice_date,tax_rate,status,remark)
                VALUES(?,?,?,?,?,?,?,'RECEIVED','财务录入')
                ON DUPLICATE KEY UPDATE invoice_number=VALUES(invoice_number),invoice_quantity=VALUES(invoice_quantity),
                invoice_amount=VALUES(invoice_amount),invoice_date=VALUES(invoice_date),tax_rate=VALUES(tax_rate),status='RECEIVED'
                """, invoiceNumber.trim(), orderId, supplierId, quantity, amount, LocalDate.now(),
                taxRate == null ? new BigDecimal("13.00") : taxRate);
        Map<String, Object> result = overview().get("items") instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .filter(item -> String.valueOf(orderId).equals(String.valueOf(item.get("orderId")))).findFirst().orElse(Map.of())
                : Map.of();
        eventService.record("SUPPLIER_INVOICE_RECEIVED", "PURCHASE_ORDER", orderId, orderId,
                "finance-service", "供应商发票已接收，已执行三单核验",
                Map.of("invoiceNumber", invoiceNumber, "matchStatus", result.getOrDefault("matchStatus", "WAITING")));
    }

    public void ensureReceiptFromOrder(Long orderId) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM goods_receipt WHERE order_id=?", Integer.class, orderId);
        if (existing != null && existing > 0) return;
        Map<String, Object> order = jdbc.queryForMap("""
                SELECT o.total_amount AS amount,COALESCE(SUM(od.quantity),0) AS quantity
                FROM purchase_order o JOIN order_detail od ON od.order_id=o.id WHERE o.id=? GROUP BY o.id
                """, orderId);
        saveReceipt(orderId, ((Number) order.get("quantity")).intValue(), (BigDecimal) order.get("amount"),
                "系统入库", "ESD-A / 验收区");
    }

    private Map<String, Object> evaluate(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>(row);
        boolean hasReceipt = row.get("receiptId") != null;
        boolean hasInvoice = row.get("invoiceId") != null;
        int orderQty = number(row.get("orderQuantity")).intValue();
        int receiptQty = number(row.get("receivedQuantity")).intValue();
        int invoiceQty = number(row.get("invoiceQuantity")).intValue();
        BigDecimal poAmount = decimal(row.get("orderAmount"));
        BigDecimal receiptAmount = decimal(row.get("receivedAmount"));
        BigDecimal invoiceAmount = decimal(row.get("invoiceAmount"));
        BigDecimal amountDifference = hasInvoice ? invoiceAmount.subtract(poAmount) : BigDecimal.ZERO;
        List<String> differences = new ArrayList<>();
        if (!hasReceipt) differences.add("缺少收货单");
        if (!hasInvoice) differences.add("缺少供应商发票");
        if (hasReceipt && orderQty != receiptQty) differences.add("采购数量与实收数量不一致");
        if (hasReceipt && hasInvoice && receiptQty != invoiceQty) differences.add("实收数量与开票数量不一致");
        if (hasReceipt && receiptAmount.subtract(poAmount).abs().compareTo(AMOUNT_TOLERANCE) > 0) differences.add("采购金额与收货金额不一致");
        if (hasInvoice && amountDifference.abs().compareTo(AMOUNT_TOLERANCE) > 0) differences.add("发票金额超出容差");
        String status = !hasReceipt || !hasInvoice ? "WAITING" : differences.isEmpty() ? "MATCHED" : "DISCREPANCY";
        item.put("matchStatus", status);
        item.put("amountDifference", amountDifference.setScale(2, RoundingMode.HALF_UP));
        item.put("differences", differences);
        item.put("nextAction", switch (status) {
            case "MATCHED" -> "核验通过，可进入付款审批";
            case "DISCREPANCY" -> "冻结付款，指派采购或仓储复核差异";
            default -> !hasReceipt ? "等待仓储确认收货" : "等待财务录入供应商发票";
        });
        return item;
    }

    private void requireOrder(Long orderId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM purchase_order WHERE id=? AND deleted=0", Integer.class, orderId);
        if (count == null || count == 0) throw new BusinessException("采购订单不存在");
    }

    private long count(List<Map<String, Object>> items, String status) {
        return items.stream().filter(item -> status.equals(item.get("matchStatus"))).count();
    }

    private Number number(Object value) {
        return value instanceof Number n ? n : 0;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }
}
