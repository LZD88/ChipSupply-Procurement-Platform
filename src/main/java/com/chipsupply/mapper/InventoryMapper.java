package com.chipsupply.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chipsupply.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存 Mapper 接口
 */
@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

}