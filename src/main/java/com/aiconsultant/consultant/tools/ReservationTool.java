package com.aiconsultant.consultant.tools;

import com.aiconsultant.consultant.entity.Reservation;
import com.aiconsultant.consultant.service.ReservationService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ReservationTool implements BaseAgentTool{
    @Resource
    private ReservationService reservationService;

    //1.工具方法: 添加预约信息
    @Tool("预约志愿填报服务")
    public void  addReservation(
            @P("考生姓名") String name,
           @P("考生性别") String gender,
            @P("考生手机号") String phone,
            @P("预约沟通时间,格式为: yyyy-MM-dd'T'HH:mm") String communicationTime,
            @P("考生所在省份") String province,
            @P("考生预估分数") Integer estimatedScore
    ){
        //Reservation reservation = new Reservation(null,name,gender,phone, LocalDateTime.parse(communicationTime),province,estimatedScore);
        Reservation reservation = new Reservation()
                .setName(name)
                .setGender(gender)
                .setPhone(phone)
                .setCommunicationTime(LocalDateTime.parse(communicationTime))
                .setProvince(province)
                .setEstimatedScore(estimatedScore);
        reservationService.insert(reservation);
    }
    //2.工具方法: 查询预约信息
    @Tool("根据考生手机号查询预约单")
    public Reservation findReservation(@P("考生手机号") String phone){
        return reservationService.findByPhone(phone);
    }
}
