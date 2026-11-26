package com.atguigu.java.ai.langchain4j.tools;

import com.atguigu.java.ai.langchain4j.common.ToolAuditor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class DateTools {

    private final ToolAuditor auditor;

    public DateTools(ToolAuditor auditor) {
        this.auditor = auditor;
    }

    @Tool(name = "获取当前日期时间", value = "获取服务器当前日期和时间，需要计算预约日期时调用")
    public String currentDateTime(@ToolMemoryId String memoryId) {
        return auditor.audit("获取当前日期时间", memoryId, "",
                () -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
}
