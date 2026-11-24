package com.atguigu.java.ai.langchain4j.service.parser;

import com.atguigu.java.ai.langchain4j.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class MineruClient {

    private static final Logger log = LoggerFactory.getLogger(MineruClient.class);

    private static final int CONNECT_TIMEOUT_MILLIS = 5000;

    private final RagProperties ragProperties;
    private final RestClient restClient;

    public MineruClient(RagProperties ragProperties) {
        this.ragProperties = ragProperties;

        int readTimeoutMillis = ragProperties.getMineru().getTimeoutSeconds() * 1000;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(readTimeoutMillis);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public String parseMarkdown(Path file) {
        long start = System.nanoTime();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", new FileSystemResource(file));
        body.add("lang_list", ragProperties.getMineru().getLang());
        body.add("backend", ragProperties.getMineru().getBackend());
        if (ragProperties.getMineru().getServerUrl() != null
                && !ragProperties.getMineru().getServerUrl().isBlank()) {
            body.add("server_url", ragProperties.getMineru().getServerUrl());
        }
        body.add("parse_method", "auto");
        body.add("formula_enable", "true");
        body.add("table_enable", "true");
        body.add("return_md", "true");
        body.add("return_content_list", "false");
        body.add("return_images", "false");
        body.add("response_format_zip", "false");

        JsonNode response = restClient.post()
                .uri(fileParseUrl())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("MinerU 返回为空");
        }

        JsonNode results = response.path("results");
        if (!results.isObject() || results.size() == 0) {
            throw new IllegalStateException("MinerU 未返回解析结果");
        }

        JsonNode first = results.elements().next();
        String markdown = first.path("md_content").asText("");
        if (markdown.isBlank()) {
            throw new IllegalStateException("MinerU 未返回 Markdown 内容");
        }

        long costMillis = (System.nanoTime() - start) / 1_000_000;
        log.info("MinerU 解析完成，file={}, chars={}, cost={}ms",
                file.getFileName(), markdown.length(), costMillis);
        return markdown;
    }

    private String fileParseUrl() {
        String base = ragProperties.getMineru().getUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/file_parse";
    }
}
