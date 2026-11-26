package com.atguigu.java.ai.langchain4j.service.impl;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.entity.DepartmentSchedule;
import com.atguigu.java.ai.langchain4j.mapper.AppointmentMapper;
import com.atguigu.java.ai.langchain4j.mapper.DepartmentScheduleMapper;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.service.BookingResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

@Service
public class AppointmentServiceImpl extends ServiceImpl<AppointmentMapper, Appointment> implements AppointmentService {

    private final DepartmentScheduleMapper scheduleMapper;

    public AppointmentServiceImpl(DepartmentScheduleMapper scheduleMapper) {
        this.scheduleMapper = scheduleMapper;
    }

    /**
     * 查询订单是否存在
     * @param appointment
     * @return
     */
    @Override
    public Appointment getOne(Appointment appointment) {
        // 与唯一索引口径一致：用户 + 科室 + 日期 + 时段
        if (!StringUtils.hasText(appointment.getDepartment())
                || !StringUtils.hasText(appointment.getDate())
                || !StringUtils.hasText(appointment.getTime())) {
            return null;
        }
        LambdaQueryWrapper<Appointment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Appointment::getUserId, appointment.getUserId());
        queryWrapper.eq(Appointment::getDepartment, appointment.getDepartment());
        queryWrapper.eq(Appointment::getDate, appointment.getDate());
        queryWrapper.eq(Appointment::getTime, appointment.getTime());

        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingResult bookIfAbsent(Appointment appointment) {
        if (getOne(appointment) != null) {
            return BookingResult.DUPLICATE;
        }
        appointment.setId(null);
        try {
            save(appointment);
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底
            return BookingResult.DUPLICATE;
        }
        if (!reserveSlot(appointment)) {
            // 号源已满，回滚预约
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return BookingResult.NO_SLOT;
        }
        return BookingResult.SUCCESS;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelIfOwned(Appointment appointment) {
        // 有预约 ID 时直接按 ID + 用户定位，否则按时段定位
        Appointment appointmentDB = appointment.getId() != null
                ? getOne(new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getId, appointment.getId())
                        .eq(Appointment::getUserId, appointment.getUserId()))
                : getOne(appointment);
        if (appointmentDB == null) {
            return false;
        }
        boolean removed = remove(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getId, appointmentDB.getId())
                .eq(Appointment::getUserId, appointment.getUserId()));
        if (removed) {
            scheduleMapper.release(appointmentDB.getDepartment(),
                    appointmentDB.getDate(), appointmentDB.getTime());
        }
        return removed;
    }

    @Override
    public boolean hasSlot(String department, String date, String time) {
        DepartmentSchedule schedule = scheduleMapper.findBySlot(department, date, time);
        return schedule == null || schedule.getBooked() < schedule.getTotal();
    }

    private boolean reserveSlot(Appointment appointment) {
        DepartmentSchedule schedule = scheduleMapper.findBySlot(
                appointment.getDepartment(), appointment.getDate(), appointment.getTime());
        if (schedule == null) {
            return true;
        }
        return scheduleMapper.reserve(appointment.getDepartment(),
                appointment.getDate(), appointment.getTime()) > 0;
    }
}
