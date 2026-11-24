package com.atguigu.java.ai.langchain4j.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xiaozhi.rag")
public class RagProperties {

    private int embeddingDimension = 1024;

    private int chunkSize = 500;

    private int chunkOverlap = 80;

    private int candidateK = 50;

    private int rrfK = 60;

    private int rerankCandidateK = 30;

    private int topN = 5;

    private Rerank rerank = new Rerank();

    private Boost boost = new Boost();

    private Mineru mineru = new Mineru();

    @Data
    public static class Rerank {

        private boolean enabled = true;

        private String url = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

        private String model = "gte-rerank-v2";

        private String apiKey;

        private int timeoutSeconds = 10;
    }

    @Data
    public static class Boost {

        private double departmentWeight = 0.05;

        private double titleWeight = 0.03;

        private double officialWeight = 0.02;
    }

    @Data
    public static class Mineru {

        private boolean enabled = false;

        private String url = "http://localhost:8000";

        private String backend = "pipeline";

        private String serverUrl = "";

        private String lang = "ch";

        private int timeoutSeconds = 300;
    }
}
