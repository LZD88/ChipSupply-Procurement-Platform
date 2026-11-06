package com.chipsupply.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chipsupply.dto.OrderDetailWithPartAndSupplier;
import com.chipsupply.dto.PartWithSupplier;
import com.chipsupply.entity.OrderDetail;
import com.chipsupply.entity.OrderDetailWithPart;
import com.chipsupply.entity.Part;
import com.chipsupply.entity.PurchaseOrder;
import com.chipsupply.entity.PurchaseOrderWithDetails;
import com.chipsupply.entity.Supplier;
import com.chipsupply.mapper.OrderDetailMapper;
import com.chipsupply.mapper.PartMapper;
import com.chipsupply.mapper.PurchaseOrderMapper;
import com.chipsupply.mapper.SupplierMapper;
import com.chipsupply.service.PurchaseOrderService;
import com.chipsupply.service.InventoryService;
import com.chipsupply.service.BusinessEventService;
import com.chipsupply.service.ReconciliationService;
import com.chipsupply.common.enums.OrderStatusEnum;
import com.chipsupply.common.enums.ResultCode;
import com.chipsupply.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采购订单服务实现类
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder> implements PurchaseOrderService {

    private final OrderDetailMapper orderDetailMapper;
    private final PartMapper partMapper;
    private final SupplierMapper supplierMapper;
    private final InventoryService inventoryService;
    private final BusinessEventService eventService;
    private final ReconciliationService reconciliationService;

    @Override
    public Page<PurchaseOrder> pageOrders(Page<PurchaseOrder> page, String orderNumber,
                                          Integer status, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(orderNumber)) {
            wrapper.like(PurchaseOrder::getOrderNumber, orderNumber);
        }

        if (status != null) {
            wrapper.eq(PurchaseOrder::getStatus, status);
        }

        if (startDate != null) {
            wrapper.ge(PurchaseOrder::getOrderTime, startDate.atStartOfDay());
        }

        if (endDate != null) {
            wrapper.le(PurchaseOrder::getOrderTime, endDate.plusDays(1).atStartOfDay());
        }

        wrapper.orderByDesc(PurchaseOrder::getOrderTime);

        return baseMapper.selectPage(page, wrapper);
    }

    private List<OrderDetailWithPart> checkedDetails(PurchaseOrder order, boolean required) {
        List<OrderDetailWithPart> details = order instanceof PurchaseOrderWithDetails withDetails
                ? withDetails.getOrderDetail() : null;
        if (details == null && !required) return null;
        if (details == null || details.isEmpty()) throw new com.chipsupply.common.exception.BusinessException("采购单至少需要一项芯片物料");
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (OrderDetailWithPart detail : details) {
            if (detail == null || detail.getPartId() == null || detail.getQuantity() == null
                    || detail.getQuantity() < 1 || detail.getUnitPrice() == null
                    || detail.getUnitPrice().signum() <= 0 || detail.getUnitPrice().scale() > 2) {
                throw new com.chipsupply.common.exception.BusinessException("物料、正整数数量和有效单价（最多两位小数）不能为空");
            }
            if (!ids.add(detail.getPartId())) throw new com.chipsupply.common.exception.BusinessException("同一物料不可在订单中重复，请合并数量");
            Part part = partMapper.selectById(detail.getPartId());
            if (part == null) throw new com.chipsupply.common.exception.BusinessException("芯片物料不存在");
            if (part.getMinOrderQuantity() != null && detail.getQuantity() < part.getMinOrderQuantity())
                throw new com.chipsupply.common.exception.BusinessException(part.getName() + "的最小起订量为" + part.getMinOrderQuantity() + "片");
        }
        return details;
    }

    private void saveDetails(Long orderId, List<OrderDetailWithPart> details) {
        for (OrderDetailWithPart item : details) {
            OrderDetail detail = OrderDetail.builder().orderId(orderId).partId(item.getPartId())
                    .quantity(item.getQuantity()).unitPrice(item.getUnitPrice()).remark(item.getRemark()).build();
            detail.setDeleted(0);
            detail.setCreateTime(LocalDateTime.now());
            detail.setUpdateTime(LocalDateTime.now());
            orderDetailMapper.insert(detail);
            item.setId(detail.getId());
            item.setOrderId(orderId);
            item.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
    }

    private BigDecimal sumDetails(List<OrderDetailWithPart> details) {
        return details.stream().map(d -> d.getUnitPrice().multiply(BigDecimal.valueOf(d.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void checkStatus(Integer status) {
        if (status != null && (status < 1 || status > 5))
            throw new com.chipsupply.common.exception.BusinessException("订单状态必须为1至5");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createOrder(PurchaseOrder order) {
        if (order == null || !StringUtils.hasText(order.getOrderNumber())) return false;
        checkStatus(order.getStatus());
        if (order.getStatus() != null && !OrderStatusEnum.PENDING_REVIEW.getCode().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "新订单只能从待审核状态开始");
        }
        List<OrderDetailWithPart> details = checkedDetails(order, true);
        if (baseMapper.selectCount(new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getOrderNumber, order.getOrderNumber())) > 0) return false;
        if (order.getOrderTime() == null) order.setOrderTime(LocalDateTime.now());
        if (order.getStatus() == null) order.setStatus(1);
        order.setTotalAmount(sumDetails(details));
        order.setDeleted(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        if (!save(order)) return false;
        saveDetails(order.getId(), details);
        eventService.record("PURCHASE_ORDER_CREATED", "PURCHASE_ORDER", order.getId(), order.getId(),
                "procurement-service", "采购订单已创建，等待审批",
                Map.of("orderNumber", order.getOrderNumber(), "amount", order.getTotalAmount(), "itemCount", details.size()));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(PurchaseOrder order) {
        if (order == null || order.getId() == null) return false;
        PurchaseOrder existing = getById(order.getId());
        if (existing == null) return false;
        if (!OrderStatusEnum.PENDING_REVIEW.getCode().equals(existing.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "订单进入审核后已锁定，不允许修改业务内容");
        }
        checkStatus(order.getStatus());
        if (order.getStatus() != null && !order.getStatus().equals(existing.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "订单状态只能通过状态流转接口修改");
        }
        List<OrderDetailWithPart> details = checkedDetails(order, false);
        if (order.getOrderNumber() != null && !StringUtils.hasText(order.getOrderNumber()))
            throw new com.chipsupply.common.exception.BusinessException("订单编号不能为空");
        order.setTotalAmount(details == null ? null : sumDetails(details));
        order.setCreateTime(null);
        order.setUpdateTime(LocalDateTime.now());
        // 直接更新主单，保留编号、原始下单时间和关联物流；明细只有明确传入才替换。
        if (!super.updateById(order)) return false;
        if (details != null) {
            orderDetailMapper.physicalDeleteByOrderId(order.getId());
            saveDetails(order.getId(), details);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOrderStatus(Long id, Integer status) {
        if (id == null || status == null) return false;
        checkStatus(status);
        PurchaseOrder existing = getById(id);
        if (existing == null) return false;
        OrderStatusEnum current = OrderStatusEnum.getByCode(existing.getStatus());
        OrderStatusEnum target = OrderStatusEnum.getByCode(status);
        if (current == null || !current.canTransitionTo(target)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR,
                    "不允许从“" + (current == null ? existing.getStatus() : current.getDescription())
                            + "”流转到“" + (target == null ? status : target.getDescription()) + "”");
        }
        if (baseMapper.transitionStatus(id, existing.getStatus(), status) != 1) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "订单状态已被其他请求修改，请刷新后重试");
        }
        if (target == OrderStatusEnum.STORED) {
            List<OrderDetail> details = orderDetailMapper.selectList(
                    new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, id));
            if (details.isEmpty()) throw new BusinessException("订单没有可入库明细");
            for (OrderDetail detail : details) {
                if (!inventoryService.inbound(detail.getPartId(), detail.getQuantity(), null)) {
                    throw new BusinessException("订单物料入库失败，事务已回滚");
                }
            }
            reconciliationService.ensureReceiptFromOrder(id);
        }
        eventService.record("ORDER_STATUS_CHANGED", "PURCHASE_ORDER", id, id,
                "workflow-service", "订单状态从“" + current.getDescription() + "”流转到“" + target.getDescription() + "”",
                Map.of("from", current.getCode(), "to", target.getCode()));
        return true;
    }

    @Override
    public List<OrderStatusEnum> getAllowedTransitions(Long id) {
        PurchaseOrder order = getById(id);
        if (order == null) return List.of();
        OrderStatusEnum current = OrderStatusEnum.getByCode(order.getStatus());
        return current == null ? List.of() : current.allowedTransitions();
    }

    @Override
    public OrderStatistics getOrderStatistics(LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<>();

        if (startDate != null) {
            wrapper.ge(PurchaseOrder::getOrderTime, startDate.atStartOfDay());
        }

        if (endDate != null) {
            wrapper.le(PurchaseOrder::getOrderTime, endDate.plusDays(1).atStartOfDay());
        }

        List<PurchaseOrder> orders = baseMapper.selectList(wrapper);

        long totalOrders = orders.size();
        long totalAmount = orders.stream()
                .map(order -> order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .longValue();
        long completedOrders = orders.stream()
                .filter(order -> order.getStatus() != null && order.getStatus() == 4)
                .count();
        long pendingOrders = orders.stream()
                .filter(order -> order.getStatus() != null && (order.getStatus() == 1 || order.getStatus() == 2 || order.getStatus() == 3))
                .count();

        return new OrderStatistics() {
            @Override
            public long getTotalOrders() {
                return totalOrders;
            }

            @Override
            public long getTotalAmount() {
                return totalAmount;
            }

            @Override
            public long getCompletedOrders() {
                return completedOrders;
            }

            @Override
            public long getPendingOrders() {
                return pendingOrders;
            }
        };
    }

    @Override
    public List<OrderDetailWithPartAndSupplier> searchOrderDetails(String partName, LocalDateTime startDate, LocalDateTime endDate) {
        // 1. 先根据时间范围查出所有订单ID
        LambdaQueryWrapper<PurchaseOrder> orderWrapper = new LambdaQueryWrapper<>();
        if (startDate != null) {
            orderWrapper.ge(PurchaseOrder::getOrderTime, startDate);
        }
        if (endDate != null) {
            orderWrapper.le(PurchaseOrder::getOrderTime, endDate);
        }
        List<PurchaseOrder> orders = baseMapper.selectList(orderWrapper);
        if (orders == null || orders.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> orderIds = orders.stream().map(PurchaseOrder::getId).collect(Collectors.toList());

        // 2. 查询所有订单的明细
        LambdaQueryWrapper<OrderDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.in(OrderDetail::getOrderId, orderIds);
        List<OrderDetail> allDetails = orderDetailMapper.selectList(detailWrapper);
        if (allDetails == null || allDetails.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 收集所有 partId
        List<Long> partIds = allDetails.stream()
                .map(OrderDetail::getPartId)
                .distinct()
                .collect(Collectors.toList());

        // 4. 一次性查出所有涉及的芯片物料
        LambdaQueryWrapper<Part> partWrapper = new LambdaQueryWrapper<>();
        partWrapper.in(Part::getId, partIds);
        if (StringUtils.hasText(partName)) {
            partWrapper.and(w -> w.like(Part::getName, partName).or().like(Part::getModel, partName)
                    .or().like(Part::getPartCode, partName).or().like(Part::getManufacturerPartNumber, partName));
        }
        List<Part> matchedParts = partMapper.selectList(partWrapper);
        if (matchedParts == null || matchedParts.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Part> partMap = matchedParts.stream()
                .collect(Collectors.toMap(Part::getId, p -> p));

        // 5. 只保留被 partName 过滤命中的明细
        List<OrderDetail> filteredDetails = allDetails.stream()
                .filter(d -> partMap.containsKey(d.getPartId()))
                .collect(Collectors.toList());

        // 6. 收集涉及的供应商ID
        List<Long> supplierIds = matchedParts.stream()
                .map(Part::getSupplierId)
                .filter(sid -> sid != null)
                .distinct()
                .collect(Collectors.toList());

        // 7. 一次性查出所有涉及的供应商
        Map<Long, Supplier> supplierMap = Map.of();
        if (!supplierIds.isEmpty()) {
            LambdaQueryWrapper<Supplier> supplierWrapper = new LambdaQueryWrapper<>();
            supplierWrapper.in(Supplier::getId, supplierIds);
            List<Supplier> suppliers = supplierMapper.selectList(supplierWrapper);
            supplierMap = suppliers.stream().collect(Collectors.toMap(Supplier::getId, s -> s));
        }

        // 8. 构建嵌套返回结果
        List<OrderDetailWithPartAndSupplier> result = new ArrayList<>();
        for (OrderDetail detail : filteredDetails) {
            Part part = partMap.get(detail.getPartId());

            OrderDetailWithPartAndSupplier detailDTO = new OrderDetailWithPartAndSupplier();
            detailDTO.setId(detail.getId());
            detailDTO.setOrderId(detail.getOrderId());
            detailDTO.setPartId(detail.getPartId());
            detailDTO.setQuantity(detail.getQuantity());
            detailDTO.setUnitPrice(detail.getUnitPrice());
            detailDTO.setSubtotal(detail.getSubtotal());
            detailDTO.setRemark(detail.getRemark());
            detailDTO.setCreateTime(detail.getCreateTime());
            detailDTO.setUpdateTime(detail.getUpdateTime());

            // 芯片物料信息
            PartWithSupplier partDTO = new PartWithSupplier();
            partDTO.setId(part.getId());
            partDTO.setPartCode(part.getPartCode());
            partDTO.setName(part.getName());
            partDTO.setModel(part.getModel());
            partDTO.setSpecification(part.getSpecification());
            partDTO.setManufacturer(part.getManufacturer());
            partDTO.setManufacturerPartNumber(part.getManufacturerPartNumber());
            partDTO.setPackageType(part.getPackageType());
            partDTO.setTemperatureGrade(part.getTemperatureGrade());
            partDTO.setMinOrderQuantity(part.getMinOrderQuantity());
            partDTO.setLeadTimeDays(part.getLeadTimeDays());
            partDTO.setUnit(part.getUnit());
            partDTO.setPurchasePrice(part.getPurchasePrice());
            partDTO.setSuggestedRetailPrice(part.getSuggestedRetailPrice());
            partDTO.setStockWarningValue(part.getStockWarningValue());
            partDTO.setSupplierId(part.getSupplierId());
            partDTO.setCategory(part.getCategory());
            partDTO.setDescription(part.getDescription());
            partDTO.setCreateTime(part.getCreateTime());
            partDTO.setUpdateTime(part.getUpdateTime());

            // 供应商信息
            if (part.getSupplierId() != null && supplierMap.containsKey(part.getSupplierId())) {
                Supplier supplier = supplierMap.get(part.getSupplierId());
                PartWithSupplier.SupplierInfo supplierInfo = new PartWithSupplier.SupplierInfo();
                supplierInfo.setId(supplier.getId());
                supplierInfo.setSupplierCode(supplier.getSupplierCode());
                supplierInfo.setName(supplier.getName());
                supplierInfo.setContactPerson(supplier.getContactPerson());
                supplierInfo.setPhone(supplier.getPhone());
                supplierInfo.setEmail(supplier.getEmail());
                supplierInfo.setAddress(supplier.getAddress());
                supplierInfo.setCreditRating(supplier.getCreditRating());
                supplierInfo.setStatus(supplier.getStatus());
                partDTO.setSupplier(supplierInfo);
            }

            detailDTO.setPartDetail(partDTO);
            result.add(detailDTO);
        }

        return result;
    }
}
