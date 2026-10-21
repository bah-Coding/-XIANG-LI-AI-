package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author bzh
 * @since 2026-08-02
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long id, Boolean isFollow);

    Result isFollow(Long id);

    Result followCommons(Long id);

    /** 某用户关注的人 */
    Result followList(Long userId);

    /** 某用户的粉丝 */
    Result fansList(Long userId);

    /** 某用户的关注/粉丝数量 */
    Result followCount(Long userId);
}
