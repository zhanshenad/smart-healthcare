package com.atguigu.java.ai.langchain4j.service.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import com.atguigu.java.ai.langchain4j.config.RagProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StructureAwareChunkerTest {

    private StructureAwareChunker chunker;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(1000);
        properties.setChunkOverlap(100);

        chunker = new StructureAwareChunker(
                new MarkdownSectionParser(),
                List.of(
                        new MarkdownTableChunker(),
                        new LawsChunker(),
                        new SopStepChunker(),
                        new PolicyManualChunker(),
                        new MarkdownOutlineChunker()),
                DocumentSplitters.recursive(properties.getChunkSize(), properties.getChunkOverlap()),
                properties);
    }

    @Test
    void shouldChunkLawsByArticle() {
        List<TextSegment> chunks = chunk("""
                # 医院管理制度
                第一条 为规范门诊秩序，制定本制度。
                第二条 患者挂号后按顺序就诊。
                """);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.metadata().getString("strategy")).isEqualTo("laws_structured");
            assertThat(chunk.metadata().getString("role")).isEqualTo("law_clause");
            assertThat(chunk.metadata().getString("outline_path")).isEqualTo("医院管理制度");
        });
        assertThat(chunks).extracting(TextSegment::text)
                .allSatisfy(text -> assertThat(text).startsWith("第"));
    }

    @Test
    void shouldChunkLawByProvision() {
        List<TextSegment> chunks = chunk("""
                # 条款
                第一款 门诊实行实名制。
                第二款 急诊实行首诊负责制。
                """);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.metadata().getString("strategy")).isEqualTo("laws_structured");
            assertThat(chunk.metadata().getString("role")).isEqualTo("law_sub_clause");
        });
    }

    @Test
    void shouldChunkTableByRowAndKeepHeader() {
        List<TextSegment> chunks = chunk("""
                # 门诊价格表
                | 项目 | 价格 |
                | --- | --- |
                | 挂号 | 10 |
                | 门诊 | 20 |
                """);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.metadata().getString("strategy")).isEqualTo("markdown_table");
            assertThat(chunk.metadata().getString("role")).isEqualTo("table_row");
            assertThat(chunk.text()).contains("| 项目 | 价格 |");
        });
        assertThat(chunks).extracting(TextSegment::text)
                .anySatisfy(text -> assertThat(text).contains("| 挂号 | 10 |"))
                .anySatisfy(text -> assertThat(text).contains("| 门诊 | 20 |"));
    }

    @Test
    void shouldKeepTableHeaderWhenNoRows() {
        List<TextSegment> chunks = chunk("""
                # 价格表
                | 项目 | 价格 |
                | --- | --- |
                """);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("| 项目 | 价格 |");
        assertThat(chunks.get(0).metadata().getString("role")).isEqualTo("table_header");
    }

    @Test
    void shouldChunkOutlineAndKeepPath() {
        List<TextSegment> chunks = chunk("""
                # 就医指南
                ## 挂号
                请在门诊一楼挂号。
                ## 停车
                地下停车场入口在东侧。
                """);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(chunk -> chunk.metadata().getString("outline_path"))
                .containsExactly("就医指南 > 挂号", "就医指南 > 停车");
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.metadata().getString("strategy")).isEqualTo("markdown_outline"));
    }

    @Test
    void shouldKeepTrailingHashAndIndentedHeading() {
        List<TextSegment> chunks = chunk("""
                # C# 医院
                  ## 挂号
                请在一楼挂号。
                """);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().getString("outline_path"))
                .isEqualTo("C# 医院 > 挂号");
    }

    @Test
    void shouldChunkSopByStep() {
        List<TextSegment> chunks = chunk("""
                # 挂号流程
                第一步 打开小程序。
                第二步 选择科室。
                第三步 完成支付。
                """);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.metadata().getString("strategy")).isEqualTo("sop_steps");
            assertThat(chunk.metadata().getString("role")).isEqualTo("step");
        });
    }

    @Test
    void shouldChunkSopWhenNumberedHeadingsUnderFlow() {
        List<TextSegment> chunks = chunk("""
                # 门诊就诊流程
                ## 1.挂号
                通过公众号或自助机挂号。
                ## 2.候诊
                到诊区等待叫号。
                """);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.metadata().getString("strategy")).isEqualTo("sop_steps");
            assertThat(chunk.metadata().getString("role")).isEqualTo("step");
        });
    }

    @Test
    void shouldCreateParentAndChildChunksForPolicy() {
        List<TextSegment> chunks = chunk("""
                # 门诊管理制度
                门诊管理制度适用于所有门诊科室。

                患者就诊前需完成挂号。

                医生接诊后应规范书写病历。
                """);

        assertThat(chunks).hasSize(4);
        assertThat(chunks.get(0).metadata().getString("role")).isEqualTo("parent");
        String parentKey = chunks.get(0).metadata().getString("parent_key");
        assertThat(parentKey).isNotBlank();
        assertThat(chunks.subList(1, 4)).allSatisfy(chunk -> {
            assertThat(chunk.metadata().getString("role")).isEqualTo("child");
            assertThat(chunk.metadata().getString("strategy")).isEqualTo("policy_manual_structured");
            assertThat(chunk.metadata().getString("parent_key")).isEqualTo(parentKey);
        });
    }

    @Test
    void shouldFallbackToRecursiveForPlainText() {
        List<TextSegment> chunks = chunk("""
                普通文本第一段。

                普通文本第二段。
                """);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata().getString("strategy")).isEqualTo("recursive");
        assertThat(chunks.get(0).metadata().getString("role")).isEqualTo("normal");
    }

    private List<TextSegment> chunk(String markdown) {
        return chunker.chunk(markdown, new Metadata().put("doc_key", "doc"));
    }
}
