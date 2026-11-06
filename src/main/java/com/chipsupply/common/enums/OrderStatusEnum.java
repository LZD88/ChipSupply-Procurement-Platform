package com.chipsupply.common.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 订单状态枚举
 */
public enum OrderStatusEnum {

    /**
     * 待审核
     */
    PENDING_REVIEW(1, "待审核"),

    /**
     * 已审核
     */
    REVIEWED(2, "已审核"),

    /**
     * 采购中
     */
    PURCHASING(3, "采购中"),

    /**
     * 已入库
     */
    STORED(4, "已入库"),

    /**
     * 已取消
     */
    CANCELLED(5, "已取消");

    private final Integer code;
    private final String description;

    OrderStatusEnum(Integer code, String description) {
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
    public static OrderStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
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

    /**
     * 采购订单的唯一合法流转图：
     * 待审核→已审核/已取消，已审核→采购中/已取消，采购中→已入库。
     */
    public boolean canTransitionTo(OrderStatusEnum target) {
        if (target == null) return false;
        return switch (this) {
            case PENDING_REVIEW -> target == REVIEWED || target == CANCELLED;
            case REVIEWED -> target == PURCHASING || target == CANCELLED;
            case PURCHASING -> target == STORED;
            case STORED, CANCELLED -> false;
        };
    }

    public List<OrderStatusEnum> allowedTransitions() {
        return Collections.unmodifiableList(Arrays.stream(values())
                .filter(this::canTransitionTo)
                .toList());
    }
}
