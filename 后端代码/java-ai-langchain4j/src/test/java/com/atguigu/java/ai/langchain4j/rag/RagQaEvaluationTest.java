package com.atguigu.java.ai.langchain4j.rag;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 用 QA 对账清单评测检索效果，需显式开启：
 * mvn -Dtest=RagQaEvaluationTest -Drag.eval=true -Drag.qa.file=<路径> test
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "rag.eval", matches = "true")
class RagQaEvaluationTest {

    private static final Pattern QUESTION = Pattern.compile("^Q(\\d+)\\s+(.+)$");

    @Autowired
    private ContentRetriever contentRetrieverXiaozhiMilvus;

    @Test
    void shouldEvaluateQaRetrieval() throws IOException {
        Path qaFile = Path.of(System.getProperty("rag.qa.file",
                "../rag文档/QA对账.txt"));
        assertThat(qaFile).exists();

        List<QaItem> items = parse(Files.readString(qaFile, StandardCharsets.UTF_8));
        assertThat(items).hasSizeGreaterThanOrEqualTo(20);

        int limit = Integer.parseInt(System.getProperty("rag.eval.limit", "0"));
        if (limit > 0) {
            items = items.subList(0, Math.min(limit, items.size()));
        }

        Map<String, List<Integer>> ranksByCategory = new LinkedHashMap<>();
        List<String> details = new ArrayList<>();
        int recallAt1 = 0;
        int recallAt5 = 0;
        double reciprocalRank = 0.0;
        int evidenceHits = 0;

        for (QaItem item : items) {
            List<Content> contents = contentRetrieverXiaozhiMilvus.retrieve(Query.from(item.question()));
            int rank = rankOf(item, contents);
            String category = categoryOf(item.source());

            ranksByCategory.computeIfAbsent(category, key -> new ArrayList<>()).add(rank);
            if (rank == 1) {
                recallAt1++;
            }
            if (rank > 0 && rank <= 5) {
                recallAt5++;
            }
            if (rank > 0) {
                reciprocalRank += 1.0 / rank;
            }
            if (evidenceHit(item, contents)) {
                evidenceHits++;
            }
            details.add(String.format(Locale.ROOT, "Q%02d\t%s\trang=%d\t%s",
                    item.number(), item.question(), rank, contents.isEmpty() ? "" : sourceOf(contents.get(0))));
        }

        int total = items.size();
        String tag = System.getProperty("rag.eval.tag", "structured");
        StringBuilder report = new StringBuilder();
        report.append("# RAG 检索评测报告（").append(tag).append("）\n\n")
                .append("- 问题数：").append(total).append('\n')
                .append("- Recall@1：").append(percent(recallAt1, total)).append('\n')
                .append("- Recall@5：").append(percent(recallAt5, total)).append('\n')
                .append("- MRR：").append(String.format(Locale.ROOT, "%.4f", reciprocalRank / total)).append('\n')
                .append("- 证据命中率（Top5 含期望章节/条款）：")
                .append(percent(evidenceHits, total)).append("\n\n")
                .append("## 分类指标\n\n")
                .append("| 类别 | 数量 | Recall@1 | Recall@5 | MRR |\n")
                .append("| --- | --- | --- | --- | --- |\n");

        for (Map.Entry<String, List<Integer>> entry : ranksByCategory.entrySet()) {
            List<Integer> ranks = entry.getValue();
            long r1 = ranks.stream().filter(rank -> rank == 1).count();
            long r5 = ranks.stream().filter(rank -> rank > 0 && rank <= 5).count();
            double mrr = ranks.stream().mapToDouble(rank -> rank > 0 ? 1.0 / rank : 0).average().orElse(0);
            report.append(String.format(Locale.ROOT, "| %s | %d | %s | %s | %.4f |\n",
                    entry.getKey(), ranks.size(), percent((int) r1, ranks.size()),
                    percent((int) r5, ranks.size()), mrr));
        }

        report.append("\n## 明细\n\n```\n").append(String.join("\n", details)).append("\n```\n");

        Path out = Path.of("target", "rag-eval-report-" + tag + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);
    }

    private List<QaItem> parse(String text) {
        List<QaItem> items = new ArrayList<>();
        Integer number = null;
        String question = null;
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.strip();
            Matcher matcher = QUESTION.matcher(line);
            if (matcher.matches()) {
                number = Integer.parseInt(matcher.group(1));
                question = matcher.group(2).strip();
                continue;
            }
            if (number == null || !line.startsWith("出处：")) {
                continue;
            }

            String reference = line.substring(3).strip();
            int index = reference.indexOf('（');
            String source = index < 0 ? reference : reference.substring(0, index).strip();
            String evidence = index < 0 ? "" : reference.substring(index + 1).strip();
            if (evidence.endsWith("）")) {
                evidence = evidence.substring(0, evidence.length() - 1).strip();
            }
            items.add(new QaItem(number, question, source, evidence));
            number = null;
            question = null;
        }
        return items;
    }

    private int rankOf(QaItem item, List<Content> contents) {
        String expectedSource = fileName(item.source());
        String expectedTitle = stripExtension(expectedSource);
        for (int i = 0; i < contents.size(); i++) {
            var metadata = contents.get(i).textSegment().metadata();
            String source = metadata.getString("source");
            String title = metadata.getString("title");
            if (expectedSource.equals(source) || expectedTitle.equals(title)) {
                return i + 1;
            }
        }
        return 0;
    }

    private boolean evidenceHit(QaItem item, List<Content> contents) {
        if (item.evidence().isBlank()) {
            return false;
        }
        String evidence = item.evidence().replaceAll("[（）()\\s]", "");
        return contents.stream().anyMatch(content -> {
            String text = content.textSegment().text().replaceAll("[（）()\\s]", "");
            String path = String.valueOf(content.textSegment().metadata().getString("outline_path"))
                    .replaceAll("[（）()\\s]", "");
            return text.contains(evidence) || path.contains(evidence);
        });
    }

    private String categoryOf(String source) {
        if (source.contains("markdown_outline")) {
            return "大纲类";
        }
        if (source.contains("markdown_table")) {
            return "表格类";
        }
        if (source.contains("sop_steps")) {
            return "SOP类";
        }
        if (source.contains("policy_manual_structured")) {
            return "制度类";
        }
        if (source.contains("laws_structured")) {
            return "法条类";
        }
        return "其他";
    }

    private String sourceOf(Content content) {
        return content.textSegment().metadata().getString("source");
    }

    private String fileName(String path) {
        String normalized = path.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String percent(int value, int total) {
        return String.format(Locale.ROOT, "%.1f%% (%d/%d)", value * 100.0 / total, value, total);
    }

    private record QaItem(int number, String question, String source, String evidence) {
    }
}
