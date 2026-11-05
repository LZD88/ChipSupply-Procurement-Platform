package com.chipsupply.entity;

/**
 * 订单明细（包含芯片物料详情）
 */
public class OrderDetailWithPart extends OrderDetail {

    /**
     * 芯片物料详情
     */
    private Part partDetail;

    public Part getPartDetail() {
        return partDetail;
    }

    public void setPartDetail(Part partDetail) {
        this.partDetail = partDetail;
    }
}