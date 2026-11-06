package com.chipsupply.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chipsupply.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 库存 Mapper 接口
 */
@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    /** 单条 SQL 完成首次建账或累加入库，唯一键竞争也不会丢失增量。 */
    @Insert("""
            INSERT INTO inventory(part_id, current_quantity, safety_stock, last_inbound_time,
                                  warehouse_location, version, deleted, create_time, update_time)
            VALUES(#{partId}, #{quantity}, #{safetyStock}, NOW(),
                   COALESCE(NULLIF(#{warehouseLocation}, ''), 'ESD-A / 待分配库位'), 0, 0, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                current_quantity = current_quantity + VALUES(current_quantity),
                version = version + 1,
                last_inbound_time = NOW(),
                warehouse_location = CASE
                    WHEN #{warehouseLocation} IS NULL OR #{warehouseLocation} = '' THEN warehouse_location
                    ELSE #{warehouseLocation}
                END,
                update_time = NOW()
            """)
    int atomicInbound(@Param("partId") Long partId,
                      @Param("quantity") Integer quantity,
                      @Param("safetyStock") Integer safetyStock,
                      @Param("warehouseLocation") String warehouseLocation);

    /** 条件扣减避免并发超卖，库存永远不会因本操作降为负数。 */
    @Update("""
            UPDATE inventory
               SET current_quantity = current_quantity - #{quantity},
                   version = version + 1,
                   last_outbound_time = NOW(),
                   update_time = NOW()
             WHERE part_id = #{partId}
               AND deleted = 0
               AND current_quantity >= #{quantity}
            """)
    int atomicOutbound(@Param("partId") Long partId, @Param("quantity") Integer quantity);

}
