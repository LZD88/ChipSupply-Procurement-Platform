package com.chipsupply.service;

import com.chipsupply.entity.Inventory;
import com.chipsupply.entity.Logistics;
import com.chipsupply.entity.OrderDetail;
import com.chipsupply.entity.Part;
import com.chipsupply.entity.PurchaseOrder;
import com.chipsupply.entity.Supplier;
import com.chipsupply.mapper.InventoryMapper;
import com.chipsupply.mapper.LogisticsMapper;
import com.chipsupply.mapper.OrderDetailMapper;
import com.chipsupply.mapper.PartMapper;
import com.chipsupply.mapper.PurchaseOrderMapper;
import com.chipsupply.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基于现有业务数据生成可解释的供应链决策信号。
 *
 * <p>当前版本不依赖黑盒模型：所有分值、阈值和建议数量都有可追溯的业务依据，
 * 适合作为后续接入时序预测模型或外部供应风险数据的稳定 API 契约。</p>
 */
@Service
@RequiredArgsConstructor
public class SupplyIntelligenceService {
    private static final int LOOKBACK_DAYS = 180;

    private final InventoryMapper inventoryMapper;
    private final PartMapper partMapper;
    private final SupplierMapper supplierMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final LogisticsMapper logisticsMapper;

    public Map<String, Object> replenishmentForecast() {
        List<Inventory> inventories = inventoryMapper.selectList(null);
        Map<Long, Part> parts = partMapper.selectList(null).stream()
                .collect(Collectors.toMap(Part::getId, part -> part));
        Map<Long, Supplier> suppliers = supplierMapper.selectList(null).stream()
                .collect(Collectors.toMap(Supplier::getId, supplier -> supplier));
        Map<Long, PurchaseOrder> orders = purchaseOrderMapper.selectList(null).stream()
                .collect(Collectors.toMap(PurchaseOrder::getId, order -> order));

        LocalDateTime cutoff = LocalDateTime.now().minusDays(LOOKBACK_DAYS);
        Map<Long, Integer> historicalQuantity = new HashMap<>();
        for (OrderDetail detail : orderDetailMapper.selectList(null)) {
            PurchaseOrder order = orders.get(detail.getOrderId());
            if (order == null || !Objects.equals(order.getStatus(), 4) || order.getOrderTime() == null
                    || order.getOrderTime().isBefore(cutoff)) {
                continue;
            }
            historicalQuantity.merge(detail.getPartId(), safeInt(detail.getQuantity()), Integer::sum);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Inventory inventory : inventories) {
            Part part = parts.get(inventory.getPartId());
            if (part == null) {
                continue;
            }
            int current = safeInt(inventory.getCurrentQuantity());
            int safety = Math.max(safeInt(inventory.getSafetyStock()), safeInt(part.getStockWarningValue()));
            int leadTime = Math.max(1, safeInt(part.getLeadTimeDays()));
            int history = historicalQuantity.getOrDefault(part.getId(), 0);
            double dailyDemand = history > 0
                    ? history / (double) LOOKBACK_DAYS
                    : Math.max(1D, safety / 30D);
            double coverageDays = current / dailyDemand;
            int reorderPoint = (int) Math.ceil(dailyDemand * leadTime + safety);
            int targetStock = (int) Math.ceil(reorderPoint + dailyDemand * 30);
            int rawQuantity = Math.max(0, targetStock - current);
            int moq = Math.max(1, safeInt(part.getMinOrderQuantity()));
            int recommendedQuantity = rawQuantity == 0 ? 0 : roundUp(rawQuantity, moq);

            String level;
            int score;
            if (current <= safety || coverageDays <= leadTime) {
                level = "HIGH";
                score = 90;
            } else if (coverageDays <= leadTime + 15D || current <= reorderPoint) {
                level = "MEDIUM";
                score = 65;
            } else {
                level = "LOW";
                score = 25;
            }
            if (recommendedQuantity == 0 && "LOW".equals(level)) {
                score = 10;
            }

            Supplier supplier = suppliers.get(part.getSupplierId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("partId", part.getId());
            item.put("partCode", part.getPartCode());
            item.put("partName", part.getName());
            item.put("model", part.getModel());
            item.put("supplierId", part.getSupplierId());
            item.put("supplierName", supplier == null ? "未关联供应商" : supplier.getName());
            item.put("currentQuantity", current);
            item.put("safetyStock", safety);
            item.put("dailyDemand", decimal(dailyDemand));
            item.put("coverageDays", decimal(coverageDays));
            item.put("leadTimeDays", leadTime);
            item.put("reorderPoint", reorderPoint);
            item.put("recommendedQuantity", recommendedQuantity);
            item.put("estimatedAmount", part.getPurchasePrice() == null
                    ? BigDecimal.ZERO
                    : part.getPurchasePrice().multiply(BigDecimal.valueOf(recommendedQuantity)).setScale(2, RoundingMode.HALF_UP));
            item.put("riskLevel", level);
            item.put("riskScore", score);
            item.put("reason", replenishmentReason(level, current, safety, coverageDays, leadTime));
            items.add(item);
        }
        items.sort(Comparator
                .comparingInt((Map<String, Object> item) -> (Integer) item.get("riskScore")).reversed()
                .thenComparingDouble(item -> ((BigDecimal) item.get("coverageDays")).doubleValue()));

        long high = items.stream().filter(item -> "HIGH".equals(item.get("riskLevel"))).count();
        long medium = items.stream().filter(item -> "MEDIUM".equals(item.get("riskLevel"))).count();
        BigDecimal amount = items.stream()
                .map(item -> (BigDecimal) item.get("estimatedAmount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return responseSummary(items, Map.of(
                "highRiskCount", high,
                "mediumRiskCount", medium,
                "suggestedOrderCount", items.stream().filter(item -> (Integer) item.get("recommendedQuantity") > 0).count(),
                "estimatedReplenishmentAmount", amount.setScale(2, RoundingMode.HALF_UP),
                "model", "近180天采购节奏 + 安全库存 + 供应商交期"
        ));
    }

    public Map<String, Object> supplierRiskRadar() {
        List<Supplier> suppliers = supplierMapper.selectList(null);
        List<Part> parts = partMapper.selectList(null);
        Map<Long, Part> partMap = parts.stream().collect(Collectors.toMap(Part::getId, part -> part));
        Map<Long, PurchaseOrder> orderMap = purchaseOrderMapper.selectList(null).stream()
                .collect(Collectors.toMap(PurchaseOrder::getId, order -> order));
        Map<Long, List<OrderDetail>> detailsByOrder = orderDetailMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId));
        Map<Long, Logistics> logisticsByOrder = logisticsMapper.selectList(null).stream()
                .collect(Collectors.toMap(Logistics::getOrderId, logistics -> logistics, (left, right) -> right));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Supplier supplier : suppliers) {
            List<Part> suppliedParts = parts.stream()
                    .filter(part -> Objects.equals(part.getSupplierId(), supplier.getId()))
                    .toList();
            List<PurchaseOrder> relatedOrders = orderMap.values().stream()
                    .filter(order -> detailsByOrder.getOrDefault(order.getId(), List.of()).stream()
                            .map(OrderDetail::getPartId)
                            .map(partMap::get)
                            .filter(Objects::nonNull)
                            .anyMatch(part -> Objects.equals(part.getSupplierId(), supplier.getId())))
                    .toList();
            List<PurchaseOrder> completed = relatedOrders.stream()
                    .filter(order -> Objects.equals(order.getStatus(), 4) && order.getExpectedDeliveryDate() != null)
                    .toList();
            long onTime = completed.stream().filter(order -> {
                LocalDate actual = actualArrival(order, logisticsByOrder.get(order.getId()));
                return actual != null && !actual.isAfter(order.getExpectedDeliveryDate());
            }).count();
            double onTimeRate = completed.isEmpty() ? 1D : onTime / (double) completed.size();
            double averageLeadTime = suppliedParts.stream()
                    .map(Part::getLeadTimeDays).filter(Objects::nonNull)
                    .mapToInt(Integer::intValue).average().orElse(0D);
            int abnormalLogistics = (int) relatedOrders.stream()
                    .map(order -> logisticsByOrder.get(order.getId()))
                    .filter(Objects::nonNull)
                    .filter(logistics -> Objects.equals(logistics.getStatus(), 4))
                    .count();

            int creditRisk = creditRisk(supplier.getCreditRating());
            int deliveryRisk = (int) Math.round((1D - onTimeRate) * 100D);
            int leadTimeRisk = (int) Math.min(100D, Math.max(0D, (averageLeadTime - 5D) * 5D));
            int statusRisk = Objects.equals(supplier.getStatus(), 1) ? 0 : 100;
            int concentrationRisk = suppliedParts.size() <= 1 ? 70 : suppliedParts.size() == 2 ? 45 : 20;
            int riskScore = clamp((int) Math.round(creditRisk * .25 + deliveryRisk * .35
                    + leadTimeRisk * .15 + statusRisk * .15 + concentrationRisk * .10
                    + Math.min(20, abnormalLogistics * 10)));
            // 合作状态是准入硬约束：已终止或审核中的供应商不能被综合平均分“稀释”为低风险。
            if (!Objects.equals(supplier.getStatus(), 1)) {
                riskScore = Math.max(riskScore, 75);
            }
            String level = riskScore >= 60 ? "HIGH" : riskScore >= 35 ? "MEDIUM" : "LOW";

            Map<String, Object> dimensions = new LinkedHashMap<>();
            dimensions.put("credit", creditRisk);
            dimensions.put("delivery", deliveryRisk);
            dimensions.put("leadTime", leadTimeRisk);
            dimensions.put("cooperation", statusRisk);
            dimensions.put("concentration", concentrationRisk);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("supplierId", supplier.getId());
            item.put("supplierCode", supplier.getSupplierCode());
            item.put("supplierName", supplier.getName());
            item.put("creditRating", supplier.getCreditRating());
            item.put("status", supplier.getStatus());
            item.put("partCount", suppliedParts.size());
            item.put("orderCount", relatedOrders.size());
            item.put("onTimeRate", decimal(onTimeRate * 100D));
            item.put("averageLeadTimeDays", decimal(averageLeadTime));
            item.put("abnormalLogisticsCount", abnormalLogistics);
            item.put("riskScore", riskScore);
            item.put("riskLevel", level);
            item.put("dimensions", dimensions);
            item.put("recommendation", supplierRecommendation(level, onTimeRate, supplier.getStatus()));
            items.add(item);
        }
        items.sort(Comparator.comparingInt((Map<String, Object> item) -> (Integer) item.get("riskScore")).reversed());
        long high = items.stream().filter(item -> "HIGH".equals(item.get("riskLevel"))).count();
        long medium = items.stream().filter(item -> "MEDIUM".equals(item.get("riskLevel"))).count();
        BigDecimal avg = items.isEmpty() ? BigDecimal.ZERO : decimal(items.stream()
                .mapToInt(item -> (Integer) item.get("riskScore")).average().orElse(0D));
        return responseSummary(items, Map.of(
                "highRiskCount", high,
                "mediumRiskCount", medium,
                "averageRiskScore", avg,
                "supplierCount", items.size(),
                "model", "信用、履约、交期、合作状态、供应集中度五维评分"
        ));
    }

    public Map<String, Object> orderAnomalies() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<PurchaseOrder> orders = purchaseOrderMapper.selectList(null);
        Map<Long, Logistics> logisticsByOrder = logisticsMapper.selectList(null).stream()
                .collect(Collectors.toMap(Logistics::getOrderId, logistics -> logistics, (left, right) -> right));
        Map<Long, List<OrderDetail>> detailsByOrder = orderDetailMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId));
        Map<Long, Part> parts = partMapper.selectList(null).stream()
                .collect(Collectors.toMap(Part::getId, part -> part));

        List<Map<String, Object>> items = new ArrayList<>();
        for (PurchaseOrder order : orders) {
            List<OrderSignal> signals = new ArrayList<>();
            long ageDays = order.getOrderTime() == null ? 0 : ChronoUnit.DAYS.between(order.getOrderTime(), now);
            Logistics logistics = logisticsByOrder.get(order.getId());
            if (Objects.equals(order.getStatus(), 1) && ageDays >= 2) {
                signals.add(new OrderSignal("APPROVAL_STALLED", "审批停滞", "HIGH", "待审核已超过 " + ageDays + " 天"));
            }
            if (Objects.equals(order.getStatus(), 2) && ageDays >= 3 && (logistics == null || logistics.getShipTime() == null)) {
                signals.add(new OrderSignal("SHIPMENT_MISSING", "发运信息缺失", "MEDIUM", "订单已审核但尚未登记发运"));
            }
            if (Objects.equals(order.getStatus(), 3) && order.getExpectedDeliveryDate() != null
                    && order.getExpectedDeliveryDate().isBefore(today)) {
                long days = ChronoUnit.DAYS.between(order.getExpectedDeliveryDate(), today);
                signals.add(new OrderSignal("DELIVERY_OVERDUE", "交付逾期", "HIGH", "已超过预计交付日 " + days + " 天"));
            }
            if (logistics != null && Objects.equals(logistics.getStatus(), 4)) {
                signals.add(new OrderSignal("LOGISTICS_EXCEPTION", "物流异常", "HIGH", "物流节点已标记异常，请立即核查"));
            }
            if (Objects.equals(order.getStatus(), 4) && order.getExpectedDeliveryDate() != null) {
                LocalDate actual = actualArrival(order, logistics);
                if (actual != null && actual.isAfter(order.getExpectedDeliveryDate())) {
                    long days = ChronoUnit.DAYS.between(order.getExpectedDeliveryDate(), actual);
                    signals.add(new OrderSignal("DELIVERED_LATE", "历史逾期交付", "LOW", "实际交付晚于承诺日期 " + days + " 天"));
                }
            }
            if (signals.isEmpty()) {
                continue;
            }
            List<OrderDetail> details = detailsByOrder.getOrDefault(order.getId(), List.of());
            String partName = details.stream().findFirst().map(OrderDetail::getPartId).map(parts::get)
                    .filter(Objects::nonNull).map(Part::getName).orElse("未关联物料");
            OrderSignal primary = signals.stream().min(Comparator.comparingInt(signal -> severityRank(signal.severity()))).orElseThrow();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderId", order.getId());
            item.put("orderNumber", order.getOrderNumber());
            item.put("status", order.getStatus());
            item.put("totalAmount", order.getTotalAmount());
            item.put("partName", partName);
            item.put("expectedDeliveryDate", order.getExpectedDeliveryDate());
            item.put("severity", primary.severity());
            item.put("type", primary.type());
            item.put("title", primary.title());
            item.put("description", primary.description());
            item.put("signalCount", signals.size());
            item.put("signals", signals);
            item.put("suggestedAction", anomalyAction(primary.type()));
            items.add(item);
        }
        items.sort(Comparator
                .comparingInt((Map<String, Object> item) -> severityRank((String) item.get("severity")))
                .thenComparing(item -> String.valueOf(item.get("expectedDeliveryDate"))));
        return responseSummary(items, Map.of(
                "highCount", items.stream().filter(item -> "HIGH".equals(item.get("severity"))).count(),
                "mediumCount", items.stream().filter(item -> "MEDIUM".equals(item.get("severity"))).count(),
                "lowCount", items.stream().filter(item -> "LOW".equals(item.get("severity"))).count(),
                "activeOrderCount", orders.stream().filter(order -> order.getStatus() != null && order.getStatus() < 4).count(),
                "generatedAt", now
        ));
    }

    private static Map<String, Object> responseSummary(List<Map<String, Object>> items, Map<String, Object> summary) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("summary", summary);
        response.put("items", items);
        return response;
    }

    private static String replenishmentReason(String level, int current, int safety, double coverage, int leadTime) {
        if (current <= safety) {
            return "现有库存已触及安全库存，建议优先补货";
        }
        if (coverage <= leadTime) {
            return "预计可用天数短于供应商交期，存在补货窗口缺口";
        }
        if ("MEDIUM".equals(level)) {
            return "库存覆盖期接近采购交期与缓冲区间，建议提前锁定产能";
        }
        return "库存覆盖期充足，当前保持监控即可";
    }

    private static String supplierRecommendation(String level, double onTimeRate, Integer status) {
        if (!Objects.equals(status, 1)) {
            return "暂停新增订单，完成供应商准入或合作状态复核";
        }
        if (onTimeRate < .8D) {
            return "降低订单份额，并引入备选供应商进行双源保障";
        }
        if ("HIGH".equals(level)) {
            return "启动专项复核，重点核验交期、质量与供应连续性";
        }
        if ("MEDIUM".equals(level)) {
            return "维持合作并提高履约监测频率";
        }
        return "供应表现稳定，可维持当前合作策略";
    }

    private static String anomalyAction(String type) {
        return switch (type) {
            case "APPROVAL_STALLED" -> "提醒审批责任人并核对预算、数量与供应商信息";
            case "SHIPMENT_MISSING" -> "联系采购执行人与供应商补录发运信息";
            case "DELIVERY_OVERDUE" -> "联系供应商确认最新到货时间并评估替代货源";
            case "LOGISTICS_EXCEPTION" -> "联系承运方核验异常节点，必要时启动补发";
            default -> "纳入供应商履约复盘并更新风险评分";
        };
    }

    private static LocalDate actualArrival(PurchaseOrder order, Logistics logistics) {
        if (order.getActualDeliveryDate() != null) {
            return order.getActualDeliveryDate();
        }
        return logistics == null || logistics.getActualArrivalTime() == null
                ? null : logistics.getActualArrivalTime().toLocalDate();
    }

    private static int creditRisk(String rating) {
        if (rating == null) return 55;
        return switch (rating.toUpperCase()) {
            case "A" -> 8;
            case "B" -> 28;
            case "C" -> 58;
            case "D" -> 88;
            default -> 55;
        };
    }

    private static int severityRank(String severity) {
        return switch (severity) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static int roundUp(int value, int step) {
        return ((value + step - 1) / step) * step;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static BigDecimal decimal(double value) {
        if (!Double.isFinite(value)) return BigDecimal.ZERO;
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    public record OrderSignal(String type, String title, String severity, String description) { }
}
