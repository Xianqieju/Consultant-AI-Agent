package com.aiconsultant.consultant.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aiconsultant.consultant.entity.Reservation;
import org.springframework.stereotype.Service;

@Service
public interface ReservationService extends IService<Reservation> {
    //1.添加预约信息的方法
    void insert(Reservation reservation);

    Reservation findByPhone(String phone);
}
