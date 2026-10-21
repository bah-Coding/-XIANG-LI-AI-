package com.hmdp.mapper;

import com.hmdp.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author bzh
 * @since 2026-08-02
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {


    User selectByPhone(String phone);

    void save(User user);
}
