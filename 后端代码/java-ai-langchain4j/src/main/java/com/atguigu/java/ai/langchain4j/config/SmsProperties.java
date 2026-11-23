package com.atguigu.java.ai.langchain4j.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {

    private int codeTtlMinutes = 5;

    private int sendIntervalSeconds = 60;

    private int maxVerifyAttempts = 5;

    private int maxDailySends = 20;

    private boolean debug = true;
}
