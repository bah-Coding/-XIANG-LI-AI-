package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author bzh
 * @since 2026-08-02
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryShopType();
}
