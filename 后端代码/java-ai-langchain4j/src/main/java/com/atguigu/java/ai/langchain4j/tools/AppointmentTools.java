package com.atguigu.java.ai.langchain4j.tools;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.atguigu.java.ai.langchain4j.security.CurrentUser;
import com.atguigu.java.ai.langchain4j.security.LoginUser;
import com.atguigu.java.ai.langchain4j.service.AppointmentService;
import com.atguigu.java.ai.langchain4j.service.ChatSessionService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import org.springframework.stereotype.Component;

@Component
public class AppointmentTools {

    private final AppointmentService appointmentService;
    private final ChatSessionService chatSessionService;

    public AppointmentTools(AppointmentService appointmentService, ChatSessionService chatSessionService) {
        this.appointmentService = appointmentService;
        this.chatSessionService = chatSessionService;
    }

    @Tool(name = "预约挂号", value = "根据参数，先执行工具方法queryDepartment查询是否可预约，并直接给用户回答是否可预约，并让用户确认所有预约信息，用户确认后再进行预约。如果用户没有提供具体的医生姓名，请从向量存储中找到一位医生。")
    public String bookAppointment(@ToolMemoryId String memoryId, Appointment appointment) {
        // 用户身份只从服务端会话中取，不信任模型传入的 userId
        appointment.setUserId(resolveUserId(memoryId));
        Appointment appointmentDB = appointmentService.getOne(appointment);

        if (appointmentDB == null) {
            appointment.setId(null);
            if (appointmentService.save(appointment)) {
                return "预约成功，并返回预约详情";
            } else {
                return "预约失败";
            }
        }

        return "您在相同的科室和时间已有预约";
    }

    @Tool(name = "取消预约挂号", value = "根据参数，查询预约是否存在，如果存在则删除预约记录并返回取消预约成功，否则返回取消预约失败")
    public String cancelAppointment(@ToolMemoryId String memoryId, Appointment appointment) {
        appointment.setUserId(resolveUserId(memoryId));
        Appointment appointmentDB = appointmentService.getOne(appointment);
        if (appointmentDB != null) {
            if (appointmentService.removeById(appointmentDB.getId())) {
                return "取消预约成功";
            } else {
                return "取消预约失败";
            }
        }

        return "您没有预约记录，请核对预约科室和时间";
    }

    private Long resolveUserId(String memoryId) {
        Long sessionUserId = chatSessionService.requireUserId(memoryId);
        // 工具线程可能拿不到 SecurityContext，拿得到时再校验一次请求用户
        LoginUser current = CurrentUser.getOrNull();
        if (current != null && !sessionUserId.equals(current.userId())) {
            throw ApiException.forbidden("无权操作该会话的预约");
        }
        return sessionUserId;
    }

    @Tool(name = "查询是否有号源", value = "根据科室名称，日期，时间和医生查询是否有号源，并返回给用户")
    public boolean queryDepartment(
            @P(value = "科室名称") String name,
            @P(value = "日期") String date,
            @P(value = "时间，可选值：上午、下午") String time,
            @P(value = "医生名称", required = false) String doctorName
    ) {
        System.out.println("查询是否有号源");
        System.out.println("科室名称：" + name);
        System.out.println("日期：" + date);
        System.out.println("时间：" + time);
        System.out.println("医生名称：" + doctorName);
        return true;
    }
}
