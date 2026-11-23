package com.atguigu.java.ai.langchain4j.memory;

import com.atguigu.java.ai.langchain4j.service.SummaryService;
import com.atguigu.java.ai.langchain4j.store.MongoConversationSummaryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryChatMemoryTest {

    @Mock
    private ChatMemoryStore messageStore;

    @Mock
    private MongoConversationSummaryStore summaryStore;

    @Mock
    private SummaryService summaryService;

    @Mock
    private Tokenizer tokenizer;

    private SummaryChatMemory memory;

    @BeforeEach
    void setUp() {
        lenient().when(tokenizer.estimateTokenCountInMessage(any())).thenReturn(100);
        lenient().when(tokenizer.estimateTokenCountInText(anyString())).thenReturn(0);
    }

    @Test
    void systemMessageShouldBeReplacedInPlace() {
        memory = new SummaryChatMemory("1", 1000, 6, true, tokenizer,
                messageStore, summaryStore, summaryService, new MemoryLockRegistry());

        List<ChatMessage> stored = new ArrayList<>(List.of(
                SystemMessage.from("旧系统提示"),
                UserMessage.from("你好")));
        when(messageStore.getMessages("1")).thenReturn(stored);

        memory.add(SystemMessage.from("新系统提示"));

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageStore).updateMessages(eq("1"), captor.capture());
        List<ChatMessage> updated = captor.getValue();

        assertThat(updated).hasSize(2);
        assertThat(((SystemMessage) updated.get(0)).text()).isEqualTo("新系统提示");
        assertThat(((UserMessage) updated.get(1)).singleText()).isEqualTo("你好");
    }

    @Test
    void overflowShouldSummarizeAndRemoveOldMessages() {
        memory = new SummaryChatMemory("1", 350, 2, true, tokenizer,
                messageStore, summaryStore, summaryService, new MemoryLockRegistry());

        List<ChatMessage> stored = new ArrayList<>(List.of(
                SystemMessage.from("系统提示"),
                UserMessage.from("用户1"),
                AiMessage.from("助手1"),
                UserMessage.from("用户2"),
                AiMessage.from("助手2"),
                UserMessage.from("用户3")));
        when(messageStore.getMessages("1")).thenReturn(stored);
        when(summaryService.summarize(any(), anyList())).thenReturn("用户咨询过症状");

        memory.add(UserMessage.from("用户4"));

        verify(summaryService).summarize(eq(null), anyList());
        verify(summaryStore).mergeSummary("1", "用户咨询过症状", 4);

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageStore).updateMessages(eq("1"), captor.capture());
        List<ChatMessage> updated = captor.getValue();

        assertThat(updated).hasSize(3);
        assertThat(((SystemMessage) updated.get(0)).text()).isEqualTo("系统提示");
        assertThat(((UserMessage) updated.get(1)).singleText()).isEqualTo("用户3");
        assertThat(((UserMessage) updated.get(2)).singleText()).isEqualTo("用户4");
    }

    @Test
    void summaryFailureShouldKeepOldMessages() {
        memory = new SummaryChatMemory("1", 350, 2, true, tokenizer,
                messageStore, summaryStore, summaryService, new MemoryLockRegistry());

        List<ChatMessage> stored = new ArrayList<>(List.of(
                SystemMessage.from("系统提示"),
                UserMessage.from("用户1"),
                AiMessage.from("助手1"),
                UserMessage.from("用户2"),
                AiMessage.from("助手2"),
                UserMessage.from("用户3")));
        when(messageStore.getMessages("1")).thenReturn(stored);
        when(summaryService.summarize(any(), anyList())).thenReturn(null);

        memory.add(UserMessage.from("用户4"));

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageStore).updateMessages(eq("1"), captor.capture());
        assertThat(captor.getValue()).hasSize(7);
    }

    @Test
    void messagesShouldContainSummary() {
        memory = new SummaryChatMemory("1", 1000, 6, true, tokenizer,
                messageStore, summaryStore, summaryService, new MemoryLockRegistry());

        when(messageStore.getMessages("1")).thenReturn(new ArrayList<>(List.of(
                SystemMessage.from("系统提示"),
                UserMessage.from("最近一句话"))));
        when(summaryStore.findSummary("1")).thenReturn("用户之前咨询过头痛");

        List<ChatMessage> messages = memory.messages();

        assertThat(messages).hasSize(2);
        String systemText = ((SystemMessage) messages.get(0)).text();
        assertThat(systemText).contains("系统提示").contains("用户之前咨询过头痛");
    }
}
