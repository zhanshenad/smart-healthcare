package com.atguigu.java.ai.langchain4j.service.chunking;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class PolicyManualChunker implements ChunkingStrategy {

    private static final String STRATEGY = "policy_manual_structured";

    private static final List<String> POLICY_KEYWORDS = List.of(
            "制度", "手册", "规范", "管理办法", "实施细则", "流程");

    @Override
    public boolean supports(MarkdownSection section) {
        String heading = section.heading();
        return POLICY_KEYWORDS.stream().anyMatch(heading::contains);
    }

    @Override
    public List<ChunkDraft> chunk(MarkdownSection section) {
        String text = section.text();
        if (text.isBlank()) {
            return List.of();
        }

        String parentKey = "policy-" + section.index();
        List<ChunkDraft> drafts = new ArrayList<>();
        drafts.add(new ChunkDraft(
                text, STRATEGY, "parent", section.outlinePath(), parentKey));

        for (String paragraph : text.split("\\R\\s*\\R")) {
            String child = paragraph.strip();
            if (child.isBlank()) {
                continue;
            }
            drafts.add(new ChunkDraft(
                    child, STRATEGY, "child", section.outlinePath(), parentKey));
        }
        return drafts;
    }
}
