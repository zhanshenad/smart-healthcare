package com.atguigu.java.ai.langchain4j.service.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MarkdownSectionParser {

    private static final Pattern HEADING = Pattern.compile(
            "^ {0,3}(#{1,6})\\s+(.+?)(?:\\s+#+)?\\s*$");

    public List<MarkdownSection> parse(String markdown) {
        List<MarkdownSection> sections = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        int sectionIndex = 0;
        int level = 0;
        String heading = "";
        String outlinePath = "";
        boolean inCodeFence = false;

        for (String rawLine : markdown.split("\\R", -1)) {
            String line = rawLine.stripTrailing();
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inCodeFence = !inCodeFence;
                lines.add(line);
                continue;
            }

            Matcher matcher = inCodeFence ? null : HEADING.matcher(line);
            if (matcher != null && matcher.matches()) {
                sections.add(new MarkdownSection(
                        sectionIndex++, level, heading, outlinePath, List.copyOf(lines)));
                lines.clear();

                level = matcher.group(1).length();
                heading = matcher.group(2).strip();
                while (headingStack.size() >= level) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(heading);
                outlinePath = String.join(" > ", headingStack);
                continue;
            }
            lines.add(line);
        }

        sections.add(new MarkdownSection(
                sectionIndex, level, heading, outlinePath, List.copyOf(lines)));
        return sections.stream()
                .filter(section -> !section.isBlank())
                .toList();
    }
}
