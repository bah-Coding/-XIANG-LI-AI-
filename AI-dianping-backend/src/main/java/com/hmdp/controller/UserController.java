package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.common.ErrorCode;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.dto.UserInfoUpdateDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;


/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author bzh
 * @since 2026-08-02
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    private final IUserService userService;

    private final IUserInfoService userInfoService;

    private final StringRedisTemplate stringRedisTemplate;

    public UserController(IUserService userService, IUserInfoService userInfoService,
                          StringRedisTemplate stringRedisTemplate) {
        this.userService = userService;
        this.userInfoService = userInfoService;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
       return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@Valid @RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        return userService.logout();
    }

    @GetMapping("/me")
    public Result me(){
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 更新当前登录用户的资料（昵称写 tb_user，其余写 tb_user_info）
     */
    @PutMapping("/info")
    public Result updateInfo(@RequestBody UserInfoUpdateDTO dto, HttpServletRequest request) {
        UserDTO current = UserHolder.getUser();
        if (current == null || current.getId() == null) {
            return Result.fail(ErrorCode.UNAUTHORIZED);
        }
        Long userId = current.getId();

        if (StrUtil.isNotBlank(dto.getNickName())) {
            userService.lambdaUpdate()
                    .eq(User::getId, userId)
                    .set(User::getNickName, dto.getNickName())
                    .update();
            // 同步 Redis 里的登录态，避免刷新后还是旧昵称
            String token = request.getHeader("authorization");
            if (StrUtil.isNotBlank(token)) {
                stringRedisTemplate.opsForHash()
                        .put(RedisConstants.LOGIN_USER_KEY + token, "nickName", dto.getNickName());
            }
        }

        UserInfo info = userInfoService.getById(userId);
        boolean create = info == null;
        if (create) {
            info = new UserInfo().setUserId(userId).setCreateTime(LocalDateTime.now());
        }
        if (dto.getCity() != null) {
            info.setCity(dto.getCity());
        }
        if (dto.getIntroduce() != null) {
            info.setIntroduce(dto.getIntroduce());
        }
        if (dto.getGender() != null) {
            info.setGender(dto.getGender());
        }
        if (dto.getBirthday() != null) {
            info.setBirthday(dto.getBirthday());
        }
        info.setUpdateTime(LocalDateTime.now());

        if (create) {
            userInfoService.save(info);
        } else {
            userInfoService.updateById(info);
        }
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
}
