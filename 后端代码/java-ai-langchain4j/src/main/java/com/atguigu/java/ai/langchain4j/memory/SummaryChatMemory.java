package com.atguigu.java.ai.langchain4j.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class SummaryChatMemory implements ChatMemory {

    private final Object id;
    private final int maxTokens;
    private final int keepRecentTurns;
    private final boolean summaryEnabled;
    private final Tokenizer tokenizer;
    private final ChatMemoryStore messageStore;
    private final MongoConversationSummaryStore summaryStore;
    private final SummaryService summaryService;
    private final MemoryLockRegistry lockRegistry;

    public SummaryChatMemory(Object id,
                             int maxTokens,
                             int keepRecentTurns,
                             boolean summaryEnabled,
                             Tokenizer tokenizer,
                             ChatMemoryStore messageStore,
                             MongoConversationSummaryStore summaryStore,
                             SummaryService summaryService,
                             MemoryLockRegistry lockRegistry) {
        this.id = id;
        this.maxTokens = maxTokens;
        this.keepRecentTurns = keepRecentTurns;
        this.summaryEnabled = summaryEnabled;
        this.tokenizer = tokenizer;
        this.messageStore = messageStore;
        this.summaryStore = summaryStore;
        this.summaryService = summaryService;
        this.lockRegistry = lockRegistry;
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        ReentrantLock lock = lockRegistry.lockFor(memoryKey());
        lock.lock();
        try {
            List<ChatMessage> messages = new ArrayList<>(messageStore.getMessages(id));

            if (message instanceof SystemMessage systemMessage) {
                // 系统提示词固定放在第一位，避免每次动态日期变化导致提示词顺序错乱
                replaceSystemMessage(messages, systemMessage);
            } else {
                messages.add(message);
            }

            if (tokenCount(messages) > maxTokens) {
                compress(messages);
            }

            messageStore.updateMessages(id, messages);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ChatMessage> messages() {
        ReentrantLock lock = lockRegistry.lockFor(memoryKey());
        lock.lock();
        try {
            List<ChatMessage> messages = new ArrayList<>(messageStore.getMessages(id));
            String summary = summaryStore.findSummary(memoryKey());
            if (summary == null || summary.isBlank()) {
                return messages;
            }

            List<ChatMessage> result = new ArrayList<>(messages);
            int systemIndex = systemIndex(result);
            String summaryBlock = "以下是更早对话的摘要，请结合它回答：\n" + summary;
            if (systemIndex >= 0) {
                SystemMessage oldSystem = (SystemMessage) result.get(systemIndex);
                result.set(systemIndex, SystemMessage.from(oldSystem.text() + "\n\n" + summaryBlock));
            } else {
                result.add(0, SystemMessage.from(summaryBlock));
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        ReentrantLock lock = lockRegistry.lockFor(memoryKey());
        lock.lock();
        try {
            messageStore.deleteMessages(id);
            summaryStore.delete(memoryKey());
        } finally {
            lock.unlock();
        }
    }

    private void replaceSystemMessage(List<ChatMessage> messages, SystemMessage systemMessage) {
        int index = systemIndex(messages);
        if (index >= 0) {
            messages.set(index, systemMessage);
        } else {
            messages.add(0, systemMessage);
        }
    }

    private void compress(List<ChatMessage> messages) {
        // 按用户提问分组，避免把工具调用和工具结果拆开
        List<TurnRange> turns = splitTurns(messages);
        int keepTurns = Math.min(Math.max(keepRecentTurns, 1), turns.size());
        int removeCount = Math.max(0, turns.size() - keepTurns);
        List<TurnRange> turnsToRemove = turns.subList(0, removeCount);

        if (!removeTurns(messages, turnsToRemove)) {
            return;
        }

        // 摘要本身也可能很长，必要时继续按整轮删除
        while (tokenCount(messages) > maxTokens) {
            List<TurnRange> currentTurns = splitTurns(messages);
            if (currentTurns.size() <= 1) {
                log.warn("单轮对话超过记忆窗口，memoryId={}", id);
                break;
            }
            if (!removeTurns(messages, List.of(currentTurns.get(0)))) {
                break;
            }
        }
    }

    private boolean removeTurns(List<ChatMessage> messages, List<TurnRange> turns) {
        if (turns.isEmpty()) {
            return true;
        }

        List<ChatMessage> removed = new ArrayList<>();
        for (TurnRange turn : turns) {
            removed.addAll(messages.subList(turn.start(), turn.end()));
        }

        if (summaryEnabled) {
            String oldSummary = summaryStore.findSummary(memoryKey());
            String newSummary = summaryService.summarize(oldSummary, removed);
            if (newSummary == null || newSummary.isBlank()) {
                // 摘要失败时保留原消息，避免直接丢历史
                log.warn("对话摘要生成失败，暂不压缩记忆，memoryId={}", id);
                return false;
            }
            summaryStore.mergeSummary(memoryKey(), newSummary, removed.size());
        }

        for (int i = turns.size() - 1; i >= 0; i--) {
            TurnRange turn = turns.get(i);
            messages.subList(turn.start(), turn.end()).clear();
        }
        return true;
    }

    private List<TurnRange> splitTurns(List<ChatMessage> messages) {
        List<TurnRange> turns = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message instanceof SystemMessage) {
                continue;
            }
            if (message instanceof UserMessage) {
                if (start >= 0) {
                    turns.add(new TurnRange(start, i));
                }
                start = i;
            } else if (start < 0) {
                start = i;
            }
        }
        if (start >= 0) {
            turns.add(new TurnRange(start, messages.size()));
        }
        return turns;
    }

    private int tokenCount(List<ChatMessage> messages) {
        int count = 0;
        for (ChatMessage message : messages) {
            count += tokenizer.estimateTokenCountInMessage(message);
        }
        String summary = summaryStore.findSummary(memoryKey());
        if (summary != null && !summary.isBlank()) {
            count += tokenizer.estimateTokenCountInText(summary);
        }
        return count;
    }

    private String memoryKey() {
        return String.valueOf(id);
    }

    private int systemIndex(List<ChatMessage> messages) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof SystemMessage) {
                return i;
            }
        }
        return -1;
    }

    private record TurnRange(int start, int end) {
        int size() {
            return end - start;
        }
    }
}
