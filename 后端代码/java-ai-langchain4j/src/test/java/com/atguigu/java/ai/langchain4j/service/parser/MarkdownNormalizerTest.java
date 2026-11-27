package com.atguigu.java.ai.langchain4j.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownNormalizerTest {

    private final MarkdownNormalizer normalizer = new MarkdownNormalizer();

    @Test
    void shouldConvertHtmlTableToPipeTable() {
        String html = """
                # 价格表

                <table><tr><td>项目</td><td>价格</td></tr>
                <tr><td>挂号</td><td>10</td></tr>
                <tr><td colspan="2">合计</td></tr></table>
                """;

        String markdown = normalizer.normalize(html);

        assertThat(markdown).contains("| 项目 | 价格 |");
        assertThat(markdown).contains("| --- | --- |");
        assertThat(markdown).contains("| 挂号 | 10 |");
        assertThat(markdown).contains("| 合计 |  |");
    }

    @Test
    void shouldSplitArticlesJoinedInOneLine() {
        String text = "第一条　为了规范管理，制定本办法。第二条　患者应当实名就诊。第三条　医疗机构应当公示价格。";

        String markdown = normalizer.normalize(text);

        assertThat(markdown).contains("\n\n第二条");
        assertThat(markdown).contains("\n\n第三条");
    }

    @Test
    void shouldKeepArticleReferenceInSentence() {
        String text = "违反本条例第三十一条规定的，依法处理。";

        String markdown = normalizer.normalize(text);

        assertThat(markdown).isEqualTo(text);
    }
}
