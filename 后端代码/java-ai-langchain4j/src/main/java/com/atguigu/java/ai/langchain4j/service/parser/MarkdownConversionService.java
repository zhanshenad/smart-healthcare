package com.atguigu.java.ai.langchain4j.service.parser;

import com.atguigu.java.ai.langchain4j.config.RagProperties;
import java.nio.file.Path;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MarkdownConversionService {

    private static final Logger log = LoggerFactory.getLogger(MarkdownConversionService.class);

    private final MineruClient mineruClient;
    private final MarkdownNormalizer normalizer;
    private final RagProperties ragProperties;

    public MarkdownConversionService(MineruClient mineruClient,
                                     MarkdownNormalizer normalizer,
                                     RagProperties ragProperties) {
        this.mineruClient = mineruClient;
        this.normalizer = normalizer;
        this.ragProperties = ragProperties;
    }

    public ParsedDocument convert(Path file, ParsedDocument parsed) {
        if (ragProperties.getMineru().isEnabled() && isPdf(file)) {
            try {
                String markdown = normalizer.normalize(mineruClient.parseMarkdown(file));
                return new ParsedDocument(markdown, parsed.title(),
                        parsed.docType(), "HIGH", "mineru");
            } catch (Exception e) {
                log.warn("MinerU 解析失败，回退到普通文本转换: {}", e.getMessage());
            }
        }

        if ("markdown".equals(parsed.parserName()) || looksLikeMarkdown(parsed.markdown())) {
            return parsed;
        }

        String markdown = normalizer.normalize(plainTextToMarkdown(parsed.title(), parsed.markdown()));
        String parserName = parsed.parserName() + "+text2md";
        return new ParsedDocument(markdown, parsed.title(),
                parsed.docType(), "LOW", parserName);
    }

    private boolean looksLikeMarkdown(String text) {
        return text.lines().anyMatch(line -> {
            String value = line.strip();
            return value.matches("^#{1,6}\\s+.+")
                    || (value.contains("|") && value.matches("^\\|?[\\s:|-]+\\|?$"))
                    || value.startsWith("```");
        });
    }

    private String plainTextToMarkdown(String title, String text) {
        String body = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        if (body.isBlank()) {
            return "";
        }

        String[] paragraphs = body.split("\\n\\s*\\n");
        StringBuilder markdown = new StringBuilder();
        if (title != null && !title.isBlank()) {
            markdown.append("# ").append(title.strip()).append("\n\n");
        }

        for (String paragraph : paragraphs) {
            String value = paragraph.strip();
            if (value.isBlank()) {
                continue;
            }
            markdown.append(value).append("\n\n");
        }
        return markdown.toString().strip();
    }

    private boolean isPdf(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }
}
