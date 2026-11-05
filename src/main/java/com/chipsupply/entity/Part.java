package com.chipsupply.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 产品/芯片物料实体类
 */
@TableName("part")
public class Part extends BaseEntity {

    /**
     * 芯片物料ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 物料编码
     */
    @NotBlank(message = "物料编码不能为空")
    @TableField("part_code")
    private String partCode;

    /**
     * 物料名称
     */
    @NotBlank(message = "物料名称不能为空")
    private String name;

    /**
     * 型号
     */
    private String model;

    /**
     * 规格
     */
    private String specification;

    /**
     * 单位（个/套/件/台等）
     */
    private String unit;

    /**
     * 采购单价
     */
    @NotNull(message = "采购单价不能为空")
    @DecimalMin(value = "0.00", message = "采购单价不能小于0")
    @TableField("purchase_price")
    private BigDecimal purchasePrice;

    /**
     * 建议零售价
     */
    @TableField("suggested_retail_price")
    private BigDecimal suggestedRetailPrice;

    /**
     * 库存预警值
     */
    @TableField("stock_warning_value")
    private Integer stockWarningValue;

    /**
     * 供应商ID
     */
    @TableField("supplier_id")
    private Long supplierId;

    /**
     * 分类
     * 微控制器、存储芯片、电源管理、模拟芯片、接口芯片、传感芯片
     */
    private String category;

    /**
     * 物料描述
     */
    private String description;

    /** 制造商（教学虚构） */
    private String manufacturer;
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String value) { this.manufacturer = value; }

    /** 制造商料号 MPN */
    private String manufacturerPartNumber;
    public String getManufacturerPartNumber() { return manufacturerPartNumber; }
    public void setManufacturerPartNumber(String value) { this.manufacturerPartNumber = value; }

    /** 封装 */
    private String packageType;
    public String getPackageType() { return packageType; }
    public void setPackageType(String value) { this.packageType = value; }

    /** 温度等级 */
    private String temperatureGrade;
    public String getTemperatureGrade() { return temperatureGrade; }
    public void setTemperatureGrade(String value) { this.temperatureGrade = value; }

    /** 最小起订量（片） */
    @jakarta.validation.constraints.Min(1)
    private Integer minOrderQuantity;
    public Integer getMinOrderQuantity() { return minOrderQuantity; }
    public void setMinOrderQuantity(Integer value) { this.minOrderQuantity = value; }

    /** 标准交期（天） */
    @jakarta.validation.constraints.Min(1)
    private Integer leadTimeDays;
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer value) { this.leadTimeDays = value; }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPartCode() {
        return partCode;
    }

    public void setPartCode(String partCode) {
        this.partCode = partCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String specification) {
        this.specification = specification;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public BigDecimal getSuggestedRetailPrice() {
        return suggestedRetailPrice;
    }

    public void setSuggestedRetailPrice(BigDecimal suggestedRetailPrice) {
        this.suggestedRetailPrice = suggestedRetailPrice;
    }

    public Integer getStockWarningValue() {
        return stockWarningValue;
    }

    public void setStockWarningValue(Integer stockWarningValue) {
        this.stockWarningValue = stockWarningValue;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Part part = (Part) o;
        return id != null && id.equals(part.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}