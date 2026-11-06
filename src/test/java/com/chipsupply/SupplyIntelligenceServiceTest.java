package com.chipsupply;

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
import com.chipsupply.service.SupplyIntelligenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplyIntelligenceServiceTest {
    private InventoryMapper inventoryMapper;
    private PartMapper partMapper;
    private SupplierMapper supplierMapper;
    private PurchaseOrderMapper purchaseOrderMapper;
    private OrderDetailMapper orderDetailMapper;
    private LogisticsMapper logisticsMapper;
    private SupplyIntelligenceService service;

    @BeforeEach
    void setUp() {
        inventoryMapper = mock(InventoryMapper.class);
        partMapper = mock(PartMapper.class);
        supplierMapper = mock(SupplierMapper.class);
        purchaseOrderMapper = mock(PurchaseOrderMapper.class);
        orderDetailMapper = mock(OrderDetailMapper.class);
        logisticsMapper = mock(LogisticsMapper.class);
        service = new SupplyIntelligenceService(inventoryMapper, partMapper, supplierMapper,
                purchaseOrderMapper, orderDetailMapper, logisticsMapper);
    }

    @Test
    void replenishmentRoundsSuggestionToMoqAndExplainsHighRisk() {
        Inventory inventory = new Inventory();
        inventory.setPartId(1L);
        inventory.setCurrentQuantity(40);
        inventory.setSafetyStock(100);

        Part part = part(1L, 9L);
        part.setMinOrderQuantity(50);
        part.setLeadTimeDays(14);
        part.setPurchasePrice(new BigDecimal("2.00"));

        Supplier supplier = supplier(9L, 1, "A");
        when(inventoryMapper.selectList(null)).thenReturn(List.of(inventory));
        when(partMapper.selectList(null)).thenReturn(List.of(part));
        when(supplierMapper.selectList(null)).thenReturn(List.of(supplier));
        when(purchaseOrderMapper.selectList(null)).thenReturn(List.of());
        when(orderDetailMapper.selectList(null)).thenReturn(List.of());

        Map<String, Object> result = service.replenishmentForecast();
        Map<String, Object> item = firstItem(result);

        assertEquals("HIGH", item.get("riskLevel"));
        assertEquals(250, item.get("recommendedQuantity"));
        assertEquals(new BigDecimal("500.00"), item.get("estimatedAmount"));
        assertTrue(item.get("reason").toString().contains("安全库存"));
    }

    @Test
    void supplierRadarRaisesRiskWhenCooperationIsStopped() {
        Supplier supplier = supplier(9L, 2, "D");
        when(supplierMapper.selectList(null)).thenReturn(List.of(supplier));
        when(partMapper.selectList(null)).thenReturn(List.of(part(1L, 9L)));
        when(purchaseOrderMapper.selectList(null)).thenReturn(List.of());
        when(orderDetailMapper.selectList(null)).thenReturn(List.of());
        when(logisticsMapper.selectList(null)).thenReturn(List.of());

        Map<String, Object> item = firstItem(service.supplierRiskRadar());

        assertEquals("HIGH", item.get("riskLevel"));
        assertTrue((Integer) item.get("riskScore") >= 60);
        assertTrue(item.get("recommendation").toString().contains("暂停新增订单"));
    }

    @Test
    void orderCenterDetectsOverdueDeliveryAndReturnsAction() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(7L);
        order.setOrderNumber("PO-7");
        order.setStatus(3);
        order.setOrderTime(LocalDateTime.now().minusDays(20));
        order.setExpectedDeliveryDate(LocalDate.now().minusDays(4));
        order.setTotalAmount(new BigDecimal("8000"));

        OrderDetail detail = new OrderDetail();
        detail.setOrderId(7L);
        detail.setPartId(1L);
        detail.setQuantity(500);
        detail.setUnitPrice(new BigDecimal("16"));

        when(purchaseOrderMapper.selectList(null)).thenReturn(List.of(order));
        when(logisticsMapper.selectList(null)).thenReturn(List.of());
        when(orderDetailMapper.selectList(null)).thenReturn(List.of(detail));
        when(partMapper.selectList(null)).thenReturn(List.of(part(1L, 9L)));

        Map<String, Object> item = firstItem(service.orderAnomalies());

        assertEquals("DELIVERY_OVERDUE", item.get("type"));
        assertEquals("HIGH", item.get("severity"));
        assertTrue(item.get("suggestedAction").toString().contains("替代货源"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstItem(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("items")).get(0);
    }

    private static Part part(Long id, Long supplierId) {
        Part part = new Part();
        part.setId(id);
        part.setSupplierId(supplierId);
        part.setPartCode("CS-TEST-01");
        part.setName("测试芯片");
        part.setModel("TEST-01");
        part.setStockWarningValue(100);
        part.setMinOrderQuantity(10);
        part.setLeadTimeDays(7);
        part.setPurchasePrice(BigDecimal.ONE);
        return part;
    }

    private static Supplier supplier(Long id, Integer status, String credit) {
        Supplier supplier = new Supplier();
        supplier.setId(id);
        supplier.setSupplierCode("SUP-TEST");
        supplier.setName("测试供应商");
        supplier.setStatus(status);
        supplier.setCreditRating(credit);
        return supplier;
    }
}
