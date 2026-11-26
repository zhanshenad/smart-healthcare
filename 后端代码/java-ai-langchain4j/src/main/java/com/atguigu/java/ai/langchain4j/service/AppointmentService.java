package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AppointmentService extends IService<Appointment> {
    Appointment getOne(Appointment appointment);

    // 不存在才插入
    BookingResult bookIfAbsent(Appointment appointment);

    // 按预约 ID 和用户删除，并释放号源
    boolean cancelIfOwned(Appointment appointment);

    // 未配置排班的时段默认有号
    boolean hasSlot(String department, String date, String time);
}
