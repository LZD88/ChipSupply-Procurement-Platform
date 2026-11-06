package com.chipsupply.controller;

import com.chipsupply.audit.Auditable;
import com.chipsupply.common.Result;
import com.chipsupply.service.BusinessEventService;
import com.chipsupply.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "采购控制塔")
@RestController
@RequestMapping("/api/control-tower")
@RequiredArgsConstructor
public class ProcurementControlController {
    private final ReconciliationService reconciliationService;
    private final BusinessEventService eventService;

    @Operation(summary = "三单匹配与采购对账总览")
    @GetMapping("/reconciliation")
    public Result<Map<String, Object>> reconciliation() {
        return Result.success(reconciliationService.overview());
    }

    @Operation(summary = "确认采购收货单")
    @PostMapping("/reconciliation/{orderId}/receipt")
    @Auditable(action = "GOODS_RECEIPT_CONFIRM", resourceType = "PURCHASE_ORDER")
    public Result<Void> saveReceipt(@PathVariable Long orderId, @Valid @RequestBody ReceiptRequest request) {
        reconciliationService.saveReceipt(orderId, request.quantity(), request.amount(), request.receiver(), request.location());
        return Result.success();
    }

    @Operation(summary = "录入或更新供应商发票")
    @PostMapping("/reconciliation/{orderId}/invoice")
    @Auditable(action = "SUPPLIER_INVOICE_RECEIVE", resourceType = "PURCHASE_ORDER")
    public Result<Void> saveInvoice(@PathVariable Long orderId, @Valid @RequestBody InvoiceRequest request) {
        reconciliationService.saveInvoice(orderId, request.invoiceNumber(), request.quantity(), request.amount(), request.taxRate());
        return Result.success();
    }

    @Operation(summary = "事件链路总览")
    @GetMapping("/traces")
    public Result<Map<String, Object>> traces() {
        return Result.success(eventService.overview());
    }

    @Operation(summary = "获取单条端到端事件链路")
    @GetMapping("/traces/{traceId}")
    public Result<List<Map<String, Object>>> trace(@PathVariable String traceId) {
        return Result.success(eventService.trace(traceId));
    }

    public record ReceiptRequest(@Min(1) Integer quantity, @DecimalMin("0.01") BigDecimal amount,
                                 @NotBlank String receiver, @NotBlank String location) { }
    public record InvoiceRequest(@NotBlank String invoiceNumber, @Min(1) Integer quantity,
                                 @DecimalMin("0.01") BigDecimal amount, BigDecimal taxRate) { }
}
