package com.atguigu.java.ai.langchain4j.rag;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MedicalContentInjector implements ContentInjector {

    @Override
    public ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {
        if (contents == null || contents.isEmpty() || !(chatMessage instanceof UserMessage userMessage)
                || !userMessage.hasSingleText()) {
            return chatMessage;
        }

        String context = contents.stream()
                .map(content -> content.textSegment().text())
                .collect(Collectors.joining("\n\n"));

        String prompt = """
                请仅根据下面的资料回答用户问题，资料中没有的内容不要编造。
                引用资料时请标注来源编号，例如 [来源1]。
                如果资料不足以回答，请说明“知识库中没有找到相关信息”。

                资料：
                %s

                用户问题：
                %s
                """.formatted(context, userMessage.singleText());

        return UserMessage.from(prompt);
    }
}
