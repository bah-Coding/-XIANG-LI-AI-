package com.hmdp.mapper;

import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author bzh
 * @since 2026-08-02
 */
public interface ShopTypeMapper extends BaseMapper<ShopType> {


    @Select("select * from tb_shop_type")
    List<ShopType> selectAll();
}
