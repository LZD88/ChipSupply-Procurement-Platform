package com.chipsupply.common.enums;

/**
 * 物料分类枚举
 */
public enum PartCategoryEnum {

    /**
     * 微控制器
     */
    MCU("微控制器", "微控制器"),

    /**
     * 存储芯片
     */
    MEMORY("存储芯片", "存储芯片"),

    /**
     * 电源管理
     */
    POWER("电源管理", "电源管理"),

    /**
     * 模拟芯片
     */
    ANALOG("模拟芯片", "模拟芯片"),

    /**
     * 接口芯片
     */
    INTERFACE("接口芯片", "接口芯片"),

    /**
     * 传感芯片
     */
    SENSOR("传感芯片", "传感芯片");

    private final String code;
    private final String description;

    PartCategoryEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取枚举
     */
    public static PartCategoryEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (PartCategoryEnum category : values()) {
            if (category.getCode().equals(code)) {
                return category;
            }
        }
        return null;
    }

    /**
     * 检查code是否存在
     */
    public static boolean isValid(String code) {
        return getByCode(code) != null;
    }
}