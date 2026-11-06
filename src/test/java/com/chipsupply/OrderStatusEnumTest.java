package com.chipsupply;

import com.chipsupply.common.enums.OrderStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusEnumTest {
    @Test
    void onlyAllowsDefinedForwardTransitions() {
        assertTrue(OrderStatusEnum.PENDING_REVIEW.canTransitionTo(OrderStatusEnum.REVIEWED));
        assertTrue(OrderStatusEnum.PENDING_REVIEW.canTransitionTo(OrderStatusEnum.CANCELLED));
        assertTrue(OrderStatusEnum.REVIEWED.canTransitionTo(OrderStatusEnum.PURCHASING));
        assertTrue(OrderStatusEnum.PURCHASING.canTransitionTo(OrderStatusEnum.STORED));

        assertFalse(OrderStatusEnum.PENDING_REVIEW.canTransitionTo(OrderStatusEnum.STORED));
        assertFalse(OrderStatusEnum.PURCHASING.canTransitionTo(OrderStatusEnum.REVIEWED));
        assertFalse(OrderStatusEnum.STORED.canTransitionTo(OrderStatusEnum.PURCHASING));
        assertFalse(OrderStatusEnum.CANCELLED.canTransitionTo(OrderStatusEnum.PENDING_REVIEW));
    }
}
