package com.chipsupply.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chipsupply.dto.InventoryWithPart;
import com.chipsupply.entity.Inventory;
import com.chipsupply.entity.Part;
import com.chipsupply.mapper.InventoryMapper;
import com.chipsupply.mapper.PartMapper;
import com.chipsupply.service.InventoryService;
import com.chipsupply.service.BusinessEventService;
import com.chipsupply.infrastructure.RedisHotDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 库存服务实现类
 */
@Service
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory> implements InventoryService {

    @Autowired
    private PartMapper partMapper;

    @Autowired
    private BusinessEventService eventService;

    @Autowired
    private RedisHotDataService hotData;

    @Override
    public Page<Inventory> pageInventories(Page<Inventory> page, String partName, String warehouseLocation) {
        // 这里需要联表查询，简化处理：先查询所有，后续可优化
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(warehouseLocation)) {
            wrapper.like(Inventory::getWarehouseLocation, warehouseLocation);
        }

        wrapper.orderByDesc(Inventory::getCreateTime);

        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean inbound(Long partId, Integer quantity, String warehouseLocation) {
        if (partId == null || quantity == null || quantity <= 0) {
            return false;
        }

        // 检查物料是否存在
        Part part = partMapper.selectById(partId);
        if (part == null) {
            return false;
        }

        int safetyStock = part.getStockWarningValue() != null ? part.getStockWarningValue() : 100;
        boolean success = hotData.withInventoryLock(partId, () ->
                baseMapper.atomicInbound(partId, quantity, safetyStock,
                        StringUtils.hasText(warehouseLocation) ? warehouseLocation : null) > 0);
        if (success && eventService != null) {
            eventService.record("INVENTORY_INBOUND", "PART", partId, null,
                    "inventory-service", part.getName() + " 完成入库",
                    Map.of("partId", partId, "quantity", quantity));
        }
        if (success) evictInventoryCache(partId);
        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean outbound(Long partId, Integer quantity) {
        if (partId == null || quantity == null || quantity <= 0) {
            return false;
        }

        boolean success = hotData.withInventoryLock(partId, () -> baseMapper.atomicOutbound(partId, quantity) == 1);
        if (success && eventService != null) {
            eventService.record("INVENTORY_OUTBOUND", "PART", partId, null,
                    "inventory-service", "库存出库完成", Map.of("partId", partId, "quantity", quantity));
        }
        if (success) evictInventoryCache(partId);
        return success;
    }

    @Override
    public List<Inventory> getWarningList() {
        // 查询库存低于安全库存的记录
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("current_quantity <= safety_stock")
               .orderByAsc(Inventory::getCurrentQuantity);

        return baseMapper.selectList(wrapper);
    }

    @Override
    public boolean updateSafetyStock(Long partId, Integer safetyStock) {
        if (partId == null || safetyStock == null || safetyStock < 0) {
            return false;
        }

        LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Inventory::getPartId, partId);
        Inventory inventory = baseMapper.selectOne(queryWrapper);

        if (inventory == null) {
            return false;
        }

        LambdaUpdateWrapper<Inventory> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Inventory::getSafetyStock, safetyStock)
                     .eq(Inventory::getId, inventory.getId());

        return update(updateWrapper);
    }

    @Override
    public InventoryWithPart getInventoryWithPart(Long id) {
        Inventory inventory = baseMapper.selectById(id);
        if (inventory == null) {
            return null;
        }
        return convertToWithPart(inventory);
    }

    @Override
    public Page<InventoryWithPart> pageInventoriesWithPart(Page<Inventory> page, String partName, String warehouseLocation) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();

        Map<Long, Part> matchedPartMap = null;
        if (StringUtils.hasText(partName)) {
            LambdaQueryWrapper<Part> partFilter = new LambdaQueryWrapper<>();
            partFilter.and(w -> w.like(Part::getName, partName)
                    .or().like(Part::getModel, partName)
                    .or().like(Part::getPartCode, partName)
                    .or().like(Part::getManufacturerPartNumber, partName));
            matchedPartMap = partMapper.selectList(partFilter).stream()
                    .collect(Collectors.toMap(Part::getId, p -> p));
            if (matchedPartMap.isEmpty()) {
                return new Page<>(page.getCurrent(), page.getSize(), 0);
            }
            // 必须在数据库分页前按物料 ID 过滤，避免目标物料位于后续页时被误判为无库存。
            wrapper.in(Inventory::getPartId, matchedPartMap.keySet());
        }

        if (StringUtils.hasText(warehouseLocation)) {
            wrapper.like(Inventory::getWarehouseLocation, warehouseLocation);
        }

        wrapper.orderByDesc(Inventory::getCreateTime);

        Page<Inventory> result = baseMapper.selectPage(page, wrapper);

        if (result.getRecords() == null || result.getRecords().isEmpty()) {
            Page<InventoryWithPart> emptyPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            return emptyPage;
        }

        // 批量查询芯片物料
        List<Long> partIds = result.getRecords().stream()
                .map(Inventory::getPartId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Part> partMap;
        if (matchedPartMap != null) {
            Map<Long, Part> filteredPartMap = matchedPartMap;
            partMap = partIds.stream().filter(filteredPartMap::containsKey)
                    .collect(Collectors.toMap(id -> id, filteredPartMap::get));
        } else {
            LambdaQueryWrapper<Part> partWrapper = new LambdaQueryWrapper<>();
            partWrapper.in(Part::getId, partIds);
            partMap = partMapper.selectList(partWrapper).stream()
                    .collect(Collectors.toMap(Part::getId, p -> p));
        }

        // 过滤：只保留芯片物料名匹配的库存记录
        List<InventoryWithPart> dtoList = new ArrayList<>();
        for (Inventory inv : result.getRecords()) {
            Part part = partMap.get(inv.getPartId());
            if (part == null) {
                continue;
            }
            dtoList.add(convertToWithPart(inv, part));
        }

        Page<InventoryWithPart> pageResult = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageResult.setRecords(dtoList);
        return pageResult;
    }

    @Override
    public List<InventoryWithPart> getWarningListWithPart() {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("current_quantity <= safety_stock")
               .orderByAsc(Inventory::getCurrentQuantity);

        List<Inventory> list = baseMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> partIds = list.stream()
                .map(Inventory::getPartId)
                .collect(Collectors.toList());

        Map<Long, Part> partMap = partMapper.selectList(
                new LambdaQueryWrapper<Part>().in(Part::getId, partIds)
        ).stream().collect(Collectors.toMap(Part::getId, p -> p));

        List<InventoryWithPart> result = new ArrayList<>();
        for (Inventory inv : list) {
            Part part = partMap.get(inv.getPartId());
            if (part != null) {
                result.add(convertToWithPart(inv, part));
            }
        }
        return result;
    }

    /**
     * 将单个 Inventory 转换为 InventoryWithPart（需要单独查询 Part）
     */
    private InventoryWithPart convertToWithPart(Inventory inv) {
        Part part = partMapper.selectById(inv.getPartId());
        return convertToWithPart(inv, part);
    }

    /**
     * 将 Inventory 和 Part 合并为 InventoryWithPart
     */
    private InventoryWithPart convertToWithPart(Inventory inv, Part part) {
        InventoryWithPart dto = new InventoryWithPart();
        dto.setId(inv.getId());
        dto.setPartId(inv.getPartId());
        dto.setCurrentQuantity(inv.getCurrentQuantity());
        dto.setSafetyStock(inv.getSafetyStock());
        dto.setLastInboundTime(inv.getLastInboundTime());
        dto.setLastOutboundTime(inv.getLastOutboundTime());
        dto.setWarehouseLocation(inv.getWarehouseLocation());
        dto.setVersion(inv.getVersion());
        dto.setDeleted(inv.getDeleted());
        dto.setCreateTime(inv.getCreateTime());
        dto.setUpdateTime(inv.getUpdateTime());

        if (part != null) {
            InventoryWithPart.PartInfo partInfo = new InventoryWithPart.PartInfo();
            partInfo.setId(part.getId());
            partInfo.setPartCode(part.getPartCode());
            partInfo.setName(part.getName());
            partInfo.setModel(part.getModel());
            partInfo.setSpecification(part.getSpecification());
            partInfo.setManufacturer(part.getManufacturer());
            partInfo.setManufacturerPartNumber(part.getManufacturerPartNumber());
            partInfo.setPackageType(part.getPackageType());
            partInfo.setTemperatureGrade(part.getTemperatureGrade());
            partInfo.setMinOrderQuantity(part.getMinOrderQuantity());
            partInfo.setLeadTimeDays(part.getLeadTimeDays());
            partInfo.setUnit(part.getUnit());
            partInfo.setPurchasePrice(part.getPurchasePrice());
            partInfo.setSuggestedRetailPrice(part.getSuggestedRetailPrice());
            partInfo.setStockWarningValue(part.getStockWarningValue());
            partInfo.setSupplierId(part.getSupplierId());
            partInfo.setCategory(part.getCategory());
            partInfo.setDescription(part.getDescription());
            partInfo.setDeleted(part.getDeleted());
            partInfo.setCreateTime(part.getCreateTime());
            partInfo.setUpdateTime(part.getUpdateTime());
            dto.setPartDetail(partInfo);
        }

        return dto;
    }

    private void evictInventoryCache(Long partId) {
        Inventory inventory = baseMapper.selectOne(new LambdaQueryWrapper<Inventory>().eq(Inventory::getPartId, partId));
        if (inventory != null) hotData.evict("inventory", inventory.getId());
    }
}
