package com.atguigu.java.ai.langchain4j.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atguigu.java.ai.langchain4j.common.ToolAuditor;
import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.service.BookingResult;
import com.atguigu.java.ai.langchain4j.service.ChatSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentToolsTest {

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private ChatSessionService chatSessionService;

    @Test
    void bookAppointmentShouldUseSessionUserId() {
        AppointmentTools tools = new AppointmentTools(
                appointmentService, chatSessionService, new ToolAuditor());
        Appointment appointment = new Appointment();
        appointment.setUsername("张三");

        when(chatSessionService.requireUserId("memory-1")).thenReturn(99L);
        when(appointmentService.bookIfAbsent(any(Appointment.class)))
                .thenReturn(BookingResult.SUCCESS);

        String result = tools.bookAppointment("memory-1", appointment);

        assertThat(result).contains("预约成功");
        assertThat(appointment.getUserId()).isEqualTo(99L);
        verify(appointmentService).bookIfAbsent(appointment);
    }

    @Test
    void cancelAppointmentShouldOnlyCancelOwnRecord() {
        AppointmentTools tools = new AppointmentTools(
                appointmentService, chatSessionService, new ToolAuditor());
        Appointment appointment = new Appointment();

        when(chatSessionService.requireUserId("memory-1")).thenReturn(99L);
        when(appointmentService.cancelIfOwned(any(Appointment.class))).thenReturn(true);

        String result = tools.cancelAppointment("memory-1", appointment);

        assertThat(result).contains("取消预约成功");
        assertThat(appointment.getUserId()).isEqualTo(99L);
        verify(appointmentService).cancelIfOwned(appointment);
    }
}
