package com.atguigu.java.ai.langchain4j.service.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PdfFileParser implements KnowledgeFileParser {

    private final ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();

    @Override
    public boolean supports(String extension) {
        return "pdf".equals(extension.toLowerCase(Locale.ROOT));
    }

    @Override
    public ParsedDocument parse(Path file) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file)) {
            Document document = pdfParser.parse(inputStream);
            return new ParsedDocument(document.text(), titleOf(file), "PDF", "LOW", "pdfbox");
        }
    }
}
