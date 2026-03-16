package com.aiconsultant.consultant.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.Reservation;
import com.aiconsultant.consultant.mapper.ReservationMapper;
import com.aiconsultant.consultant.service.ReservationService;
import org.springframework.stereotype.Service;

@Service
public class ReservationServiceImpl extends ServiceImpl<ReservationMapper,Reservation> implements ReservationService {
    //1.添加预约信息的方法
    @Override
    public void insert(Reservation reservation) {
        insert(reservation);
    }

    //2.查询预约信息的方法(根据手机号查询)
    @Override
    public Reservation findByPhone(String phone) {
        return findByPhone(phone);
    }
}
