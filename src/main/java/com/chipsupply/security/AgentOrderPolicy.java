package com.chipsupply.security;

import com.chipsupply.common.exception.BusinessException;
import com.chipsupply.entity.OrderDetailWithPart;
import com.chipsupply.entity.PurchaseOrderWithDetails;
import org.springframework.stereotype.Component;

/**
 * Agent 服务账号的订单字段白名单。
 *
 * <p>Spring Security 负责限制 Agent 能访问哪些接口；本策略继续限制其在允许接口中
 * 能写哪些字段，避免通过请求体伪造创建人、状态、实到日期或数据库维护字段。</p>
 */
@Component
public class AgentOrderPolicy {

    public void enforceCreate(PurchaseOrderWithDetails order, AuthenticatedUser user) {
        if (!isAgent(user)) return;
        if (order.getId() != null || order.getTotalAmount() != null || order.getOrderTime() != null
                || order.getActualDeliveryDate() != null || order.getCreatedBy() != null
                || order.getDeleted() != null || order.getCreateTime() != null || order.getUpdateTime() != null) {
            throw new BusinessException("Agent 创建订单只能提交业务白名单字段，状态、金额、创建人和系统维护字段由服务端生成");
        }
        if (order.getStatus() != null && order.getStatus() != 1) {
            throw new BusinessException("Agent 创建的订单只能进入待审核状态");
        }
        validateDetailWriteFields(order);
        order.setStatus(1);
    }

    public void enforceUpdate(PurchaseOrderWithDetails order, AuthenticatedUser user) {
        if (!isAgent(user)) return;
        if (order.getOrderNumber() != null || order.getTotalAmount() != null || order.getStatus() != null
                || order.getOrderTime() != null || order.getActualDeliveryDate() != null
                || order.getCreatedBy() != null || order.getDeleted() != null
                || order.getCreateTime() != null || order.getUpdateTime() != null) {
            throw new BusinessException("Agent 只能修改待审核订单的交期、备注和物料明细，不能修改状态或系统字段");
        }
        validateDetailWriteFields(order);
    }

    private void validateDetailWriteFields(PurchaseOrderWithDetails order) {
        if (order.getOrderDetail() == null) return;
        for (OrderDetailWithPart detail : order.getOrderDetail()) {
            if (detail == null) continue;
            if (detail.getId() != null || detail.getOrderId() != null || detail.getSubtotal() != null
                    || detail.getPartDetail() != null || detail.getDeleted() != null
                    || detail.getCreateTime() != null || detail.getUpdateTime() != null) {
                throw new BusinessException("Agent 订单明细只能提交物料、数量、单价和备注");
            }
        }
    }

    private boolean isAgent(AuthenticatedUser user) {
        return user != null && "AGENT".equalsIgnoreCase(user.role());
    }
}
