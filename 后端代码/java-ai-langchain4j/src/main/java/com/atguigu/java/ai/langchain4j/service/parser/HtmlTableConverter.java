package com.atguigu.java.ai.langchain4j.service.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 HTML 表格转换成 Markdown 管道表，供切片策略识别。
 */
public final class HtmlTableConverter {

    private static final Pattern TABLE = Pattern.compile(
            "<table\\b[^>]*>([\\s\\S]*?)</table>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW = Pattern.compile(
            "<tr\\b[^>]*>([\\s\\S]*?)</tr>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL = Pattern.compile(
            "<t[hd]\\b([^>]*)>([\\s\\S]*?)</t[hd]>", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLSPAN = Pattern.compile(
            "colspan\\s*=\\s*[\"']?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY = Pattern.compile("&#(x?[0-9a-fA-F]+);");

    private HtmlTableConverter() {
    }

    public static String convert(String markdown) {
        if (markdown == null || !markdown.toLowerCase().contains("<table")) {
            return markdown;
        }

        Matcher matcher = TABLE.matcher(markdown);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(markdown, last, matcher.start());
            String table = toMarkdown(matcher.group(1));
            if (table.isBlank()) {
                result.append(matcher.group());
            } else {
                result.append("\n\n").append(table).append("\n\n");
            }
            last = matcher.end();
        }
        result.append(markdown.substring(last));
        return result.toString();
    }

    private static String toMarkdown(String body) {
        List<List<String>> rows = new ArrayList<>();
        Matcher rowMatcher = ROW.matcher(body);
        while (rowMatcher.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = CELL.matcher(rowMatcher.group(1));
            while (cellMatcher.find()) {
                String value = cleanCell(cellMatcher.group(2));
                cells.add(value);
                int span = colspan(cellMatcher.group(1));
                for (int i = 1; i < span; i++) {
                    cells.add("");
                }
            }
            boolean hasText = cells.stream().anyMatch(value -> !value.isBlank());
            if (!cells.isEmpty() && hasText) {
                rows.add(cells);
            }
        }

        if (rows.isEmpty()) {
            return "";
        }
        int width = rows.stream().mapToInt(List::size).max().orElse(0);
        if (width == 0) {
            return "";
        }

        StringBuilder table = new StringBuilder();
        appendRow(table, rows.get(0), width);
        table.append('|');
        for (int i = 0; i < width; i++) {
            table.append(" --- |");
        }
        table.append('\n');
        for (int i = 1; i < rows.size(); i++) {
            appendRow(table, rows.get(i), width);
        }
        return table.toString().stripTrailing();
    }

    private static void appendRow(StringBuilder table, List<String> cells, int width) {
        table.append('|');
        for (int i = 0; i < width; i++) {
            String value = i < cells.size() ? cells.get(i) : "";
            table.append(' ').append(value).append(" |");
        }
        table.append('\n');
    }

    private static String cleanCell(String html) {
        String text = html.replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("(?i)</p>", " ")
                .replaceAll("(?i)</div>", " ");
        text = TAG.matcher(text).replaceAll("");
        text = unescape(text);
        text = text.replace("|", "\\|");
        return text.replaceAll("\\s+", " ").strip();
    }

    private static String unescape(String text) {
        String value = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        Matcher matcher = ENTITY.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String code = matcher.group(1);
            int point = code.startsWith("x") || code.startsWith("X")
                    ? Integer.parseInt(code.substring(1), 16)
                    : Integer.parseInt(code);
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    new String(Character.toChars(point))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static int colspan(String attributes) {
        Matcher matcher = COLSPAN.matcher(attributes);
        if (!matcher.find()) {
            return 1;
        }
        return Math.max(1, Math.min(20, Integer.parseInt(matcher.group(1))));
    }
}
