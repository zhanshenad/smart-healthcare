package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.assistant.XiaozhiAgent;
import com.atguigu.java.ai.langchain4j.dto.ChatForm;
import com.atguigu.java.ai.langchain4j.security.CurrentUser;
import com.atguigu.java.ai.langchain4j.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "智慧医疗")
@RestController
@RequestMapping("/medical")
public class XiaozhiController {

    private final XiaozhiAgent xiaozhiAgent;
    private final ChatSessionService chatSessionService;

    public XiaozhiController(XiaozhiAgent xiaozhiAgent, ChatSessionService chatSessionService) {
        this.xiaozhiAgent = xiaozhiAgent;
        this.chatSessionService = chatSessionService;
    }

    @Operation(summary = "对话")
    @PostMapping(value = "/chat", produces = "text/plain;charset=UTF-8")
    public Flux<String> chat(@Valid @RequestBody ChatForm chatForm) {
        Long userId = CurrentUser.id();
        // 会话必须属于当前用户，防止使用别人的 memoryId
        chatSessionService.ensureOwner(userId, chatForm.getMemoryId());
        return xiaozhiAgent.chat(chatForm.getMemoryId(), chatForm.getMessage());
    }
}
