package com.atguigu.java.ai.langchain4j.memory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

@Component
public class MemoryLockRegistry {

    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    // 单实例部署下，用进程内锁保证同一 memoryId 串行读写
    public ReentrantLock lockFor(String memoryId) {
        return locks.computeIfAbsent(memoryId, key -> new ReentrantLock());
    }
}
