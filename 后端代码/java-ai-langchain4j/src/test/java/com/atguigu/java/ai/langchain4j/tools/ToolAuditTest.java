package com.atguigu.java.ai.langchain4j.tools;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ToolAuditTest {

    @Autowired
    private AppointmentTools appointmentTools;

    @Test
    void shouldExposeAllTools() {
        List<ToolSpecification> specifications = ToolSpecifications.toolSpecificationsFrom(appointmentTools);

        assertThat(specifications).hasSize(3);
        assertThat(specifications).extracting(ToolSpecification::name)
                .contains("预约挂号", "取消预约挂号", "查询是否有号源");
    }

    @Test
    void shouldRunToolWithAudit() {
        assertThat(appointmentTools.hasAvailableSlot(
                "memory-1", "审计测试科", "2026-12-01", "上午", null)).isTrue();
    }
}
