package com.chipsupply;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chipsupply.dto.InventoryWithPart;
import com.chipsupply.entity.Inventory;
import com.chipsupply.entity.Part;
import com.chipsupply.mapper.InventoryMapper;
import com.chipsupply.mapper.PartMapper;
import com.chipsupply.service.impl.InventoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryFilteringTest {
    private InventoryMapper inventoryMapper;
    private PartMapper partMapper;
    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        inventoryMapper = mock(InventoryMapper.class);
        partMapper = mock(PartMapper.class);
        service = new InventoryServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", inventoryMapper);
        ReflectionTestUtils.setField(service, "partMapper", partMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersPartIdsBeforeInventoryPagination() {
        Part part = new Part();
        part.setId(23L);
        part.setName("六轴惯性传感器");
        part.setModel("CS-IMU-06");
        when(partMapper.selectList(any(Wrapper.class))).thenReturn(List.of(part));

        Inventory inventory = new Inventory();
        inventory.setId(23L);
        inventory.setPartId(23L);
        inventory.setCurrentQuantity(800);
        inventory.setSafetyStock(300);
        when(inventoryMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<Inventory> requested = invocation.getArgument(0);
            requested.setTotal(1);
            requested.setRecords(List.of(inventory));
            return requested;
        });

        Page<InventoryWithPart> result = service.pageInventoriesWithPart(
                new Page<>(1, 20), "六轴", null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .satisfies(row -> assertThat(row.getPartId()).isEqualTo(23L));
        InOrder calls = inOrder(partMapper, inventoryMapper);
        calls.verify(partMapper).selectList(any(Wrapper.class));
        calls.verify(inventoryMapper).selectPage(any(Page.class), any(Wrapper.class));
    }
}
