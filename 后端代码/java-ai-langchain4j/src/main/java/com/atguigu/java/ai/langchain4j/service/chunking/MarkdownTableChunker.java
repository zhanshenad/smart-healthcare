package com.atguigu.java.ai.langchain4j.service.chunking;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class MarkdownTableChunker implements ChunkingStrategy {

    private static final String STRATEGY = "markdown_table";

    @Override
    public boolean supports(MarkdownSection section) {
        List<String> lines = section.lines();
        for (int i = 0; i < lines.size() - 1; i++) {
            if (isTableRow(lines.get(i)) && isSeparator(lines.get(i + 1))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<ChunkDraft> chunk(MarkdownSection section) {
        List<String> lines = section.lines();
        List<ChunkDraft> drafts = new ArrayList<>();
        List<String> prose = new ArrayList<>();
        int index = 0;

        while (index < lines.size()) {
            if (index + 1 < lines.size()
                    && isTableRow(lines.get(index))
                    && isSeparator(lines.get(index + 1))) {
                flushProse(prose, section, drafts);
                String header = lines.get(index);
                index += 2;
                boolean hasData = false;

                while (index < lines.size() && isTableRow(lines.get(index))) {
                    drafts.add(new ChunkDraft(
                            header + "\n" + lines.get(index),
                            STRATEGY,
                            "table_row",
                            section.outlinePath(),
                            null));
                    hasData = true;
                    index++;
                }
                if (!hasData) {
                    drafts.add(new ChunkDraft(
                            header, STRATEGY, "table_header", section.outlinePath(), null));
                }
                continue;
            }

            prose.add(lines.get(index));
            index++;
        }

        flushProse(prose, section, drafts);
        return drafts;
    }

    private void flushProse(List<String> prose, MarkdownSection section, List<ChunkDraft> drafts) {
        String text = String.join("\n", prose).strip();
        prose.clear();
        if (text.isBlank()) {
            return;
        }
        drafts.add(new ChunkDraft(text, STRATEGY, "table_context", section.outlinePath(), null));
    }

    private boolean isTableRow(String line) {
        return line.contains("|") && !isSeparator(line);
    }

    private boolean isSeparator(String line) {
        String value = line.strip();
        return value.contains("-") && value.matches("^\\|?[\\s:|-]+\\|?$");
    }
}
