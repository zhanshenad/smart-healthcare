package com.atguigu.java.ai.langchain4j.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DateTools {

    @Tool(name = "获取当前日期时间", value = "获取服务器当前日期和时间，需要计算预约日期时调用")
    public String currentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
