package com.hmdp.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReservationDTO {

    @NotNull(message = "店铺不能为空")
    private Long shopId;

    @NotNull(message = "预约时间不能为空")
    @Future(message = "预约时间必须晚于当前时间")
    private LocalDateTime reservationTime;

    @NotNull(message = "预约人数不能为空")
    @Min(value = 1, message = "预约人数至少 1 人")
    private Integer numPeople;

    private String remark;
}
