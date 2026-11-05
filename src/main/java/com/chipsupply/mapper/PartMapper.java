package com.chipsupply.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chipsupply.entity.Part;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品/芯片物料 Mapper 接口
 */
@Mapper
public interface PartMapper extends BaseMapper<Part> {

}