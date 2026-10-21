package com.hmdp.controller;

import com.hmdp.dto.ReservationDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Reservation;
import com.hmdp.service.IReservationService;
import com.hmdp.utils.UserHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.hmdp.common.ErrorCode;

@RestController
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final IReservationService reservationService;

    @PostMapping
    public Result create(@Valid @RequestBody ReservationDTO dto) {
        return reservationService.createReservation(dto);
    }

    @GetMapping("/{id}")
    public Result getById(@PathVariable("id") Long id) {
        Reservation reservation = reservationService.getById(id);
        return reservation == null ? Result.fail(ErrorCode.NOT_FOUND, "预约不存在") : Result.ok(reservation);
    }

    @GetMapping("/my")
    public Result myList() {
        Long userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        List<Reservation> list = reservationService.lambdaQuery()
                .eq(userId != null, Reservation::getUserId, userId)
                .orderByDesc(Reservation::getCreateTime)
                .list();
        return Result.ok(list, (long) list.size());
    }
}
