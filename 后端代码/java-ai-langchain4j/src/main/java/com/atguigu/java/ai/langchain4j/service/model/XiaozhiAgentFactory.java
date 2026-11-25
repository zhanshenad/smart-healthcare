package com.atguigu.java.ai.langchain4j.service.model;

import com.atguigu.java.ai.langchain4j.assistant.XiaozhiAgent;
import com.atguigu.java.ai.langchain4j.config.ModelProperties;
import com.atguigu.java.ai.langchain4j.tools.AppointmentTools;
import com.atguigu.java.ai.langchain4j.tools.DateTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class XiaozhiAgentFactory {

    private final ChatMemoryProvider chatMemoryProvider;
    private final AppointmentTools appointmentTools;
    private final DateTools dateTools;
    private final RetrievalAugmentor retrievalAugmentor;

    public XiaozhiAgentFactory(
            @Qualifier("chatMemoryProviderXiaozhi") ChatMemoryProvider chatMemoryProvider,
            AppointmentTools appointmentTools,
            DateTools dateTools,
            RetrievalAugmentor retrievalAugmentor) {
        this.chatMemoryProvider = chatMemoryProvider;
        this.appointmentTools = appointmentTools;
        this.dateTools = dateTools;
        this.retrievalAugmentor = retrievalAugmentor;
    }

    public XiaozhiAgent create(ModelProperties.Entry entry,
                               ChatLanguageModel chatModel,
                               StreamingChatLanguageModel streamingModel) {
        AiServices<XiaozhiAgent> builder = AiServices.builder(XiaozhiAgent.class)
                .chatLanguageModel(chatModel)
                .streamingChatLanguageModel(streamingModel)
                .chatMemoryProvider(chatMemoryProvider)
                .retrievalAugmentor(retrievalAugmentor);
        // 模型配置关闭 tools 时不绑定预约和日期工具
        if (entry.isTools()) {
            builder.tools(appointmentTools, dateTools);
        }
        return builder.build();
    }
}
