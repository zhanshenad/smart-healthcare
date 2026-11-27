package com.atguigu.java.ai.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AppointmentServiceTest {

    private static final AtomicLong USER_ID = new AtomicLong(System.nanoTime());

    @Autowired
    private AppointmentService appointmentService;

    private Long userId;

    @AfterEach
    void cleanup() {
        if (userId != null) {
            appointmentService.remove(new LambdaQueryWrapper<Appointment>()
                    .eq(Appointment::getUserId, userId));
        }
    }

    @Test
    void shouldSaveAndReadAppointment() {
        Appointment appointment = newAppointment();
        appointmentService.save(appointment);

        assertThat(appointment.getId()).isNotNull();

        Appointment query = new Appointment();
        query.setUserId(userId);
        query.setDepartment(appointment.getDepartment());
        query.setDate(appointment.getDate());
        query.setTime(appointment.getTime());

        assertThat(appointmentService.getOne(query)).isNotNull();
    }

    @Test
    void shouldRemoveAppointmentById() {
        Appointment appointment = newAppointment();
        appointmentService.save(appointment);

        assertThat(appointmentService.removeById(appointment.getId())).isTrue();
        assertThat(appointmentService.getById(appointment.getId())).isNull();
    }

    private Appointment newAppointment() {
        userId = USER_ID.incrementAndGet();
        Appointment appointment = new Appointment();
        appointment.setUserId(userId);
        appointment.setUsername("测试用户");
        appointment.setIdCard("123456789012345678");
        appointment.setDepartment("内科");
        appointment.setDate("2099-12-31");
        appointment.setTime("上午");
        appointment.setDoctorName("张医生");
        return appointment;
    }
}
