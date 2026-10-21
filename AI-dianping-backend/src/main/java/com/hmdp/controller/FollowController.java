package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author bzh
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    private final IFollowService followService;

    public FollowController(IFollowService followService) {
        this.followService = followService;
    }
    //关注
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(id, isFollow);
    }

    //取消关注
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
        return followService.isFollow(id);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        return followService.followCommons(id);
    }

    /** 某用户关注的人 */
    @GetMapping("/follows/{id}")
    public Result followList(@PathVariable("id") Long id) {
        return followService.followList(id);
    }

    /** 某用户的粉丝 */
    @GetMapping("/fans/{id}")
    public Result fansList(@PathVariable("id") Long id) {
        return followService.fansList(id);
    }

    /** 某用户的关注/粉丝数量 */
    @GetMapping("/count/{id}")
    public Result followCount(@PathVariable("id") Long id) {
        return followService.followCount(id);
    }
}
