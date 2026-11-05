package com.chipsupply.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chipsupply.entity.Part;

import java.math.BigDecimal;
import java.util.List;

/**
 * 产品/芯片物料服务接口
 */
public interface PartService extends IService<Part> {

    /**
     * 分页查询芯片物料
     *
     * @param page     分页参数
     * @param name     物料名称（可选）
     * @param category 分类（可选）
     * @param supplierId 供应商ID（可选）
     * @return 分页结果
     */
    Page<Part> pageParts(Page<Part> page, String name, String category, Long supplierId);

    /**
     * 根据名称搜索芯片物料
     *
     * @param name 物料名称关键字
     * @return 物料列表
     */
    List<Part> searchByName(String name);

    /**
     * 更新采购单价
     *
     * @param id            物料ID
     * @param purchasePrice 新采购单价
     * @return 是否成功
     */
    boolean updatePurchasePrice(Long id, BigDecimal purchasePrice);

    /**
     * 根据供应商ID获取物料列表
     *
     * @param supplierId 供应商ID
     * @return 物料列表
     */
    List<Part> getPartsBySupplierId(Long supplierId);

    /**
     * 检查物料编码是否存在
     *
     * @param partCode 物料编码
     * @param excludeId 排除的ID（用于更新时）
     * @return 是否存在
     */
    boolean existsPartCode(String partCode, Long excludeId);
}