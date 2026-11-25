package com.atguigu.java.ai.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SummaryService {

    private final OpenAiChatModel chatModel;

    public SummaryService(OpenAiChatModel chatModel) {
        this.chatModel = chatModel;
    }

    // 把较早的对话压缩成摘要
    public String summarize(String existingSummary, List<ChatMessage> messages) {
        String prompt = """
                你负责压缩医疗问诊对话历史。请保留以下信息：
                1. 用户的主要症状、持续时间和既往史；
                2. 已经确认的科室、医生、预约时间；
                3. 还需要继续追问或办理的事项。
                不要编造信息，不要加入诊断结论。用简洁中文分条输出，不超过300字。

                已有摘要：
                %s

                本次需要合并的对话：
                %s

                请输出合并后的新摘要：
                """.formatted(
                existingSummary == null ? "无" : existingSummary,
                formatMessages(messages));

        try {
            return chatModel.chat(prompt);
        } catch (Exception e) {
            log.warn("生成对话摘要失败，将直接丢弃较早消息: {}", e.getMessage());
            return null;
        }
    }

    private String formatMessages(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage) {
                builder.append("用户：").append(userMessage.singleText()).append('\n');
            } else if (message instanceof AiMessage aiMessage) {
                builder.append("助手：").append(aiMessage.text() == null ? "" : aiMessage.text()).append('\n');
            } else if (message instanceof ToolExecutionResultMessage toolMessage) {
                builder.append("工具结果：").append(toolMessage.text()).append('\n');
            } else if (message instanceof SystemMessage systemMessage) {
                builder.append("系统：").append(systemMessage.text()).append('\n');
            }
        }
        return builder.toString();
    }
}
