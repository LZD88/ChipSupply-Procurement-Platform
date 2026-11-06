package com.chipsupply.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chipsupply.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 采购订单 Mapper 接口
 */
@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {

    /**
     * 物理删除订单（绕过 @TableLogic 软删除，直接从数据库删除）
     */
    @Delete("DELETE FROM purchase_order WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /** 以旧状态为并发条件执行流转，防止两个审批请求相互覆盖。 */
    @Update("""
            <script>
            UPDATE purchase_order
               SET status = #{targetStatus}, update_time = NOW()
               <if test='targetStatus == 4'>, actual_delivery_date = COALESCE(actual_delivery_date, CURDATE())</if>
             WHERE id = #{id} AND status = #{currentStatus} AND deleted = 0
            </script>
            """)
    int transitionStatus(@Param("id") Long id,
                         @Param("currentStatus") Integer currentStatus,
                         @Param("targetStatus") Integer targetStatus);
}
