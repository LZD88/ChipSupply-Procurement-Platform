package com.chipsupply;

import com.chipsupply.common.exception.BusinessException;
import com.chipsupply.entity.OrderDetailWithPart;
import com.chipsupply.entity.PurchaseOrderWithDetails;
import com.chipsupply.security.AgentOrderPolicy;
import com.chipsupply.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentOrderPolicyTest {
    private final AgentOrderPolicy policy = new AgentOrderPolicy();
    private final AuthenticatedUser agent = new AuthenticatedUser(9L, "procurement_agent", "AGENT", "采购智能助手");

    @Test
    void agentCreateIsForcedToPendingAndAcceptsOnlyBusinessFields() {
        PurchaseOrderWithDetails order = validOrder();
        policy.enforceCreate(order, agent);
        assertThat(order.getStatus()).isEqualTo(1);
    }

    @Test
    void agentCannotForgeCreatorOrProductionStatus() {
        PurchaseOrderWithDetails forgedCreator = validOrder();
        forgedCreator.setCreatedBy(1L);
        assertThatThrownBy(() -> policy.enforceCreate(forgedCreator, agent))
                .isInstanceOf(BusinessException.class).hasMessageContaining("白名单字段");

        PurchaseOrderWithDetails approved = validOrder();
        approved.setStatus(2);
        assertThatThrownBy(() -> policy.enforceCreate(approved, agent))
                .isInstanceOf(BusinessException.class).hasMessageContaining("待审核");
    }

    @Test
    void agentCannotSetActualDeliveryOrDatabaseDetailFields() {
        PurchaseOrderWithDetails delivered = validOrder();
        delivered.setActualDeliveryDate(LocalDate.of(2026, 11, 6));
        assertThatThrownBy(() -> policy.enforceCreate(delivered, agent))
                .isInstanceOf(BusinessException.class);

        PurchaseOrderWithDetails forgedDetail = validOrder();
        forgedDetail.getOrderDetail().get(0).setSubtotal(new BigDecimal("1.00"));
        assertThatThrownBy(() -> policy.enforceCreate(forgedDetail, agent))
                .isInstanceOf(BusinessException.class).hasMessageContaining("订单明细");
    }

    @Test
    void agentUpdateRejectsStatusButAllowsExpectedDateAndRemark() {
        PurchaseOrderWithDetails allowed = new PurchaseOrderWithDetails();
        allowed.setExpectedDeliveryDate(LocalDate.of(2026, 11, 20));
        allowed.setRemark("人工审批后的交期修订");
        policy.enforceUpdate(allowed, agent);

        PurchaseOrderWithDetails forbidden = new PurchaseOrderWithDetails();
        forbidden.setStatus(2);
        assertThatThrownBy(() -> policy.enforceUpdate(forbidden, agent))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能修改状态");
    }

    private PurchaseOrderWithDetails validOrder() {
        OrderDetailWithPart detail = new OrderDetailWithPart();
        detail.setPartId(23L);
        detail.setQuantity(500);
        detail.setUnitPrice(new BigDecimal("15.80"));
        PurchaseOrderWithDetails order = new PurchaseOrderWithDetails();
        order.setOrderNumber("PO20261106001");
        order.setOrderDetail(List.of(detail));
        order.setRemark("decisionId=DEC-test; policyVersion=supplier-score-v1");
        return order;
    }
}
