package com.chipsupply.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chipsupply.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户/员工 Mapper 接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}