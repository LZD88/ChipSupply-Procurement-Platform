package com.chipsupply.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chipsupply.entity.Logistics;
import org.apache.ibatis.annotations.Mapper;

/**
 * 物流信息 Mapper 接口
 */
@Mapper
public interface LogisticsMapper extends BaseMapper<Logistics> {

}