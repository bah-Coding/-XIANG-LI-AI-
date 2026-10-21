package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户资料更新入参：昵称写回 tb_user，其余写回 tb_user_info。
 */
@Data
public class UserInfoUpdateDTO {

    private String nickName;
    private String city;
    private String introduce;
    private Boolean gender;
    private LocalDate birthday;
}
