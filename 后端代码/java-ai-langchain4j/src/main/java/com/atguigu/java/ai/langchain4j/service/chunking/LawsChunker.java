package com.atguigu.java.ai.langchain4j.service.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class LawsChunker implements ChunkingStrategy {

    private static final String STRATEGY = "laws_structured";

    private static final Pattern ARTICLE = Pattern.compile(
            "^第[一二三四五六七八九十百千万零〇0-9]+条.*");

    private static final Pattern PROVISION = Pattern.compile(
            "^第[一二三四五六七八九十百千万零〇0-9]+款.*");

    private static final Pattern PAREN_CLAUSE = Pattern.compile(
            "^[（(][一二三四五六七八九十0-9]+[）)].*");

    private static final Pattern CLAUSE = Pattern.compile(
            "^(第[一二三四五六七八九十百千万零〇0-9]+款|[（(][一二三四五六七八九十0-9]+[）)]).*");

    @Override
    public boolean supports(MarkdownSection section) {
        for (String line : section.lines()) {
            String value = line.strip();
            if (ARTICLE.matcher(value).matches() || PROVISION.matcher(value).matches()) {
                return true;
            }
        }
        return section.outlinePath().contains("章") && hasParenthesizedClause(section);
    }

    @Override
    public List<ChunkDraft> chunk(MarkdownSection section) {
        boolean articleMode = section.lines().stream()
                .map(String::strip)
                .anyMatch(line -> ARTICLE.matcher(line).matches());

        List<ChunkDraft> drafts = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        String role = articleMode ? "law_clause" : "law_sub_clause";

        for (String line : section.lines()) {
            boolean startsNew = articleMode
                    ? ARTICLE.matcher(line.strip()).matches()
                    : CLAUSE.matcher(line.strip()).matches();
            if (startsNew) {
                flush(buffer, role, section, drafts);
            }
            buffer.add(line);
        }
        flush(buffer, role, section, drafts);
        return drafts;
    }

    private void flush(List<String> buffer, String role,
                       MarkdownSection section, List<ChunkDraft> drafts) {
        String text = String.join("\n", buffer).strip();
        buffer.clear();
        if (text.isBlank()) {
            return;
        }
        drafts.add(new ChunkDraft(text, STRATEGY, role, section.outlinePath(), null));
    }

    private boolean hasParenthesizedClause(MarkdownSection section) {
        return section.lines().stream()
                .map(String::strip)
                .anyMatch(line -> PAREN_CLAUSE.matcher(line).matches());
    }
}
