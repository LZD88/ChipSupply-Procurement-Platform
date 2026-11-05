package com.chipsupply.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chipsupply.entity.Supplier;
import org.apache.ibatis.annotations.Mapper;

/**
 * 供应商 Mapper 接口
 */
@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {

}