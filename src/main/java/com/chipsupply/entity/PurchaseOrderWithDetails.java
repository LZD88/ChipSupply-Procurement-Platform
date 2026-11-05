package com.chipsupply.entity;

import java.util.List;

/**
 * 采购订单（包含订单明细及芯片物料详情）
 */
public class PurchaseOrderWithDetails extends PurchaseOrder {

    /**
     * 订单明细列表（包含芯片物料详情）
     */
    private List<OrderDetailWithPart> orderDetail;

    public List<OrderDetailWithPart> getOrderDetail() {
        return orderDetail;
    }

    public void setOrderDetail(List<OrderDetailWithPart> orderDetail) {
        this.orderDetail = orderDetail;
    }
}