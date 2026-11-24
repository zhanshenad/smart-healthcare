package com.atguigu.java.ai.langchain4j.service.parser;

import org.springframework.stereotype.Component;

@Component
public class MarkdownNormalizer {

    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String markdown = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\f', '\n');

        if (markdown.startsWith("﻿")) {
            markdown = markdown.substring(1);
        }

        // 标题符号后补空格，保证后续按标题切片时能识别
        markdown = markdown.replaceAll("(?m)^(#{1,6})([^#\\s])", "$1 $2");
        // 去掉 PDF 抽取产生的控制字符
        markdown = markdown.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        // 行尾空白和过多空行
        markdown = markdown.replaceAll("[ \\t]+\\n", "\n");
        markdown = markdown.replaceAll("\\n{3,}", "\n\n");

        return markdown.strip();
    }
}
