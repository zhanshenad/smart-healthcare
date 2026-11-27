package com.atguigu.java.ai.langchain4j.service.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class SopStepChunker implements ChunkingStrategy {

    private static final String STRATEGY = "sop_steps";

    private static final Pattern CHINESE_STEP = Pattern.compile(
            "^(第[一二三四五六七八九十百零〇0-9]+步|步骤[一二三四五六七八九十百零〇0-9]+).*");

    private static final Pattern ENGLISH_STEP = Pattern.compile(
            "^Step\\s*\\d+.*", Pattern.CASE_INSENSITIVE);

    private static final Pattern NUMBERED_STEP = Pattern.compile("^\\d+[.、]\\s*.*");

    @Override
    public boolean supports(MarkdownSection section) {
        boolean hasStep = section.lines().stream()
                .map(String::strip)
                .anyMatch(this::isStep);
        if (hasStep) {
            return true;
        }
        String heading = section.heading();
        if ((heading.contains("步骤") || heading.contains("流程") || heading.contains("操作"))
                && section.lines().stream()
                .map(String::strip)
                .anyMatch(line -> NUMBERED_STEP.matcher(line).matches())) {
            return true;
        }

        // 流程标题下用编号做小节，例如“门诊就诊流程 > 1.挂号”
        String path = section.outlinePath() == null ? "" : section.outlinePath();
        boolean flowPath = path.contains("步骤") || path.contains("流程") || path.contains("操作");
        return flowPath && (NUMBERED_STEP.matcher(heading).matches()
                || CHINESE_STEP.matcher(heading).matches());
    }

    @Override
    public List<ChunkDraft> chunk(MarkdownSection section) {
        boolean numberedMode = section.lines().stream()
                .map(String::strip)
                .noneMatch(this::isStep);

        List<ChunkDraft> drafts = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        for (String line : section.lines()) {
            String value = line.strip();
            boolean startsNew = numberedMode
                    ? NUMBERED_STEP.matcher(value).matches()
                    : isStep(value);
            if (startsNew) {
                flush(buffer, section, drafts);
            }
            buffer.add(line);
        }
        flush(buffer, section, drafts);
        return drafts;
    }

    private boolean isStep(String line) {
        return CHINESE_STEP.matcher(line).matches() || ENGLISH_STEP.matcher(line).matches();
    }

    private void flush(List<String> buffer, MarkdownSection section, List<ChunkDraft> drafts) {
        String text = String.join("\n", buffer).strip();
        buffer.clear();
        if (text.isBlank()) {
            return;
        }
        drafts.add(new ChunkDraft(text, STRATEGY, "step", section.outlinePath(), null));
    }
}
