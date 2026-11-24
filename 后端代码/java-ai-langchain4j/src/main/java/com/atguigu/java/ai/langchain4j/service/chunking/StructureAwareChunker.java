package com.atguigu.java.ai.langchain4j.service.chunking;

import com.atguigu.java.ai.langchain4j.config.RagProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

@Service
public class StructureAwareChunker {

    private final MarkdownSectionParser sectionParser;
    private final List<ChunkingStrategy> strategies;
    private final DocumentSplitter documentSplitter;
    private final RagProperties ragProperties;

    public StructureAwareChunker(MarkdownSectionParser sectionParser,
                                 List<ChunkingStrategy> strategies,
                                 DocumentSplitter documentSplitter,
                                 RagProperties ragProperties) {
        this.sectionParser = sectionParser;
        this.strategies = new ArrayList<>(strategies);
        AnnotationAwareOrderComparator.sort(this.strategies);
        this.documentSplitter = documentSplitter;
        this.ragProperties = ragProperties;
    }

    public List<TextSegment> chunk(String markdown, Metadata baseMetadata) {
        List<MarkdownSection> sections = sectionParser.parse(markdown);
        if (sections.isEmpty()) {
            return fallback(markdown, baseMetadata, "", "normal");
        }

        List<TextSegment> chunks = new ArrayList<>();
        for (MarkdownSection section : sections) {
            ChunkingStrategy strategy = strategies.stream()
                    .filter(candidate -> candidate.supports(section))
                    .findFirst()
                    .orElse(null);

            if (strategy == null) {
                chunks.addAll(fallback(section.text(), baseMetadata,
                        section.outlinePath(), "normal"));
                continue;
            }

            for (ChunkDraft draft : strategy.chunk(section)) {
                chunks.addAll(toSegments(draft, baseMetadata));
            }
        }

        if (chunks.isEmpty()) {
            return fallback(markdown, baseMetadata, "", "normal");
        }
        return chunks;
    }

    private List<TextSegment> toSegments(ChunkDraft draft, Metadata baseMetadata) {
        Metadata metadata = baseMetadata.copy()
                .put("strategy", draft.strategy())
                .put("role", draft.role())
                .put("outline_path", nullToEmpty(draft.outlinePath()));

        if (draft.parentKey() != null && !draft.parentKey().isBlank()) {
            metadata.put("parent_key", draft.parentKey());
        }

        if ("parent".equals(draft.role())) {
            return List.of(TextSegment.from(draft.text().strip(), metadata));
        }
        return splitIfNeeded(draft.text(), metadata);
    }

    private List<TextSegment> fallback(String text, Metadata baseMetadata,
                                       String outlinePath, String role) {
        Metadata metadata = baseMetadata.copy()
                .put("strategy", "recursive")
                .put("role", role)
                .put("outline_path", nullToEmpty(outlinePath));
        return splitIfNeeded(text, metadata);
    }

    private List<TextSegment> splitIfNeeded(String text, Metadata metadata) {
        String value = text == null ? "" : text.strip();
        if (value.isBlank()) {
            return List.of();
        }
        if (value.length() <= ragProperties.getChunkSize()) {
            return List.of(TextSegment.from(value, metadata));
        }
        return documentSplitter.split(Document.from(value, metadata)).stream()
                .filter(segment -> !segment.text().isBlank())
                .toList();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
