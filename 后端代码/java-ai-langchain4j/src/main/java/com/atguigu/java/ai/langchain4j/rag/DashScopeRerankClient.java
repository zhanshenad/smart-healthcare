package com.atguigu.java.ai.langchain4j.rag;

import com.atguigu.java.ai.langchain4j.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DashScopeRerankClient implements RerankClient {

    private final RagProperties ragProperties;
    private final RestClient restClient;

    public DashScopeRerankClient(RagProperties ragProperties) {
        this.ragProperties = ragProperties;

        int timeoutMillis = ragProperties.getRerank().getTimeoutSeconds() * 1000;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<Double> score(String query, List<String> documents) {
        Map<String, Object> body = Map.of(
                "model", ragProperties.getRerank().getModel(),
                "input", Map.of("query", query, "documents", documents),
                "parameters", Map.of("return_documents", false, "top_n", documents.size()));

        JsonNode response = restClient.post()
                .uri(ragProperties.getRerank().getUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ragProperties.getRerank().getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Rerank 返回为空");
        }

        JsonNode results = response.path("output").path("results");
        if (!results.isArray()) {
            throw new IllegalStateException("Rerank 调用失败: " + response.path("message").asText());
        }

        double[] scores = new double[documents.size()];
        Arrays.fill(scores, Double.NaN);
        for (JsonNode result : results) {
            int index = result.path("index").asInt(-1);
            if (index >= 0 && index < scores.length) {
                scores[index] = result.path("relevance_score").asDouble();
            }
        }

        for (int i = 0; i < scores.length; i++) {
            if (Double.isNaN(scores[i])) {
                throw new IllegalStateException("Rerank 返回结果不完整");
            }
        }
        return Arrays.stream(scores).boxed().toList();
    }
}
