package com.atguigu.java.ai.langchain4j.service.chunking;

import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
public class MarkdownOutlineChunker implements ChunkingStrategy {

    private static final String STRATEGY = "markdown_outline";

    @Override
    public boolean supports(MarkdownSection section) {
        return section.level() > 0;
    }

    @Override
    public List<ChunkDraft> chunk(MarkdownSection section) {
        String text = section.text();
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(new ChunkDraft(
                text, STRATEGY, "section", section.outlinePath(), null));
    }
}
