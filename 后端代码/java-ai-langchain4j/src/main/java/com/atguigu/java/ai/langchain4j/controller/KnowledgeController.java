package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.common.ApiException;
import com.atguigu.java.ai.langchain4j.common.ApiResponse;
import com.atguigu.java.ai.langchain4j.entity.KnowledgeDocument;
import com.atguigu.java.ai.langchain4j.service.DocumentIngestionService;
import com.atguigu.java.ai.langchain4j.vo.KnowledgeDocumentInfo;
import com.atguigu.java.ai.langchain4j.vo.KnowledgeIngestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Tag(name = "知识库")
@RestController
@RequestMapping("/medical/knowledge")
public class KnowledgeController {

    private final DocumentIngestionService ingestionService;

    public KnowledgeController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Operation(summary = "上传并入库知识库文档")
    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeIngestResult> ingest(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "department", required = false) String department) {

        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("knowledge-", suffixOf(originalName));
            file.transferTo(tempFile);
            return ApiResponse.ok(ingestionService.ingest(tempFile, originalName, department));
        } catch (IOException e) {
            log.error("保存上传文件失败", e);
            throw ApiException.badRequest("保存上传文件失败");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("清理临时文件失败: {}", tempFile, e);
                }
            }
        }
    }

    @Operation(summary = "查看已入库文档")
    @GetMapping("/documents")
    public ApiResponse<List<KnowledgeDocumentInfo>> documents() {
        List<KnowledgeDocumentInfo> documents = ingestionService.listDocuments().stream()
                .map(this::toInfo)
                .toList();
        return ApiResponse.ok(documents);
    }

    private KnowledgeDocumentInfo toInfo(KnowledgeDocument document) {
        int chunkCount = document.getChunkCount() == null ? 0 : document.getChunkCount();
        return new KnowledgeDocumentInfo(
                document.getId(),
                document.getTitle(),
                document.getSource(),
                document.getDocType(),
                document.getDepartment(),
                document.getStatus(),
                chunkCount,
                document.getErrorMessage(),
                document.getUpdatedAt());
    }

    private String suffixOf(String originalName) {
        if (originalName == null) {
            return ".tmp";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return ".tmp";
        }
        return "." + originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
