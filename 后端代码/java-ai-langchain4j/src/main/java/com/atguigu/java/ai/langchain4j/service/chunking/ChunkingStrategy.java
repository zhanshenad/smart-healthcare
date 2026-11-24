package com.atguigu.java.ai.langchain4j.service.chunking;

import java.util.List;

public interface ChunkingStrategy {

    boolean supports(MarkdownSection section);

    List<ChunkDraft> chunk(MarkdownSection section);
}
