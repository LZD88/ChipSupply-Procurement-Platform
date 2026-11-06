package com.chipsupply.controller;

import com.chipsupply.common.Result;
import com.chipsupply.service.SupplyIntelligenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "供应链决策中心")
@RestController
@RequestMapping("/api/intelligence")
@RequiredArgsConstructor
public class SupplyIntelligenceController {
    private final SupplyIntelligenceService intelligenceService;

    @Operation(summary = "缺货预测与智能补货建议")
    @GetMapping("/replenishment")
    public Result<Map<String, Object>> replenishment() {
        return Result.success(intelligenceService.replenishmentForecast());
    }

    @Operation(summary = "供应商五维风险雷达")
    @GetMapping("/supplier-risks")
    public Result<Map<String, Object>> supplierRisks() {
        return Result.success(intelligenceService.supplierRiskRadar());
    }

    @Operation(summary = "订单异常预警中心")
    @GetMapping("/order-anomalies")
    public Result<Map<String, Object>> orderAnomalies() {
        return Result.success(intelligenceService.orderAnomalies());
    }
}
