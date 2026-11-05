package com.chipsupply.common.enums;

/**
 * 客户类型枚举
 */
public enum CustomerTypeEnum {

    /**
     * 方案商
     */
    DEALER(1, "方案商"),

    /**
     * 电子制造商
     */
    RETAIL_STORE(2, "电子制造商"),

    /**
     * 研发机构
     */
    INDIVIDUAL(3, "研发机构");

    private final Integer code;
    private final String description;

    CustomerTypeEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取枚举
     */
    public static CustomerTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CustomerTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 检查code是否存在
     */
    public static boolean isValid(Integer code) {
        return getByCode(code) != null;
    }
}
