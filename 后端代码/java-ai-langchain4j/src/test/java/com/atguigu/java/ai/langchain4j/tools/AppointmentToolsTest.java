package com.atguigu.java.ai.langchain4j.tools;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.service.ChatSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentToolsTest {

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private ChatSessionService chatSessionService;

    @Test
    void bookAppointmentShouldUseSessionUserId() {
        AppointmentTools tools = new AppointmentTools(appointmentService, chatSessionService);
        Appointment appointment = new Appointment();
        appointment.setUsername("张三");

        when(chatSessionService.requireUserId("memory-1")).thenReturn(99L);
        when(appointmentService.getOne(any(Appointment.class))).thenReturn(null);
        when(appointmentService.save(any())).thenReturn(true);

        String result = tools.bookAppointment("memory-1", appointment);

        assertThat(result).contains("预约成功");
        assertThat(appointment.getUserId()).isEqualTo(99L);
        verify(appointmentService).save(appointment);
    }

    @Test
    void cancelAppointmentShouldOnlyRemoveOwnRecord() {
        AppointmentTools tools = new AppointmentTools(appointmentService, chatSessionService);
        Appointment appointment = new Appointment();
        Appointment existing = new Appointment();
        existing.setId(10L);

        when(chatSessionService.requireUserId("memory-1")).thenReturn(99L);
        when(appointmentService.getOne(any(Appointment.class))).thenReturn(existing);
        when(appointmentService.removeById(10L)).thenReturn(true);

        String result = tools.cancelAppointment("memory-1", appointment);

        assertThat(result).contains("取消预约成功");
        assertThat(appointment.getUserId()).isEqualTo(99L);
        verify(appointmentService).removeById(10L);
    }
}
